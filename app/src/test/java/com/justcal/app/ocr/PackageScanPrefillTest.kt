package com.justcal.app.ocr

import org.junit.Assert.*
import org.junit.Test

class PackageScanPrefillTest {
    @Test fun scanFormatsEditorNameWhilePreservingOriginalOcrSnapshot() {
        val originalName = "ЙОГУРТ ЙОГУРТ С ВИШНЕЙ"
        val result = PackageOcrResult(listOf(OcrLine(originalName, .99f, emptyList())),
            1000, 1000, OcrMetrics(1, 2, 3, 6))
        val prefill = PackageScanPrefill.from(result)
        assertEquals("Йогурт с вишней", prefill.label.draft().name)
        assertEquals(originalName, prefill.label.name?.value)
        assertEquals(originalName, prefill.rawText)
    }

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
