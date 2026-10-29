package com.justcal.app.ai

import com.justcal.app.domain.RecognitionDraft
import com.justcal.app.ocr.LabelBasis
import com.justcal.app.ocr.ParsedNutritionLabel
import java.math.BigDecimal

/** Admitted label task: missing P/F/C only. Energy, mass, name and existing OCR values are outside its scope. */
object PackageMacroAssistance {
    const val PROFILE_VERSION = "package-macros-v1-" + LabelImageCropper.PROFILE_VERSION
    const val SOURCE = "OCR_WITH_MACRO_SUGGESTIONS"

    // Exact prompt/schema used for the measured crop-v2 runs; their seven-field transport is
    // intentionally broader than the fields this production consumer is allowed to accept.
    const val PROMPT = "Read the visible food-package label. Return only JSON with exactly these keys: productName, caloriesKcal, nutritionBasisGrams, proteinGrams, fatGrams, carbohydrateGrams, packageWeightGrams. Use the printed product name in its original language. Transcribe only clearly printed numbers. All nutrition numbers must refer to the same explicitly printed basis. Use null for missing or uncertain fields. Do not estimate, calculate, infer net weight, or substitute whole-dish calories for per-100-g values. Numeric values must be JSON numbers without units; productName is a short string or null."

    fun shouldRun(ocr: ParsedNutritionLabel): Boolean {
        if (ocr.basis != LabelBasis.PER_100_G) return false
        return ocr.draft().run { listOf(protein, fat, carbs).any(String::isBlank) }
    }

    fun merge(ocr: ParsedNutritionLabel, raw: String): RecognitionDraft {
        require(raw.length <= 16_000)
        val response = NutritionExtraction.parse(raw)
        val base = ocr.draft()
        val sameBasis = (ocr.basis == LabelBasis.PER_100_G) &&
            (response.nutritionBasisGrams?.compareTo(BigDecimal("100")) == 0)
        fun missing(original: String, candidate: BigDecimal?): String {
            if (original.isNotBlank() || !sameBasis) return original
            return candidate?.takeIf { it <= BigDecimal("100") }
                ?.stripTrailingZeros()?.toPlainString().orEmpty()
        }
        val suggestion = base.copy(
            protein = missing(base.protein, response.proteinGrams),
            fat = missing(base.fat, response.fatGrams),
            carbs = missing(base.carbs, response.carbohydrateGrams),
        )
        val total = listOf(suggestion.protein, suggestion.fat, suggestion.carbs).asSequence()
            .mapNotNull(String::toBigDecimalOrNull).fold(BigDecimal.ZERO, BigDecimal::add)
        val safe = if (total <= BigDecimal("100")) suggestion else base
        return RecognitionDraft(
            name = base.name.takeIf(String::isNotBlank),
            energy = base.energy.takeIf(String::isNotBlank),
            protein = safe.protein.takeIf(String::isNotBlank),
            fat = safe.fat.takeIf(String::isNotBlank),
            carbs = safe.carbs.takeIf(String::isNotBlank),
            basisGrams = if (ocr.basis == LabelBasis.PER_100_G) "100" else null,
            source = SOURCE,
            evidence = raw,
            ocrAmount = base.amount.takeIf(String::isNotBlank),
        )
    }
}
