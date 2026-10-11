package com.justcal.app.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class DiagnosticReportSharingTest {
    private val base = ApplicationProvider.getApplicationContext<Context>()
    private val key = "report-test-${UUID.randomUUID()}"
    // Nested under the real provider's explicitly restricted cache path.
    private val directory = File(base.cacheDir, "diagnostic-reports/$key")
    private val isolated = object : ContextWrapper(base) {
        override fun getCacheDir(): File = directory
        override fun getNoBackupFilesDir(): File = File(directory, "storage").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences("$key-$name", mode)
    }
    private lateinit var repository: OcrDiagnosticsRepository
    @Before fun setUp() { directory.mkdirs(); repository = OcrDiagnosticsRepository(isolated) }
    @After fun tearDown() = runBlocking<Unit> {
        repository.awaitIdle(); repository.close(); directory.deleteRecursively()
        base.deleteSharedPreferences("$key-ocr-diagnostic-consent")
    }
    private fun scan(id: String) = DiagnosticScan(id, System.currentTimeMillis(), status = "SUCCESS",
        totalMs = 200, productName = "PERSONAL_FOOD", rawText = "PRIVATE_OCR_TEXT",
        parsedFields = mapOf("NAME" to "PERSONAL_FOOD"),
        correctedFields = listOf("ENERGY"), detailedConsent = true,
        corrections = listOf(DiagnosticCorrection("ENERGY", "EXPLICIT_CORRECTION", "12345", "67890")))

    @Test fun defaultAndSelectedZipReportsAreStructuredAndPrivacySafe() = runBlocking<Unit> {
        repository.setDetailed(true)
        repository.record(scan("first"), repository.generation)
        repository.record(scan("second"), repository.generation)
        repository.awaitIdle()
        val exporter = DiagnosticReportExporter(isolated, repository)
        val default = exporter.export()
        ZipFile(default).use { zip ->
            assertEquals(setOf("summary.json", "scans.jsonl", "corrections.json", "README.txt"),
                zip.entries().asSequence().map { it.name }.toSet())
            val summaryText = zip.getInputStream(zip.getEntry("summary.json")).bufferedReader().use { it.readText() }
            assertTrue(Json.parseToJsonElement(summaryText).toString().contains("\"schemaVersion\":1"))
            val summary = Json.decodeFromString<DiagnosticReportSummary>(summaryText)
            assertEquals(2, summary.scanCount)
            assertFalse(summary.rawTextIncluded); assertEquals(0, summary.selectedPhotoCount)
            listOf("scans.jsonl", "corrections.json").forEach { name ->
                val text = zip.getInputStream(zip.getEntry(name)).bufferedReader().use { it.readText() }
                assertFalse(text.contains("PERSONAL_FOOD")); assertFalse(text.contains("PRIVATE_OCR_TEXT"))
                assertFalse(text.contains("12345")); assertFalse(text.contains("67890"))
            }
        }
        ZipFile(exporter.export(setOf("first"), includeRawText = true)).use { zip ->
            val lines = zip.getInputStream(zip.getEntry("scans.jsonl")).bufferedReader().use { it.readLines() }
            assertEquals(1, lines.size)
            val selected = Json.decodeFromString<DiagnosticScan>(lines.single())
            assertEquals("first", selected.id); assertEquals("PRIVATE_OCR_TEXT", selected.rawText)
            assertEquals("PERSONAL_FOOD", selected.productName)
            assertEquals("PERSONAL_FOOD", selected.parsedFields["NAME"])
            val corrections = Json.decodeFromString<List<ScanCorrections>>(
                zip.getInputStream(zip.getEntry("corrections.json")).bufferedReader().use { it.readText() })
            assertEquals("12345", corrections.single().corrections.single().before)
            assertEquals("67890", corrections.single().corrections.single().after)
        }
    }

    @Test fun exportedArchiveHasRestrictedContentUriAndReadGrantShareIntent() = runBlocking<Unit> {
        val file = DiagnosticReportExporter(isolated, repository).export()
        val authority = base.packageName + ".diagnostics.fileprovider"
        val uri = FileProvider.getUriForFile(base, authority, file)
        assertEquals("content", uri.scheme); assertEquals(authority, uri.authority)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(base.contentResolver, "OCR diagnostics", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        base.contentResolver.openInputStream(uri)!!.use {
            assertEquals('P'.code, it.read()); assertEquals('K'.code, it.read())
        }
        val privateFile = File(base.filesDir, "$key-private").apply { writeText("private") }
        try {
            assertThrows(IllegalArgumentException::class.java) { FileProvider.getUriForFile(base, authority, privateFile) }
        } finally { privateFile.delete() }
    }

    @Test fun photosAreIncludedOnlyWhenExplicitlySelectedAndRawTextRequiresStoredConsent() = runBlocking<Unit> {
        repository.record(scan("basic"), repository.generation)
        repository.awaitIdle()
        val exporter = DiagnosticReportExporter(isolated, repository)
        val photo = File(directory, "selected-image").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val uri = FileProvider.getUriForFile(base, base.packageName + ".diagnostics.fileprovider", photo)
        ZipFile(exporter.export(includeRawText = true, photos = listOf(uri))).use { zip ->
            val text = zip.getInputStream(zip.getEntry("scans.jsonl")).bufferedReader().use { it.readText() }
            assertFalse(text.contains("PRIVATE_OCR_TEXT"))
            assertArrayEquals(byteArrayOf(1, 2, 3), zip.getInputStream(zip.getEntry("photos/selected-1.image")).use { it.readBytes() })
        }
    }
}
