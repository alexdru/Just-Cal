package com.justcal.app.diagnostics

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DiagnosticModelsTest {
    private fun scan(id: Int, time: Long, status: String = "SUCCESS") = DiagnosticScan(id.toString(), 0, status = status, totalMs = time)
    @Test fun privacyDefaultAndSerialization() {
        val original = scan(1, 20).copy(productName = "personal", rawText = "private", parsedFields = mapOf("ENERGY" to "42"),
            corrections = listOf(DiagnosticCorrection("ENERGY", "EXPLICIT_CORRECTION", "42", "43")), detailedConsent = true)
        val safe = Json.decodeFromString<DiagnosticScan>(Json.encodeToString(original.sanitized()))
        assertNull(safe.productName); assertNull(safe.rawText); assertTrue(safe.parsedFields.isEmpty())
        assertNull(safe.corrections.single().before)
        assertEquals("private", original.sanitized(true).rawText)
        assertNull(original.copy(detailedConsent = false).sanitized(true).rawText)
    }
    @Test fun measuredDistributionAndGroups() {
        val rows = (1..20).map { scan(it, it * 10L).copy(brand = if (it < 4) "Brand" else null) }
        val stats = DiagnosticStatistics.from(rows)
        assertEquals(20, stats.total); assertEquals(105.0, stats.medianMs!!, 0.0);
        assertEquals(1.5, DiagnosticStatistics.from(listOf(scan(1, 1), scan(2, 2))).medianMs!!, 0.0); assertEquals(190L, stats.p95Ms)
        assertEquals(200L, stats.slowest.first().totalMs)
        assertEquals(3, stats.brands.single { it.brand == "Brand" }.scans)
        assertNull(DiagnosticStatistics.from(emptyList()).p95Ms)
    }
    @Test fun brandsRequireExplicitAttribution() {
        assertNull(PackagingBrand.extract("Chicken retailer salad"))
        assertEquals("Вкусная марка", PackagingBrand.extract("Бренд: Вкусная марка"))
        assertEquals("English Foods", PackagingBrand.extract("Manufacturer: English Foods"))
    }
    @Test fun cancellationIsNotSuccessOrModelFailure() {
        val stats = DiagnosticStatistics.from(listOf(scan(1, 1, "CANCELLED"), scan(2, 2, "FAILURE"), scan(3, 3, "PARTIAL")))
        assertEquals(1, stats.successful); assertEquals(1, stats.failed); assertEquals(3, stats.total)
    }
}
