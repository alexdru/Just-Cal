package com.justcal.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class Product(val id: String, val createdAtMillis: Long, val archived: Boolean = false)

/** Complete, immutable per-100-g snapshot. Never contains a consumed amount. */
@Serializable
data class ProductRevision(
    val id: String, val productId: String, val previousRevisionId: String? = null,
    val name: String, val energy: Long, val protein: Long, val fat: Long, val carbs: Long,
    val savedAtMillis: Long, val brand: String? = null, val manufacturer: String? = null,
    val retailer: String? = null, val deliveryService: String? = null,
    val packageMassHundredths: Long? = null, val origins: String = "USER_ACCEPTED",
) {
    fun draft() = FoodDraft(name, energy.hundredthsText(), protein.hundredthsText(),
        fat.hundredthsText(), carbs.hundredthsText())
    fun evidence() = FoodMatchEvidence(name,
        listOf(energy, protein, fat, carbs).map { MatchNumber(it, MatchEvidenceState.KNOWN) },
        MatchNutritionBasis.PER_100_G, identity(brand), identity(manufacturer),
        identity(retailer), identity(deliveryService), packageMassHundredths)
    private fun identity(text: String?) = MatchIdentity(text,
        if (text == null) MatchEvidenceState.MISSING else MatchEvidenceState.KNOWN)
}

@Serializable
data class EntryProductLink(val entryId: String, val revisionId: String, val provenance: String)

enum class Retention { DIARY_ONLY, REUSE, NEW_PRODUCT, NEW_REVISION }
data class CatalogSave(
    val entryUuid: String, val productUuid: String, val revisionUuid: String,
    val retention: Retention, val selectedRevision: String? = null,
    val details: ProductDetails = ProductDetails(), val provenance: String = "USER_SELECTED",
    val preserveExistingAssociation: Boolean = false,
    val fieldOrigins: String = "USER_ACCEPTED", val revisionSavedAtMillis: Long? = null,
)
@Serializable
data class ProductDetails(
    val brand: String = "", val manufacturer: String = "", val retailer: String = "",
    val deliveryService: String = "", val packageGrams: String = "",
)

data class FoodCandidate(
    val key: String, val revision: ProductRevision, val historyEntryUuid: String? = null,
    val historyCount: Int = 0, val lastDay: Long? = null, val match: FoodMatch? = null,
)
data class FoodSearchResult(val candidates: List<FoodCandidate>, val indexing: Boolean, val truncated: Boolean,
    val retrievalMillis: Double = 0.0, val scoringMillis: Double = 0.0)

interface FoodCatalogRepository {
    suspend fun buildHistoryBatch(): Boolean
    suspend fun search(evidence: FoodMatchEvidence): FoodSearchResult
    suspend fun candidate(key: String): FoodCandidate?
    suspend fun revisions(productId: String, offset: Int): List<FoodCandidate>
    suspend fun save(entry: DiaryEntry, request: CatalogSave): Long
    suspend fun duplicates(revision: ProductRevision): List<FoodCandidate>
    suspend fun archive(productId: String)
    suspend fun forget(productId: String)
}
