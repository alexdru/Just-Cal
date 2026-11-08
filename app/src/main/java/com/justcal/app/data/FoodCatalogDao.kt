package com.justcal.app.data

import androidx.room.*

@Dao
interface FoodCatalogDao {
    @Insert suspend fun insertProduct(product: ProductEntity)
    @Insert suspend fun insertRevision(revision: ProductRevisionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun link(link: EntryProductLinkEntity)
    @Query("SELECT * FROM products WHERE id = :id") suspend fun product(id: String): ProductEntity?
    @Query("SELECT * FROM product_revisions WHERE id = :id") suspend fun revision(id: String): ProductRevisionEntity?
    @Query("SELECT r.* FROM product_revisions r JOIN products p ON p.id = r.productId WHERE r.productId = :id AND p.archived = 0 ORDER BY r.savedAtMillis DESC, r.id LIMIT 20 OFFSET :offset")
    suspend fun revisions(id: String, offset: Int): List<ProductRevisionEntity>
    @Query("UPDATE products SET archived = 1 WHERE id = :id") suspend fun archive(id: String)
    @Query("DELETE FROM food_search WHERE source = 'R' AND substr(`key`, 3) IN (SELECT id FROM product_revisions WHERE productId = :id)") suspend fun removeProductProjections(id: String)
    @Query("DELETE FROM products WHERE id = :id") suspend fun forget(id: String)
    @Query("DELETE FROM products") suspend fun deleteProducts()
    @Query("DELETE FROM entry_product_links") suspend fun deleteLinks()
    @Query("SELECT * FROM products ORDER BY id") suspend fun products(): List<ProductEntity>
    @Query("SELECT * FROM product_revisions ORDER BY id") suspend fun allRevisions(): List<ProductRevisionEntity>
    @Query("SELECT * FROM entry_product_links ORDER BY entryId") suspend fun links(): List<EntryProductLinkEntity>
    @Query("""SELECT r.* FROM product_revisions r JOIN products p ON p.id = r.productId
        WHERE r.id IN (:ids) AND p.archived = 0""")
    suspend fun revisionSnapshots(ids: List<String>): List<ProductRevisionEntity>
    @Query("""SELECT s.*, MIN(m.entryId) AS entryId, COUNT(*) AS count, MAX(d.dayEpoch) AS lastDay
        FROM food_search s JOIN food_history_members m ON m.ownerKey = s.`key`
        JOIN diary_entries d ON d.portableId = m.entryId WHERE s.`key` IN (:keys) GROUP BY s.`key`""")
    suspend fun historySnapshots(keys: List<String>): List<HistorySearchRow>
    @Query("SELECT * FROM entry_product_links WHERE entryId = :id") suspend fun entryLink(id: String): EntryProductLinkEntity?
    @Query("SELECT * FROM diary_entries WHERE portableId = :uuid") suspend fun entry(uuid: String): DiaryEntryEntity?
    @Query("""SELECT d.* FROM diary_entries d WHERE d.id > :afterId
        AND NOT EXISTS(SELECT 1 FROM food_history_members m WHERE m.entryId = d.portableId)
        AND NOT EXISTS(SELECT 1 FROM entry_product_links l WHERE l.entryId = d.portableId)
        ORDER BY d.id LIMIT 100""")
    suspend fun unindexedHistory(afterId: Long = 0): List<DiaryEntryEntity>
    @Query("""SELECT r.* FROM product_revisions r JOIN products p ON p.id = r.productId
        WHERE p.archived = 0 AND NOT EXISTS(SELECT 1 FROM food_search s WHERE s.`key` = 'R:' || r.id)
        ORDER BY r.id LIMIT 100""")
    suspend fun unindexedRevisions(): List<ProductRevisionEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun projection(row: FoodSearchEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun tokens(rows: List<FoodTokenEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun member(row: FoodHistoryMember)
    @Query("DELETE FROM food_history_members WHERE entryId = :uuid") suspend fun removeMember(uuid: String)
    @Query("DELETE FROM entry_product_links WHERE entryId = :uuid") suspend fun unlink(uuid: String)
    @Query("DELETE FROM food_search") suspend fun invalidate()
    @Query("SELECT * FROM food_search WHERE `key` = :key") suspend fun projection(key: String): FoodSearchEntity?
    @Query("""SELECT MIN(m.entryId) AS entryId, COUNT(*) AS count, MAX(d.dayEpoch) AS lastDay
        FROM food_history_members m JOIN diary_entries d ON d.portableId = m.entryId WHERE m.ownerKey = :key""")
    suspend fun history(key: String): HistoryAggregate
    @Query("""SELECT DISTINCT token FROM food_tokens WHERE token >= :prefix AND token < :end
        AND length(token) BETWEEN :minLength AND :maxLength ORDER BY token LIMIT 50""")
    suspend fun variants(prefix: String, end: String, minLength: Int, maxLength: Int): List<String>
    @Query("""SELECT s.* FROM food_search s JOIN food_tokens t ON t.ownerKey = s.`key`
        WHERE t.token IN (:tokens) AND s.source = :source
        AND ((s.source = 'H' AND EXISTS(SELECT 1 FROM food_history_members m WHERE m.ownerKey = s.`key`))
          OR (s.source = 'R' AND EXISTS(SELECT 1 FROM product_revisions r JOIN products p ON p.id = r.productId
             WHERE r.id = substr(s.`key`, 3) AND p.archived = 0)))
        GROUP BY s.`key` ORDER BY COUNT(*) DESC, s.`key` LIMIT :limit""")
    suspend fun retrieve(tokens: List<String>, source: String, limit: Int): List<FoodSearchEntity>
    @Query("SELECT COUNT(*) FROM food_search WHERE normalizationVersion != :version") suspend fun staleProjections(version: Int): Int
    @Query("""SELECT s.* FROM food_search s JOIN (
        SELECT `key`, COUNT(*) AS overlap FROM (
          SELECT `key` FROM (SELECT `key` FROM food_search WHERE source = :source AND energy BETWEEN :eLow AND :eHigh AND :eLow IS NOT NULL ORDER BY energy, `key` LIMIT 201)
          UNION ALL SELECT `key` FROM (SELECT `key` FROM food_search WHERE source = :source AND protein BETWEEN :pLow AND :pHigh AND :pLow IS NOT NULL ORDER BY protein, `key` LIMIT 201)
          UNION ALL SELECT `key` FROM (SELECT `key` FROM food_search WHERE source = :source AND fat BETWEEN :fLow AND :fHigh AND :fLow IS NOT NULL ORDER BY fat, `key` LIMIT 201)
          UNION ALL SELECT `key` FROM (SELECT `key` FROM food_search WHERE source = :source AND carbs BETWEEN :cLow AND :cHigh AND :cLow IS NOT NULL ORDER BY carbs, `key` LIMIT 201)
        ) GROUP BY `key` HAVING COUNT(*) >= 2
      ) n ON n.`key` = s.`key` WHERE s.source = :source ORDER BY n.overlap DESC, s.`key` LIMIT 201""")
    suspend fun retrieveNutrition(source: String, eLow: Long?, eHigh: Long?, pLow: Long?, pHigh: Long?,
        fLow: Long?, fHigh: Long?, cLow: Long?, cHigh: Long?): List<FoodSearchEntity>
    @Query("SELECT r.* FROM product_revisions r JOIN products p ON p.id = r.productId WHERE p.archived = 0 AND r.name = :name AND r.energy = :energy AND r.protein = :protein AND r.fat = :fat AND r.carbs = :carbs ORDER BY r.id LIMIT 20")
    suspend fun duplicates(name: String, energy: Long, protein: Long, fat: Long, carbs: Long): List<ProductRevisionEntity>
}
