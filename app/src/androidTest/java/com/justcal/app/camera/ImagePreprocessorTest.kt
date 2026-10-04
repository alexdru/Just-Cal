package com.justcal.app.camera

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImagePreprocessorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val images = ImagePreprocessor(context)
    private val request = ScanRequest(ScanMode.PACKAGE, 18000)

    private fun fixture(width: Int = 120, height: Int = 80): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.setHasAlpha(false)
            val canvas = Canvas(bitmap)
            val paint = Paint()
            listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW).forEachIndexed { i, color ->
                paint.color = color
                val left = if (i % 2 == 0) 0f else width / 2f
                val top = if (i < 2) 0f else height / 2f
                canvas.drawRect(left, top, left + width / 2f, top + height / 2f, paint)
            }
        }

    @Test fun allEightExifOrientationsDecodeUprightWithoutRecompressingSmallPhotos() = runBlocking {
        val corners = listOf(
            listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW),
            listOf(Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE),
            listOf(Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED),
            listOf(Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN),
            listOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW),
            listOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN),
            listOf(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED),
            listOf(Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE),
        )
        for (orientation in 1..8) {
            val input = images.newCaptureFile()
            val bitmap = fixture()
            input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            bitmap.recycle()
            ExifInterface(input).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
            val bytes = input.readBytes()
            val prepared = images.prepare(Uri.fromFile(input), ImageSource.CAMERA, request)
            try {
                assertEquals(request, prepared.request)
                assertEquals(if (orientation >= 5) 80 else 120, prepared.width)
                assertEquals(if (orientation >= 5) 120 else 80, prepared.height)
                assertArrayEquals(bytes, input.readBytes())
                val upright = images.decode(prepared)
                try {
                    val actual = listOf(upright.getPixel(10, 10), upright.getPixel(upright.width - 11, 10),
                        upright.getPixel(10, upright.height - 11), upright.getPixel(upright.width - 11, upright.height - 11))
                    corners[orientation - 1].zip(actual).forEach { (expected, pixel) ->
                        assertTrue("Orientation $orientation red", kotlin.math.abs(Color.red(expected) - Color.red(pixel)) < 10)
                        assertTrue("Orientation $orientation green", kotlin.math.abs(Color.green(expected) - Color.green(pixel)) < 10)
                        assertTrue("Orientation $orientation blue", kotlin.math.abs(Color.blue(expected) - Color.blue(pixel)) < 10)
                    }
                    assertEquals(prepared, images.verify(prepared).image)
                } finally { upright.recycle() }
            } finally { input.delete() }
        }
    }

    @Test fun pickerContentUriIsCopiedLocallyAndOversizedImageIsBounded() = runBlocking {
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "JustCal-pipeline-${java.util.UUID.randomUUID()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            }))
        try {
            val bitmap = fixture(4200, 32)
            context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val prepared = images.prepare(uri, ImageSource.PHOTO_PICKER, request.copy(mode = ScanMode.MEAL))
            try {
                assertEquals(4096, prepared.width)
                assertEquals(ScanMode.MEAL, prepared.request.mode)
                assertEquals(request.dayEpoch, prepared.request.dayEpoch)
                assertEquals(ImageSource.PHOTO_PICKER, prepared.source)
                assertNotEquals(uri.toString(), prepared.uri)
                val decoded = images.decode(prepared)
                try { assertEquals(prepared.width, decoded.width); assertEquals(prepared.height, decoded.height) }
                finally { decoded.recycle() }
            } finally { java.io.File(requireNotNull(Uri.parse(prepared.uri).path)).delete() }
        } finally { context.contentResolver.delete(uri, null, null) }
    }

    @Test fun corruptAndMissingFilesFailAndPartialInputIsCleaned() = runBlocking {
        val file = images.newCaptureFile().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertTrue(runCatching { images.prepare(Uri.fromFile(file), ImageSource.CAMERA, request) }.isFailure)
        assertFalse(file.exists())
        val missing = PreparedImage(Uri.fromFile(file).toString(), ImageSource.CAMERA, request, 1, 1, "image/jpeg")
        assertTrue(runCatching { images.decode(missing) }.isFailure)
        assertTrue(runCatching { images.verify(missing) }.isFailure)
    }

    @Test fun cameraCannotReadOutsideOwnedDirectory() = runBlocking {
        val other = java.io.File(context.cacheDir, "unrelated-test").apply { writeText("keep") }
        try {
            assertTrue(runCatching { images.prepare(Uri.fromFile(other), ImageSource.CAMERA, request) }.isFailure)
            assertTrue(other.exists())
        } finally { other.delete() }
    }
}
