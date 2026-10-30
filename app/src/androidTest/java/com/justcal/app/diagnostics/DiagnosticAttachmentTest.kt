package com.justcal.app.diagnostics

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.camera.*
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticAttachmentTest {
    @Test fun photosSurviveReviewCleanupButRequireExplicitExportAndRespectClear() = runBlocking {
        val base: Context = ApplicationProvider.getApplicationContext()
        val root = File(base.cacheDir, "attachment-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getNoBackupFilesDir() = File(root, "private").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(root.name + name, mode)
        }
        val images = ImagePreprocessor(context)
        val repository = OcrDiagnosticsRepository(context, images)
        try {
            val file = images.newCaptureFile()
            val bitmap = Bitmap.createBitmap(240, 120, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.GREEN)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            } finally { bitmap.recycle() }
            ExifInterface(file).apply { setAttribute(ExifInterface.TAG_USER_COMMENT, "PRIVATE_EXIF"); saveAttributes() }
            val image = images.prepare(Uri.fromFile(file), ImageSource.CAMERA, ScanRequest(ScanMode.PACKAGE, 123))
            val attempt = OcrDiagnosticSession(repository).begin(image)
            attempt.prepare()
            attempt.checkpoint(DiagnosticAiAttempt("PACKAGE_MODEL_INFERENCE", "fixture",
                runtimeVersion = "fixture", profile = "fixture", requestedBackend = "AUTO", processId = 123))
            attempt.failure(false, "TEST_FAILURE")
            repository.awaitIdle()
            val scan = repository.scans.first().single()
            assertTrue(scan.photoRetained)
            assertEquals("PACKAGE_MODEL_INFERENCE", scan.aiAttempts.single().stage)
            assertTrue(File(Uri.parse(image.uri).path!!).delete())
            val exporter = DiagnosticReportExporter(context, repository)
            ZipFile(exporter.export()).use { zip ->
                assertFalse(zip.entries().asSequence().any { it.name.startsWith("photos/") || it.name.startsWith("native/") })
            }
            ZipFile(exporter.export(includeRawText = true, includeAttachments = true)).use { zip ->
                val entry = zip.getEntry("photos/scan-${attempt.id}.jpg")
                assertNotNull(entry)
                zip.getInputStream(entry).use { stream ->
                    assertNull(ExifInterface(stream).getAttribute(ExifInterface.TAG_USER_COMMENT))
                }
                val summary = kotlinx.serialization.json.Json.decodeFromString<DiagnosticReportSummary>(
                    zip.getInputStream(zip.getEntry("summary.json")).bufferedReader().readText())
                assertEquals(1, summary.retainedPhotoCount)
                assertEquals(0, summary.nativeTraceCount)
            }
            repository.setDetailed(false)
            assertNull(repository.attachment(attempt.id, "jpg"))
            assertFalse(repository.scans.first().single().photoRetained)
            repository.clear()
            assertTrue(repository.scans.first().isEmpty())
            assertNull(repository.attachment(attempt.id, "jpg"))
            // A pending attempt cannot recreate a photo after Clear.
            repository.preparePhoto(attempt.id, image, 0, 0)
            assertNull(repository.attachment(attempt.id, "jpg"))
        } finally {
            repository.close()
            root.deleteRecursively()
            base.deleteSharedPreferences(root.name + "ocr-diagnostic-consent")
        }
    }

    @Test fun boundedAttachmentsPruneExpiredOrOrphanedFilesAndRejectOversizedTraces() = runBlocking {
        val base: Context = ApplicationProvider.getApplicationContext()
        val root = File(base.cacheDir, "attachment-retention-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = root }
        val files = DiagnosticAttachments(context, ImagePreprocessor(context))
        val id = UUID.randomUUID().toString()
        try {
            assertTrue(files.trace(id, ByteArrayInputStream(byteArrayOf(1, 2, 3))))
            assertNotNull(files.open(id, "pb")?.use { it.readBytes() })
            files.prune(emptySet())
            assertNull(files.open(id, "pb"))
            assertTrue(files.trace(id, ByteArrayInputStream(byteArrayOf(1))))
            File(root, "diagnostic-attachments/$id.pb").setLastModified(1)
            files.prune(setOf(id))
            assertNull(files.open(id, "pb"))
            assertTrue(runCatching { files.trace(id,
                ByteArrayInputStream(ByteArray(DiagnosticAttachments.MAX_TRACE_BYTES + 1))) }.isFailure)
            assertNull(files.open(id, "pb"))
            assertFalse(File(root, "diagnostic-attachments/$id.tmp").exists())
            val newer = UUID.randomUUID().toString()
            for (scanId in listOf(id, newer)) java.io.RandomAccessFile(
                File(root, "diagnostic-attachments/$scanId.jpg"), "rw").use {
                it.setLength(DiagnosticAttachments.MAX_BYTES / 2 + 1)
            }
            File(root, "diagnostic-attachments/$id.jpg").setLastModified(System.currentTimeMillis() - 1000)
            files.prune(setOf(id, newer))
            assertNull(files.open(id, "jpg"))
            assertNotNull(files.open(newer, "jpg")?.use { true })
        } finally { root.deleteRecursively() }
    }
}
