package com.justcal.app.ocr

import com.justcal.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class FoodMatchEvidenceAdapterTest {
    @Test fun missingUncertainUnsupportedAndAiEvidenceStayDistinct() {
        val label = ParsedNutritionLabel(name = LabelValue("Almond yogurt", 1f, "title"),
            energyKcal = LabelValue("123", .5f, "uncertain"),
            proteinGrams = LabelValue("5", 1f, "label"), basis = LabelBasis.PER_SERVING)
        val routed = RecognitionDraft(fat = "4", basisGrams = "100", source = "AI", conflicts = setOf("CARBS"),
            suggestedPackageGrams = "500")
        val evidence = PackageScanPrefill(label, "", 1f, OcrMetrics(0, 0, 0, 0), recognition = routed).matchEvidence()
        assertEquals(MatchNutritionBasis.UNSUPPORTED, evidence.basis)
        assertEquals(MatchEvidenceState.UNCERTAIN, evidence.nutrients[0].state)
        assertEquals(MatchEvidenceState.KNOWN, evidence.nutrients[1].state)
        assertEquals(MatchEvidenceState.AI_RECOVERED, evidence.nutrients[2].state)
        assertEquals(MatchEvidenceState.CONFLICTING, evidence.nutrients[3].state)
        assertNull(evidence.packageMassHundredths)
        assertNull(evidence.brand.text)
    }
}
