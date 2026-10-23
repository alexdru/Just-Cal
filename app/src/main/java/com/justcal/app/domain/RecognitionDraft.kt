package com.justcal.app.domain

import kotlinx.serialization.Serializable

/** App-owned, bounded suggestions. Missing/conflicting fields stay empty in the editor. */
@Serializable
data class RecognitionDraft(
    val name: String? = null,
    val energy: String? = null,
    val protein: String? = null,
    val fat: String? = null,
    val carbs: String? = null,
    val basisGrams: String? = null,
    val source: String,
    val evidence: String = "",
    val conflicts: Set<String> = emptySet(),
    val diagnosticAttemptId: String? = null,
    val ocrAmount: String? = null,
    val suggestedPackageGrams: String? = null,
) {
    fun draft() = FoodDraft(
        name = name?.takeUnless { "NAME" in conflicts }.orEmpty(),
        energy = nutrient("ENERGY", energy),
        protein = nutrient("PROTEIN", protein),
        fat = nutrient("FAT", fat),
        carbs = nutrient("CARBS", carbs),
        // Photograph-derived mass must never become the consumed amount.
        amount = ocrAmount.orEmpty(),
    )
    private fun nutrient(field: String, value: String?) =
        value?.takeIf { basisGrams == "100" && field !in conflicts }.orEmpty()
}
