package com.justcal.app.data

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.domain.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic workload measures bounded work; it is not an accuracy or real-photo benchmark. */
@RunWith(AndroidJUnit4::class)
class FoodMatchingPerformanceTest {
    @Test fun tenThousandProductsAndThreeThousandHistoryEntries() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java).build()
        try {
            db.withTransaction {
                repeat(10_000) { i ->
                    val product = UUID(1, i.toLong()).toString()
                    db.foodCatalogDao().insertProduct(ProductEntity(product, 1, false))
                    db.foodCatalogDao().insertRevision(ProductRevisionEntity.from(ProductRevision(
                        UUID(2, i.toLong()).toString(), product, name = "Vanilla almond yogurt $i",
                        energy = 12000 + i.toLong(), protein = 400, fat = 500, carbs = 1800, savedAtMillis = 1)))
                }
                repeat(3000) { i ->
                    db.diaryDao().insert(DiaryEntryEntity(0, "Almond yogurt $i", 12000, 400, 500, 1800,
                        3751, 20738, 1, "MANUAL"))
                }
            }
            val repository = RoomFoodCatalogRepository(db)
            val coldStart = System.nanoTime()
            var batches = 1
            while (repository.buildHistoryBatch()) { batches++ }
            assertTrue(batches >= 100)
            val coldMs = (System.nanoTime() - coldStart) / 1_000_000.0
            val times = mutableListOf<Double>()
            val retrieval = mutableListOf<Double>(); val scoring = mutableListOf<Double>()
            repeat(20) {
                val start = System.nanoTime()
                val result = repository.search(FoodMatchEvidence("Vanilla almond yogurt 42"))
                times += (System.nanoTime() - start) / 1_000_000.0
                retrieval += result.retrievalMillis; scoring += result.scoringMillis
                assertFalse(result.indexing)
                assertTrue(result.candidates.size <= 200)
            }
            times.sort()
            Log.i("FoodMatchingPerf", "device=" + Build.MODEL + " API=" + Build.VERSION.SDK_INT +
                " debug in-memory; products=10000 history=3000 coldIndexMs=" + coldMs +
                " retrievalMedianMs=" + retrieval.sorted()[10] + " scoringMedianMs=" + scoring.sorted()[10] +
                " warmSamples=20 medianMs=" + times[10] + " p95Ms=" + times[18] + " maxMs=" + times.last())
        } finally { db.close() }
    }
}
