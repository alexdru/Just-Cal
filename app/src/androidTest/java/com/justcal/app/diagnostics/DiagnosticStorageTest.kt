package com.justcal.app.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticStorageTest {
    @Test fun privacyRetentionSerializationAndClearGeneration() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val key = "diagnostic-test-" + java.util.UUID.randomUUID()
        val root = java.io.File(base.cacheDir, key).apply { mkdirs() }
        val context = object : android.content.ContextWrapper(base) {
            override fun getNoBackupFilesDir() = java.io.File(root, "no-backup").apply { mkdirs() }
            override fun getCacheDir() = java.io.File(root, "cache").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(key + name, mode)
        }
        val repository = OcrDiagnosticsRepository(context)
        try {
        repository.clear()
        repository.setDetailed(false)
        val generation = repository.generation
        val now = System.currentTimeMillis()
        val personal = DiagnosticScan("diagnostic-test", now, status = "SUCCESS", totalMs = 2000,
            rawText = "private OCR", productName = "private name", parsedFields = mapOf("ENERGY" to "13"), detailedConsent = true)
        repository.record(personal, generation)
        val basic = withTimeout(5000) { repository.scans.first { it.any { row -> row.id == personal.id } } }.single()
        assertNull(basic.rawText); assertNull(basic.productName); assertTrue(basic.parsedFields.isEmpty())
        assertFalse(basic.detailedConsent)
        repository.record(personal.copy(id = "expired", timestampMillis = now - OcrDiagnosticsRepository.RETENTION_MS - 1), generation)
        repository.awaitIdle()
        val report = DiagnosticReportExporter(context, repository).export()
        ZipFile(report).use { zip ->
            assertEquals(setOf("summary.json", "scans.jsonl", "corrections.json", "README.txt"),
                zip.entries().asSequence().map { it.name }.toSet())
            val scans = zip.getInputStream(zip.getEntry("scans.jsonl")).bufferedReader().readText()
            assertFalse(scans.contains("private OCR")); assertFalse(scans.contains("private name"))
            assertFalse(scans.contains("expired"))
            assertEquals(1, Json.decodeFromString<DiagnosticReportSummary>(
                zip.getInputStream(zip.getEntry("summary.json")).bufferedReader().readText()).scanCount)
        }
        report.delete()
        repository.clear()
        repository.record(personal.copy(id = "stale"), generation)
        repository.awaitIdle()
        assertTrue(repository.scans.first().isEmpty())
        repository.setDetailed(true)
        repository.record(personal.copy(id = "detailed"), repository.generation)
        withTimeout(5000) { repository.scans.first { it.any { row -> row.id == "detailed" && row.rawText != null } } }
        repository.setDetailed(false)
        val revoked = repository.scans.first().single()
        assertNull(revoked.rawText); assertNull(revoked.productName)
        repository.clear()
        } finally {
            repository.close()
            root.deleteRecursively()
            base.deleteSharedPreferences(key + "ocr-diagnostic-consent")
        }
    }
}
