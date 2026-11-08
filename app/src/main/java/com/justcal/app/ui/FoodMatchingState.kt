package com.justcal.app.ui

import com.justcal.app.domain.*
import com.justcal.app.domain.Retention
import kotlinx.serialization.Serializable

@Serializable
data class MatchUndo(
    val fields: List<String>, val dirty: Set<FoodField>, val association: ProductRevision?,
    val history: Boolean, val retention: Retention, val details: ProductDetails, val origin: String,
)
@Serializable
data class MatchMemory(
    val dirty: Set<FoodField> = emptySet(), val association: ProductRevision? = null,
    val fromHistory: Boolean = false, val retention: Retention = Retention.DIARY_ONLY,
    val undo: MatchUndo? = null, val inspectedKey: String? = null,
    val details: ProductDetails = ProductDetails(), val collapsed: Boolean = false,
    val origin: String = "SCAN_OR_MANUAL", val searchName: String? = null, val detailsDirty: Boolean = false,
)
fun MatchMemory.effectiveRetention(draft: FoodDraft, unavailable: Boolean): Retention {
    if (unavailable) return Retention.DIARY_ONLY
    val r = association ?: return retention
    fun exact(text: String) = runCatching { text.trim().replace(',', '.').toBigDecimal().movePointRight(2).longValueExact() }.getOrNull()
    val sameFields = draft.name.trim() == r.name &&
        listOf(draft.energy, draft.protein, draft.fat, draft.carbs).map(::exact) == listOf(r.energy, r.protein, r.fat, r.carbs)
    fun optional(text: String) = text.trim().ifBlank { null }
    val sameDetails = optional(details.brand) == r.brand && optional(details.manufacturer) == r.manufacturer &&
        optional(details.retailer) == r.retailer && optional(details.deliveryService) == r.deliveryService &&
        (if (details.packageGrams.isBlank()) null else exact(details.packageGrams)) == r.packageMassHundredths
    return if (retention == Retention.REUSE && (!sameFields || !sameDetails)) Retention.DIARY_ONLY else retention
}
data class FoodMatchingState(
    val memory: MatchMemory = MatchMemory(), val candidates: List<FoodCandidate> = emptyList(),
    val inspected: FoodCandidate? = null, val searching: Boolean = false, val indexing: Boolean = false,
    val error: Boolean = false, val stale: Boolean = false, val truncated: Boolean = false,
    val unavailable: Boolean = false, val pending: String? = null,
    val duplicates: List<FoodCandidate> = emptyList(), val showAll: Boolean = false,
    val alternateOffset: Int = 0, val moreVersions: Boolean = true,
    val alternateVersions: List<FoodCandidate> = emptyList(),
)
