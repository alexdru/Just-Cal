package com.justcal.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.domain.*
import com.justcal.app.domain.Retention
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodCatalogPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private suspend fun DiaryDatabase.use(block: suspend (DiaryDatabase) -> Unit) { try { block(this) } finally { close() } }
    private fun uuid() = UUID.randomUUID().toString()
    private fun entry(name: String = "Vanilla almond yogurt") = DiaryEntry(name = name,
        per100g = NutritionPer100g(12000, 400, 500, 1800), eatenGramsHundredths = 3751,
        dayEpoch = 20738, createdAtMillis = 123)
    private fun request(retention: Retention = Retention.NEW_PRODUCT) =
        CatalogSave(uuid(), uuid(), uuid(), retention)
    @Test fun historyIsLazyGroupedAndNeverPromoted() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java).addCallback(DiaryDatabase.CALLBACK).build().use { db ->
            val diary = RoomDiaryRepository(db.diaryDao())
            val catalog = RoomFoodCatalogRepository(db)
            val id = diary.save(entry())
            diary.save(entry())
            val variant = entry().copy(per100g = NutritionPer100g(12000, 400, 1000, 1800))
            diary.save(variant)
            val before = db.diaryDao().getAll()
            assertFalse(catalog.buildHistoryBatch())
            val result = catalog.search(FoodMatchEvidence("Vanilla almond yogurt"))
            assertEquals(2, result.candidates.size)
            assertEquals(2, result.candidates.maxOf { it.historyCount })
            assertTrue(db.foodCatalogDao().products().isEmpty())
            assertEquals(before, db.diaryDao().getAll())
            diary.save(entry().copy(id = id, name = "Changed recipe"))
            catalog.buildHistoryBatch()
            assertEquals(1, catalog.search(FoodMatchEvidence("Vanilla almond yogurt")).candidates.maxOf { it.historyCount })
        }
    }
    @Test fun atomicSaveRetryRevisionArchiveAndForgetPreserveDiarySnapshots() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java).addCallback(DiaryDatabase.CALLBACK).build().use { db ->
            val catalog = RoomFoodCatalogRepository(db)
            val first = request()
            val id = catalog.save(entry(), first)
            assertEquals(id, catalog.save(entry(), first))
            assertEquals(1, db.foodCatalogDao().products().size)
            assertEquals(1, db.diaryDao().getAll().size)
            try { catalog.save(entry("Different"), first); fail("Mismatched retry accepted") } catch (_: IllegalStateException) { }
            val before = db.diaryDao().get(id)
            val second = request(Retention.NEW_REVISION).copy(selectedRevision = first.revisionUuid)
            catalog.save(entry().copy(per100g = NutritionPer100g(13000, 400, 500, 1800)), second)
            assertEquals(2, db.foodCatalogDao().allRevisions().size)
            assertEquals(before, db.diaryDao().get(id))
            assertEquals(first.revisionUuid, db.foodCatalogDao().revision(second.revisionUuid)!!.previousRevisionId)
            catalog.archive(first.productUuid)
            assertNull(catalog.candidate("R:" + first.revisionUuid))
            catalog.forget(first.productUuid)
            assertTrue(db.foodCatalogDao().links().isEmpty())
            assertEquals(2, db.diaryDao().getAll().size)
        }
    }
    @Test fun partialNutritionCanRetrieveHistoryWithoutNameAndPromotionSurvivesDeletion() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java).addCallback(DiaryDatabase.CALLBACK).build().use { db ->
            val diary = RoomDiaryRepository(db.diaryDao(), db)
            val sourceId = diary.save(entry())
            val catalog = RoomFoodCatalogRepository(db)
            val result = catalog.search(FoodMatchEvidence("", listOf(MatchNumber(12000, MatchEvidenceState.KNOWN),
                MatchNumber(400, MatchEvidenceState.KNOWN), MatchNumber(), MatchNumber()), MatchNutritionBasis.PER_100_G))
            assertEquals(1, result.candidates.size)
            assertEquals(FoodMatchCategory.INSUFFICIENT, result.candidates.single().match!!.category)
            val save = request()
            catalog.save(entry().copy(dayEpoch = 20739), save.copy(provenance = "HISTORY_PROMOTION"))
            diary.delete(sourceId)
            assertNotNull(catalog.candidate("R:" + save.revisionUuid))
            assertEquals(1, db.foodCatalogDao().products().size)
        }
    }
    @Test fun failedFinalLinkRollsBackEntireCreationAndRetryIsIdempotent() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java).build().use { db ->
            val catalog = RoomFoodCatalogRepository(db); val identity = request()
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_link BEFORE INSERT ON entry_product_links BEGIN SELECT RAISE(ABORT, 'test'); END")
            var failed = false
            try { catalog.save(entry(), identity) } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertTrue(db.foodCatalogDao().products().isEmpty())
            assertTrue(db.diaryDao().getAll().isEmpty())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_link")
            val id = catalog.save(entry(), identity)
            assertEquals(id, catalog.save(entry(), identity))
            assertEquals(1, db.diaryDao().getAll().size)
            assertEquals(1, db.foodCatalogDao().allRevisions().size)
            catalog.save(entry(), request())
            assertEquals(2, db.foodCatalogDao().products().size)
        }
    }
    @Test fun historyBuildIsBoundedAndRestartable() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java).build().use { db ->
            repeat(205) { RoomDiaryRepository(db.diaryDao()).save(entry("History almond " + it)) }
            val catalog = RoomFoodCatalogRepository(db)
            assertTrue(catalog.buildHistoryBatch())
            assertTrue(catalog.search(FoodMatchEvidence("History almond")).indexing)
            assertTrue(RoomFoodCatalogRepository(db).buildHistoryBatch())
            assertFalse(catalog.buildHistoryBatch())
            assertFalse(catalog.search(FoodMatchEvidence("History almond")).indexing)
            assertEquals(205, db.diaryDao().getAll().size)
        }
    }
}
