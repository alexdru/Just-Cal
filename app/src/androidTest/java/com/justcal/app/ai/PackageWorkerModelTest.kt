package com.justcal.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.camera.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit existing-weight probe. No download, real diary writes or selection changes. */
@RunWith(AndroidJUnit4::class)
class PackageWorkerModelTest {
    @Test fun pinnedGemmaRunsInProductionWorkerUsingOnlyDisposableFiles() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("packageWorkerModel") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val descriptor = ModelCatalog(context).models.single { it.profile == ModelTaskProfile.GENERAL_VISION }
        val root = File(context.noBackupFilesDir, "local-ai-models")
        val files = ModelFiles(root)
        val original = files.list().firstOrNull { files.validation(it)?.sha256 == descriptor.sha256 }
            ?: error("Pinned Gemma must already be installed; this test never downloads")
        val marker = File(context.noBackupFilesDir, "local-ai-interrupted.json")
        check(!marker.exists()) { "Retain the existing interrupted-operation evidence before this opt-in probe" }
        require(root.usableSpace > descriptor.bytes + 2L * 1024 * 1024 * 1024)
        val id = UUID.randomUUID().toString()
        val owned = File(root, id).apply { mkdirs() }
        val backendPreferences = context.getSharedPreferences("local-ai-backends", Context.MODE_PRIVATE)
        val before = backendPreferences.all.toMap()
        val images = ImagePreprocessor(context)
        var image: PreparedImage? = null
        try {
            files.path(original).copyTo(File(owned, "model.litertlm"))
            for (name in listOf("metadata.json", "validation.json")) {
                File(files.path(original).parentFile, name).copyTo(File(owned, name))
            }
            val capture = images.newCaptureFile()
            val bitmap = Bitmap.createBitmap(768, 512, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 40f }
                listOf("Nutrition per 100 g", "Energy 120 kcal", "Protein 5 g", "Fat 4 g", "Carbohydrates 16 g")
                    .forEachIndexed { index, text -> canvas.drawText(text, 24f, 70f + 85f * index, paint) }
                capture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
            image = images.prepare(Uri.fromFile(capture), ImageSource.CAMERA, ScanRequest(ScanMode.PACKAGE, 123))
            var initialization: Long? = null
            val result = withTimeout(150_000) {
                PackageInferenceClient(context).generate(LocalModel(id, descriptor.filename, descriptor.bytes), image) {
                    assertNotEquals(Process.myPid(), it.processId)
                    initialization = it.initializationMs ?: initialization
                }
            }
            assertEquals("PACKAGE_MODEL_COMPLETED", result.stage)
            assertEquals(descriptor.sha256, result.artifactHash)
            assertFalse(result.rawOutput.isNullOrBlank())
            assertEquals(1, ModelRuntimeLease.slot.availablePermits)
            instrumentation.sendStatus(0, Bundle().apply { putString("stream",
                "\nPACKAGE_WORKER_OK initializationMs=$initialization inferenceMs=${result.inferenceMs} backend=${result.actualBackend}; runtime smoke, not recognition admission\n") })
        } finally {
            image?.let { images.discard(it.uri) }
            owned.deleteRecursively()
            File(context.cacheDir, "local-ai-runtime/$id").deleteRecursively()
            // Restore only backend cache values touched by this probe; user task selections are never accessed.
            val editor = backendPreferences.edit().clear()
            before.forEach { (key, value) -> when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            } }
            check(editor.commit())
            assertEquals(before, backendPreferences.all)
            assertEquals(descriptor.bytes, files.path(original).length())
            assertFalse(marker.exists())
        }
    }
}
