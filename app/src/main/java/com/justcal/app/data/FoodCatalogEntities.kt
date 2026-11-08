package com.justcal.app.data

import androidx.room.*
import com.justcal.app.domain.*

@Entity(tableName = "products")
data class ProductEntity(@PrimaryKey val id: String, val createdAtMillis: Long, val archived: Boolean) {
    fun domain() = Product(id, createdAtMillis, archived)
}
@Entity(tableName = "product_revisions", foreignKeys = [
    ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ProductRevisionEntity::class, parentColumns = ["id"], childColumns = ["previousRevisionId"], onDelete = ForeignKey.NO_ACTION, deferred = true),
], indices = [Index("productId"), Index("previousRevisionId")])
data class ProductRevisionEntity(
    @PrimaryKey val id: String, val productId: String, val previousRevisionId: String?,
    val name: String, val energy: Long, val protein: Long, val fat: Long, val carbs: Long,
    val savedAtMillis: Long, val brand: String?, val manufacturer: String?,
    val retailer: String?, val deliveryService: String?, val packageMassHundredths: Long?, val origins: String,
) {
    fun domain() = ProductRevision(id, productId, previousRevisionId, name, energy, protein, fat, carbs,
        savedAtMillis, brand, manufacturer, retailer, deliveryService, packageMassHundredths, origins)
    companion object {
        fun from(r: ProductRevision) = ProductRevisionEntity(r.id, r.productId, r.previousRevisionId, r.name,
            r.energy, r.protein, r.fat, r.carbs, r.savedAtMillis, r.brand, r.manufacturer, r.retailer,
            r.deliveryService, r.packageMassHundredths, r.origins)
    }
}
@Entity(tableName = "entry_product_links", foreignKeys = [
    ForeignKey(entity = DiaryEntryEntity::class, parentColumns = ["portableId"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ProductRevisionEntity::class, parentColumns = ["id"], childColumns = ["revisionId"], onDelete = ForeignKey.CASCADE),
], indices = [Index("revisionId")])
data class EntryProductLinkEntity(@PrimaryKey val entryId: String, val revisionId: String, val provenance: String) {
    fun domain() = EntryProductLink(entryId, revisionId, provenance)
}
@Entity(tableName = "food_search", indices = [Index("energy"), Index("protein"), Index("fat"), Index("carbs")])
data class FoodSearchEntity(
    @PrimaryKey val key: String, val source: String, val normalizedName: String,
    val name: String, val energy: Long, val protein: Long, val fat: Long, val carbs: Long,
    val normalizationVersion: Int,
)
@Entity(tableName = "food_tokens", primaryKeys = ["token", "ownerKey"], foreignKeys = [
    ForeignKey(entity = FoodSearchEntity::class, parentColumns = ["key"], childColumns = ["ownerKey"], onDelete = ForeignKey.CASCADE),
], indices = [Index("ownerKey")])
data class FoodTokenEntity(val token: String, val ownerKey: String)
@Entity(tableName = "food_history_members", foreignKeys = [
    ForeignKey(entity = DiaryEntryEntity::class, parentColumns = ["portableId"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = FoodSearchEntity::class, parentColumns = ["key"], childColumns = ["ownerKey"], onDelete = ForeignKey.CASCADE),
], indices = [Index("ownerKey")])
data class FoodHistoryMember(@PrimaryKey val entryId: String, val ownerKey: String)
data class HistorySearchRow(@Embedded val snapshot: FoodSearchEntity, val entryId: String, val count: Int, val lastDay: Long)
data class HistoryAggregate(val entryId: String?, val count: Int, val lastDay: Long?)
