package com.justcal.app.ai

object AiResponseValidation {
    /** Repeated generated blocks are a failed extraction, even when the native call completes. */
    fun degenerate(text: String): Boolean =
        text.lineSequence().map(String::trim).filter { it.length >= 3 }.groupingBy { it }.eachCount()
            .any { (_, count) -> count >= 6 }

    fun allMissing(value: ExtractedNutrition): Boolean =
        value.productName == null && value.caloriesKcal == null && value.nutritionBasisGrams == null &&
            value.proteinGrams == null && value.fatGrams == null && value.carbohydrateGrams == null &&
            value.packageWeightGrams == null
}
