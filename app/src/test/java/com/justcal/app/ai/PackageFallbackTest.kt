package com.justcal.app.ai

import com.justcal.app.ocr.*
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.*
import org.junit.Test

class PackageFallbackTest {
    private val baseline = PackageScanPrefill(
        ParsedNutritionLabel(energyKcal = LabelValue("120", .99f, "120 kcal"), basis = LabelBasis.PER_100_G),
        "Original OCR", .99f, OcrMetrics(0, 0, 0, 0),
    )

    @Test fun localDeadlineReturnsOcrOnlyAfterCleanup() = runBlocking {
        var closed = false
        val actual = packageEnrichmentOrBaseline(baseline) {
            try { withTimeout(10.milliseconds) { awaitCancellation() } }
            finally { closed = true }
        }
        assertTrue(closed)
        assertSame(baseline, actual)
        assertEquals("120", actual.label.draft().energy)
    }

    @Test fun callerDeadlineAndExplicitCancellationAreNeverTurnedIntoResults() = runBlocking {
        val deadline = runCatching {
            withTimeout(10.milliseconds) { packageEnrichmentOrBaseline(baseline) { awaitCancellation() } }
        }.exceptionOrNull()
        assertTrue(deadline is TimeoutCancellationException)
        var published = false
        val task = launch(start = CoroutineStart.UNDISPATCHED) {
            packageEnrichmentOrBaseline(baseline) { awaitCancellation() }
            published = true
        }
        task.cancelAndJoin()
        assertFalse(published)
        val failedDuringCancellation = launch(start = CoroutineStart.UNDISPATCHED) {
            packageEnrichmentOrBaseline(baseline) {
                currentCoroutineContext().cancel()
                throw IllegalStateException("Native failure after cancellation")
            }
            published = true
        }
        failedDuringCancellation.join()
        assertFalse(published)
    }

    @Test fun unavailableRuntimePreservesOcrButSuccessfulEnrichmentIsReturned() = runBlocking {
        assertSame(baseline, packageEnrichmentOrBaseline(baseline) { throw IllegalStateException("Unavailable") })
        assertSame(baseline, packageEnrichmentOrBaseline(baseline) { throw UnsatisfiedLinkError("Unavailable") })
        val enriched = baseline.copy(rawText = "Reviewed enrichment")
        assertSame(enriched, packageEnrichmentOrBaseline(baseline) { enriched })
    }
}
