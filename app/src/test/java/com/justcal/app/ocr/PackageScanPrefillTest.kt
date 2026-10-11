package com.justcal.app.ocr

import org.junit.Assert.*
import org.junit.Test

class PackageScanPrefillTest {
    @Test fun reviewRetainsOriginalAndRefinedRecognitionWithoutParsingBoth() {
        val result = PackageOcrResult(listOf(
            OcrLine("Nutrition per 100 g", .99f, emptyList()),
            OcrLine("Energy 770.6 kJ / 183.2 kcal", .99f, emptyList(),
                alternatives = listOf("Energy 770.6 kJ / 183.2 cal")),
        ), 1000, 1000, OcrMetrics(1, 2, 3, 6))
        val prefill = PackageScanPrefill.from(result)
        assertEquals("183.2", prefill.label.energyKcal?.value)
        assertTrue(prefill.rawText.contains("183.2 kcal"))
        assertTrue(prefill.rawText.contains("183.2 cal"))
    }
}
