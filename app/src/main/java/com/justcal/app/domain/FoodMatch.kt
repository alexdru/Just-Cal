package com.justcal.app.domain

/** Evidence is independent of the editable draft and of any copied catalog nutrition. */
enum class MatchEvidenceState { KNOWN, MISSING, UNCERTAIN, CONFLICTING, AI_RECOVERED }
enum class MatchNutritionBasis { PER_100_G, UNKNOWN, UNSUPPORTED }
data class MatchNumber(val hundredths: Long? = null, val state: MatchEvidenceState = MatchEvidenceState.MISSING) {
    init { require(hundredths == null || hundredths >= 0) }
    val comparable get() = hundredths != null && state == MatchEvidenceState.KNOWN
}
data class MatchIdentity(val text: String? = null, val state: MatchEvidenceState = MatchEvidenceState.MISSING)
data class FoodMatchEvidence(
    val name: String,
    val nutrients: List<MatchNumber> = List(4) { MatchNumber() },
    val basis: MatchNutritionBasis = MatchNutritionBasis.UNKNOWN,
    val brand: MatchIdentity = MatchIdentity(),
    val manufacturer: MatchIdentity = MatchIdentity(),
    val retailer: MatchIdentity = MatchIdentity(),
    val deliveryService: MatchIdentity = MatchIdentity(),
    val packageMassHundredths: Long? = null,
    val nameState: MatchEvidenceState = MatchEvidenceState.KNOWN,
) {
    init { require(nutrients.size == 4); require(packageMassHundredths == null || packageMassHundredths > 0) }
}
enum class FoodMatchCategory { POSSIBLE, CONFLICTING, INSUFFICIENT }
enum class FoodMatchReason {
    MISSING_NUTRITION, NON_COMPARABLE_BASIS, IDENTITY_CONFLICT, RECIPE_CONFLICT,
    NUTRITION_CONFLICT, RETAILER_DIFFERS, NAME_TRUNCATED, WEAK_EVIDENCE, AI_EVIDENCE,
}
data class FoodMatch(
    val candidateId: String,
    val category: FoodMatchCategory,
    /** Internal heuristic only; must never be presented as confidence. */
    val rankingScore: Int,
    val comparableFields: Int,
    val reasons: Set<FoodMatchReason>,
)
