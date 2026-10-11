package com.justcal.app.diagnostics

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.camera.*
import com.justcal.app.ocr.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OcrDiagnosticsPersistenceTest {
    private val base = ApplicationProvider.getApplicationContext<Context>()
    private val key = "diagnostics-test-${UUID.randomUUID()}"
    private val directory = File(base.cacheDir, key)
    private val isolated = object : ContextWrapper(base) {
        override fun getNoBackupFilesDir(): File = directory
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences("$key-$name", mode)
    }
    private lateinit var repository: OcrDiagnosticsRepository
    @Before fun setUp() { directory.mkdirs(); repository = OcrDiagnosticsRepository(isolated) }
    @After fun tearDown() = runBlocking<Unit> {
        repository.awaitIdle()
        repository.close()
        directory.deleteRecursively()
        base.deleteSharedPreferences("$key-ocr-diagnostic-consent")
    }
    private suspend fun rows(): List<DiagnosticScan> {
        repository.awaitIdle()
        return withTimeout(5000) { repository.scans.first() }
    }
    private fun scan(id: String, time: Long = System.currentTimeMillis(), status: String = "SUCCESS") =
        DiagnosticScan(id, time, status = status, totalMs = 420,
            productName = "Private food", brand = "Марка", rawText = "Private label",
            parsedFields = mapOf("ENERGY" to "205"), detailedConsent = true)

    @Test fun legacyBasicRowsRemainPrivateAndRevocationScrubsExistingRows() = runBlocking<Unit> {
        repository.setDetailed(false)
        assertFalse(repository.detailed.value)
        repository.record(scan("basic"), repository.generation)
        val basic = rows().single()
        assertNull(basic.rawText); assertNull(basic.productName); assertNull(basic.brand)
        assertTrue(basic.parsedFields.isEmpty()); assertFalse(basic.detailedConsent)
        repository.setDetailed(true)
        repository.record(scan("detailed"), repository.generation)
        assertEquals("Private label", rows().first { it.id == "detailed" }.rawText)
        repository.setDetailed(false)
        rows().forEach { assertNull(it.rawText); assertNull(it.productName); assertNull(it.brand); assertTrue(it.corrections.isEmpty()) }
    }

    @Test fun detailsDefaultOnEvenAfterAnUpgradeWithDisabledLegacyPreference() = runBlocking<Unit> {
        assertTrue(repository.detailed.value)
        repository.record(scan("new-installation"), repository.generation)
        assertEquals("Private label", rows().single().rawText)
        repository.close()
        isolated.getSharedPreferences("ocr-diagnostic-consent", Context.MODE_PRIVATE)
            .edit().putBoolean("detailed", false).commit()
        repository = OcrDiagnosticsRepository(isolated)
        assertTrue(repository.detailed.value)
        repository.record(scan("after-upgrade"), repository.generation)
        assertEquals("Private label", rows().first { it.id == "after-upgrade" }.rawText)
    }

    @Test fun changedFieldsAreCorrectionsAndPortionChangesAreSeparateFromExtraction() = runBlocking<Unit> {
        repository.setDetailed(true)
        repository.record(scan("corrected").copy(parsedFields = mapOf("NET_WEIGHT" to "100")), repository.generation)
        repository.awaitIdle()
        val value = LabelValue("100", .99f, "evidence")
        val label = ParsedNutritionLabel(name = LabelValue("Food", .99f, "evidence"),
            netGrams = value, energyKcal = value, proteinGrams = value, fatGrams = value,
            carbsGrams = null, basis = LabelBasis.PER_100_G)
        val prefill = PackageScanPrefill(label, "brand: English Brand", .99f, OcrMetrics(1, 2, 3, 6), "corrected")
        repository.corrections(prefill, label.draft().copy(energy = "120", amount = "50", carbs = "12"))
        val corrected = rows().single()
        assertEquals(setOf("ENERGY", "AMOUNT", "CARBS"), corrected.correctedFields.toSet())
        assertEquals("USER_PORTION_ADJUSTMENT", corrected.corrections.first { it.field == "AMOUNT" }.category)
        assertEquals("MISSING_FIELD_FILLED", corrected.corrections.first { it.field == "CARBS" }.category)
        assertFalse(corrected.correctedFields.contains("NAME"))
        repository.correctNetWeight("corrected", "90")
        assertEquals("EXPLICIT_EXTRACTION_CORRECTION", rows().single().corrections.first { it.field == "NET_WEIGHT" }.category)
        repository.setBrand("corrected", "Русский бренд")
        assertEquals("Русский бренд", rows().single().brand)
    }

    @Test fun retentionPrunesOldAndExcessRowsAndClearRejectsInFlightGeneration() = runBlocking<Unit> {
        repository.record(scan("expired", System.currentTimeMillis() - OcrDiagnosticsRepository.RETENTION_MS - 10_000), repository.generation)
        assertTrue(rows().isEmpty())
        val now = System.currentTimeMillis()
        repeat(502) { repository.record(scan("scan-$it", now + it), repository.generation) }
        assertEquals(500, rows().size)
        val before = repository.generation
        repository.clear()
        repository.record(scan("in-flight"), before)
        assertTrue(rows().isEmpty())
    }

    @Test fun attemptsStoreMeasuredStagesMissingFailuresAndCancellationWithoutImagePaths() = runBlocking<Unit> {
        val session = OcrDiagnosticSession(repository)
        val image = PreparedImage("file:///private/secret.jpg", ImageSource.CAMERA,
            ScanRequest(ScanMode.PACKAGE, 1), 640, 480, "image/jpeg")
        val result = PackageOcrResult(listOf(OcrLine("Nutrition per 100 g", .9f, emptyList())),
            640, 480, OcrMetrics(11, 22, 33, 66))
        val success = session.begin(image)
        success.success(result, PackageScanPrefill(ParsedNutritionLabel(basis = LabelBasis.PER_100_G),
            "Private label", .9f, result.metrics), 7)
        val known = LabelValue("100", .99f, "fixture")
        val fullLabel = ParsedNutritionLabel(name = LabelValue("Food", .99f, "fixture"),
            netGrams = known, energyKcal = known, proteinGrams = known, fatGrams = known,
            carbsGrams = known, basis = LabelBasis.PER_100_G)
        session.begin(image).success(result, PackageScanPrefill(fullLabel, "Private full label", .9f, result.metrics), 8)
        session.begin(image).failure(false, "OCR_RUNTIME_FAILURE")
        session.begin(image).failure(true, "RECOGNITION_CANCELLED")
        val rows = rows()
        assertEquals(setOf("SUCCESS", "PARTIAL", "FAILURE", "CANCELLED"), rows.map { it.status }.toSet())
        val partial = rows.first { it.status == "PARTIAL" }
        assertEquals(11L, partial.initializationMs)
        assertEquals(22L, partial.detectionMs)
        assertEquals(33L, partial.recognitionMs)
        assertEquals(7L, partial.parsingMs)
        assertTrue(partial.missingFields.contains("ENERGY"))
        assertEquals(1, partial.detectedRegions)
        assertTrue(rows.none { it.rawText?.contains("/private") == true })
        assertEquals(1, DiagnosticStatistics.from(rows).failed)
    }
}
