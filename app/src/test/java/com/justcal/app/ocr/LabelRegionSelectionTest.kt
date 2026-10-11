package com.justcal.app.ocr

import org.junit.Assert.*
import org.junit.Test

class LabelRegionSelectionTest {
    private fun line(text: String, y: Float, h: Float = 20f) = OcrLine(text, .95f,
        listOf(OcrPoint(0f, y), OcrPoint(600f, y), OcrPoint(600f, y + h), OcrPoint(0f, y + h)))

    @Test fun nutritionEndsAtUnrelatedSectionAndRetainsNetWeight() {
        val probes = linkedMapOf(
            0 to line("PRODUCT", 0f, 50f),
            1 to line("Ingredients:", 100f),
            2 to line("water flour", 120f),
            3 to line("Nutrition per 100 g", 150f),
            4 to line("Protein 10 g", 170f),
            5 to line("Fat 3 g", 190f),
            6 to line("Manufacturer", 230f),
            7 to line("Street 20", 250f),
            8 to line("Net weight 240 g", 290f),
        )
        val selected = LabelRegionSelection.select(probes)
        assertTrue(selected.containsAll(listOf(0, 3, 4, 5, 8)))
        assertFalse(selected.contains(7))
    }

    @Test fun russianHeadersAndExpiryMayContainNetWeight() {
        val selected = LabelRegionSelection.select(mapOf(
            0 to line("Пищевая ценность", 0f),
            1 to line("100 г продукта", 20f),
            2 to line("белки 11 г", 40f),
            3 to line("Изготовитель", 80f),
            4 to line("СРОК ГОДНОСТИ", 200f),
        ))
        assertTrue(selected.containsAll(listOf(0, 1, 2, 4)))
    }

    @Test fun incompleteOrServingResultsRequireFullFallback() {
        val incomplete = PackageOcrResult(listOf(line("Nutrition per 100 g Protein 11 g", 0f)),
            600, 1000, OcrMetrics(0, 0, 0, 0))
        assertFalse(LabelRegionSelection.complete(incomplete))
        assertFalse(LabelRegionSelection.complete(incomplete.copy(lines =
            listOf(line("Nutrition per serving Protein 11 g", 0f)))))
    }
}
