package com.justcal.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*

/**
 * Owns only app-private, temporary image files. Never resolves a provider URI to a filesystem path.
 * ImageDecoder applies EXIF rotation/reflection before returning dimensions and sRGB pixels.
 */
@Singleton
class ImagePreprocessor @Inject constructor(@ApplicationContext private val context: Context) {
    private val directory = File(context.cacheDir, "image-input").apply { mkdirs() }
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        cleanupScope.launch {
            val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        }
    }

    fun newCaptureFile(): File = File(directory, "${UUID.randomUUID()}.capture")

    private fun ownedFile(uri: String): File {
        val parsed = uri.toUri()
        require(parsed.scheme == "file") { "Not an owned image" }
        val file = File(requireNotNull(parsed.path)).canonicalFile
        require(file.parentFile == directory.canonicalFile) { "Not an owned image" }
        return file
    }

    fun discard(uri: String) {
        cleanupScope.launch { runCatching { ownedFile(uri).delete() } }
    }

    fun exists(image: PreparedImage): Boolean = runCatching { ownedFile(image.uri).isFile }.getOrDefault(defaultValue = false)

    suspend fun prepare(uri: Uri, source: ImageSource, request: ScanRequest): PreparedImage {
        val produced = AtomicReference<PreparedImage?>()
        try {
            return withContext(Dispatchers.IO) {
                // CameraX already wrote into our private directory; gallery input is streamed once.
                val input = if (source == ImageSource.CAMERA) ownedFile(uri.toString())
                    else File(directory, "${UUID.randomUUID()}.image")
                var output: File? = null
                var retained: File? = null
                try {
                    if (source == ImageSource.PHOTO_PICKER) {
                        require(uri.scheme == "content") { "Expected a picker content URI" }
                        context.contentResolver.openInputStream(uri).use { stream ->
                            requireNotNull(stream) { "Image unavailable" }
                            input.outputStream().use { target ->
                                val buffer = ByteArray(32 * 1024)
                                var total = 0L
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = stream.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= ImageDecodeBudget.MAX_INPUT_BYTES) { "Image is too large" }
                                    target.write(buffer, 0, count)
                                }
                            }
                        }
                    }
                    require(input.length() in 1..ImageDecodeBudget.MAX_INPUT_BYTES)
                    var original = ImageSize(1, 1)
                    var mime = ""
                    // Decode with a bounded allocation to validate the actual pixels, not just the header.
                    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(input)) { decoder, info, _ ->
                        original = ImageSize(info.size.width, info.size.height)
                        mime = info.mimeType
                        require(mime in setOf("image/jpeg", "image/png", "image/webp", "image/heif", "image/heic", "image/avif")) {
                            "Unsupported still image"
                        }
                        val size = ImageDecodeBudget.fit(original.width, original.height)
                        decoder.setTargetSize(size.width, size.height)
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                        decoder.setOnPartialImageListener { false }
                    }
                    try {
                        currentCoroutineContext().ensureActive()
                        val resized = bitmap.width != original.width || bitmap.height != original.height
                        val preparedFile = if (resized) {
                            File(directory, "${UUID.randomUUID()}.image").also { file ->
                                output = file
                                // Preserve transparency losslessly; opaque photographs use high-quality JPEG.
                                val format = if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                                mime = if (bitmap.hasAlpha()) "image/png" else "image/jpeg"
                                file.outputStream().use { check(bitmap.compress(format, 95, it)) }
                            }
                        } else input
                        currentCoroutineContext().ensureActive()
                        val result = PreparedImage(Uri.fromFile(preparedFile).toString(), source, request,
                            bitmap.width, bitmap.height, mime)
                        retained = preparedFile
                        produced.set(result)
                        result
                    } finally { bitmap.recycle() }
                } finally {
                    if (input != retained) input.delete()
                    if (output != retained) output?.delete()
                }
            }

        } catch (cancelled: CancellationException) {
            // withContext can be cancelled while dispatching its completed result back to the UI.
            produced.get()?.let { discard(it.uri) }
            throw cancelled
        }
    }

    /** Used by review and, later, the local AI adapter. Always upright, sRGB, and bounded. */
    suspend fun decode(image: PreparedImage, preview: Boolean = false): Bitmap {
        val decoded = AtomicReference<Bitmap?>()
        try {
            return withContext(Dispatchers.IO) {
                val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ownedFile(image.uri))) { decoder, info, _ ->
                    val size = if (preview) ImageDecodeBudget.fit(info.size.width, info.size.height, 1920, 2_000_000)
                        else ImageDecodeBudget.fit(info.size.width, info.size.height)
                    decoder.setTargetSize(size.width, size.height)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                    decoder.setOnPartialImageListener { false }
                }
                decoded.set(bitmap)
                currentCoroutineContext().ensureActive()
                bitmap
            }
        } catch (cancelled: CancellationException) {
            decoded.get()?.recycle()
            throw cancelled
        }
    }

    fun verify(image: PreparedImage): VerifiedImageInput {
        if (!exists(image)) throw IOException("Temporary image unavailable")
        return VerifiedImageInput(image)
    }
}
