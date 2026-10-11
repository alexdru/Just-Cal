package com.justcal.app.diagnostics

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.camera.*
import com.justcal.app.domain.FoodField
import com.justcal.app.ocr.*
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrDiagnosticIntegrationTest {
    @Test fun realModelsMeasureSessionTrackEditsAndSanitizeReport() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val key = "ocr-integration-" + UUID.randomUUID()
        val root = File(base.cacheDir, key).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(key + name, mode)
        }
        val repository = OcrDiagnosticsRepository(context)
        val images = ImagePreprocessor(context)
        val engine = PaddlePackageOcrEngine(context, images)
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        try {
            repository.setDetailed(true)
            val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 44f }
            listOf("РИС С КУРИЦЕЙ", "Масса нетто: 240 г", "Пищевая ценность на 100 г",
                "Белки 11 г  Жиры 6,4 г", "Углеводы 20,4 г", "Энергетическая ценность: 183,2 ккал",
                "Net weight 240 g").forEachIndexed { index, text -> canvas.drawText(text, 40f, 80f + index * 95, paint) }
            val file = images.newCaptureFile()
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val image = images.prepare(Uri.fromFile(file), ImageSource.CAMERA, ScanRequest(ScanMode.PACKAGE, 123))
            val session = OcrDiagnosticSession(repository).begin(image)
            val result = engine.recognize(images.verify(image))
            val started = SystemClock.elapsedRealtime()
            val prefill = PackageScanPrefill.from(result).copy(diagnosticSessionId = session.id)
            session.success(result, prefill, SystemClock.elapsedRealtime() - started)
            repository.corrections(prefill, prefill.label.draft().with(FoodField.ENERGY, "184").with(FoodField.AMOUNT, "120"))
            repository.awaitIdle()
            val scan = repository.scans.first().single()
            assertTrue(scan.initializationMs!! > 0); assertTrue(scan.detectionMs!! > 0); assertTrue(scan.recognitionMs!! > 0)
            assertNotNull(scan.parsingMs); assertTrue(scan.totalMs > 0); assertTrue(requireNotNull(scan.detectedRegions) > 0)
            assertNotNull(scan.rawText); assertTrue(scan.correctedFields.contains("ENERGY"))
            assertEquals("USER_PORTION_ADJUSTMENT", scan.corrections.single { it.field == "AMOUNT" }.category)
            assertFalse(scan.correctedFields.contains("PROTEIN"))
            val report = DiagnosticReportExporter(context, repository).export()
            ZipFile(report).use { zip ->
                val rows = zip.getInputStream(zip.getEntry("scans.jsonl")).bufferedReader().readText()
                assertFalse(rows.contains("РИС")); assertFalse(rows.contains("183.2"))
                val changes = zip.getInputStream(zip.getEntry("corrections.json")).bufferedReader().readText()
                assertFalse(changes.contains("184")); assertTrue(changes.contains("USER_PORTION_ADJUSTMENT"))
            }
        } finally {
            bitmap.recycle(); engine.release(); repository.close()
            root.deleteRecursively(); base.deleteSharedPreferences(key + "ocr-diagnostic-consent")
        }
    }
}
