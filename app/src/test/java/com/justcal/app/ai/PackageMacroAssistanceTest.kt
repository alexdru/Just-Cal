package com.justcal.app.ai

import com.justcal.app.ocr.LabelBasis
import com.justcal.app.ocr.LabelValue
import com.justcal.app.ocr.ParsedNutritionLabel
import org.junit.Assert.*
import org.junit.Test

class PackageMacroAssistanceTest {
    private val base = ParsedNutritionLabel(
        name = LabelValue("Rice", .99f, "Rice"),
        netGrams = LabelValue("250", .99f, "Net 250 g"),
        energyKcal = LabelValue("150", .99f, "150 kcal"),
        carbsGrams = LabelValue("24.8", .99f, "Carbs 24.8 g"),
        basis = LabelBasis.PER_100_G,
    )

    @Test fun onlyMissingMacrosCanChangeEvenWhenModelEnergyMassNameAndKnownCarbsAreWrong() {
        val raw = response(protein = "4.5", fat = "3.6", carbs = "99")
        val result = PackageMacroAssistance.merge(base, raw)
        val draft = result.draft()
        assertEquals("Rice", draft.name)
        assertEquals("150", draft.energy)
        assertEquals("250", draft.amount)
        assertEquals("4.5", draft.protein)
        assertEquals("3.6", draft.fat)
        assertEquals("24.8", draft.carbs)
        assertNull(result.suggestedPackageGrams)
        assertTrue(result.conflicts.isEmpty())
        assertEquals(raw, result.evidence)
        assertTrue(PackageMacroAssistance.shouldRun(base))
        assertFalse(PackageMacroAssistance.shouldRun(base.copy(
            proteinGrams = LabelValue("4.5", .99f, ""),
            fatGrams = LabelValue("3.6", .99f, ""),
        )))
    }

    @Test fun bothSourcesNeedExplicit100gAndNoOtherBasisIsConvertedOrAssumed() {
        for (basis in listOf("null", "50", "250")) {
            val result = PackageMacroAssistance.merge(base, response(basis = basis)).draft()
            assertEquals("", result.protein)
            assertEquals("", result.fat)
            assertEquals("150", result.energy)
        }
        for (basis in listOf(LabelBasis.UNKNOWN, LabelBasis.PER_SERVING, LabelBasis.AMBIGUOUS)) {
            val ocr = base.copy(basis = basis)
            assertFalse(PackageMacroAssistance.shouldRun(ocr))
            assertEquals("", PackageMacroAssistance.merge(ocr, response()).draft().protein)
        }
    }

    @Test fun nullsStayMissingExplicitZeroIsAcceptedAndAiMassNeverCreatesAnAmount() {
        val result = PackageMacroAssistance.merge(base.copy(netGrams = null),
            response(protein = "null", fat = "0", basis = "100.0")).draft()
        assertEquals("", result.protein)
        assertEquals("0", result.fat)
        assertEquals("", result.amount)
        val absent = PackageMacroAssistance.merge(base.copy(name = null, energyKcal = null, netGrams = null), response()).draft()
        assertEquals("", absent.name)
        assertEquals("", absent.energy)
        assertEquals("", absent.amount)
    }

    @Test fun individualAndCombinedMacroBoundsNeverEraseTheOcrBaseline() {
        val individual = PackageMacroAssistance.merge(base, response(protein = "101", fat = "null")).draft()
        assertEquals("", individual.protein)
        assertEquals("24.8", individual.carbs)
        val combined = PackageMacroAssistance.merge(base, response(protein = "60", fat = "60")).draft()
        assertEquals("", combined.protein)
        assertEquals("", combined.fat)
        assertEquals("150", combined.energy)
        assertEquals("24.8", combined.carbs)
        assertTrue(runCatching { PackageMacroAssistance.merge(base, response(protein = "-1")) }.isFailure)
        assertTrue(runCatching { PackageMacroAssistance.merge(base, response(protein = "\"4.5\"")) }.isFailure)
        assertTrue(runCatching { PackageMacroAssistance.merge(base, "{}") }.isFailure)
    }

    private fun response(protein: String = "4.5", fat: String = "3.6", carbs: String = "24.8", basis: String = "100") =
        """{"productName":"Wrong name","caloriesKcal":598,"nutritionBasisGrams":$basis,"proteinGrams":$protein,"fatGrams":$fat,"carbohydrateGrams":$carbs,"packageWeightGrams":999}"""
}
