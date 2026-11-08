package com.justcal.app.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.data.backup.*
import com.justcal.app.domain.*
import com.justcal.app.domain.Retention
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogBackupPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun uuid() = UUID.randomUUID().toString()
    @Test fun fullGraphExportReimportConflictsReplacementAndRecovery() = runBlocking {
        val dir = File(context.cacheDir, uuid()).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getNoBackupFilesDir() = dir }
        val db = Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java).build()
        try {
            val repo = DiaryBackupRepository(db, isolated)
            val catalog = RoomFoodCatalogRepository(db)
            val entry = DiaryEntry(name = "Vanilla almond yogurt", per100g = NutritionPer100g(12000, 400, 500, 1800),
                eatenGramsHundredths = 3751, dayEpoch = 20738, createdAtMillis = 123)
            val request = CatalogSave(uuid(), uuid(), uuid(), Retention.NEW_PRODUCT)
            catalog.save(entry, request)
            val output = ByteArrayOutputStream()
            repo.export(output)
            val original = repo.preview(ByteArrayInputStream(output.toByteArray()))
            assertEquals(2, original.schemaVersion)
            assertEquals(1, original.products.size)
            val repeat = repo.importBackup(original)
            assertEquals(1, repeat.skipped)
            assertEquals(1, repeat.products.skipped)
            assertEquals(1, repeat.revisions.skipped)
            assertEquals(1, repeat.links.skipped)
            val conflicting = original.copy(products = original.products.map { it.copy(archived = true) })
            val conflict = repo.importBackup(conflicting)
            assertEquals(1, conflict.products.conflicting)
            assertEquals(1, conflict.revisions.conflicting)
            assertEquals(1, conflict.links.conflicting)
            assertFalse(db.foodCatalogDao().products().single().archived)

            // V1 replacement retains catalog but drops old consumption links.
            repo.importBackup(DiaryBackup(exportedAtMillis = 1, appVersion = "test", entries = emptyList()), true)
            assertTrue(db.diaryDao().getAll().isEmpty())
            assertEquals(1, db.foodCatalogDao().products().size)
            assertTrue(db.foodCatalogDao().links().isEmpty())
            val recoveryOut = ByteArrayOutputStream()
            repo.exportRecovery(recoveryOut)
            val recovery = repo.preview(ByteArrayInputStream(recoveryOut.toByteArray()))
            assertEquals(original.products, recovery.products)
            assertEquals(original.revisions, recovery.revisions)
            assertEquals(original.links, recovery.links)
            repo.importBackup(recovery, true)
            assertEquals(1, db.diaryDao().getAll().size)
            assertEquals(1, db.foodCatalogDao().links().size)

            // A failing diary insert rolls back catalog replacement and keeps full recovery.
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_bad BEFORE INSERT ON diary_entries WHEN NEW.name = 'bad' BEGIN SELECT RAISE(ABORT, 'test'); END")
            var failed = false
            try { repo.importBackup(original.copy(entries = original.entries.map { it.copy(name = "bad") }), true) }
            catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertEquals(entry.name, db.diaryDao().getAll().single().name)
            assertEquals(original.products, db.foodCatalogDao().products().map { it.domain() })
            val out = ByteArrayOutputStream()
            repo.exportRecovery(out)
            assertEquals(original.links, repo.preview(ByteArrayInputStream(out.toByteArray())).links)

            // Recovery write failure aborts before any deletion.
            val blocked = object : ContextWrapper(context) { override fun getNoBackupFilesDir() = File(dir, "missing/child") }
            failed = false
            try { DiaryBackupRepository(db, blocked).importBackup(original.copy(entries = emptyList(), links = emptyList()), true) }
            catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertEquals(1, db.diaryDao().getAll().size)
        } finally { db.close(); dir.deleteRecursively() }
    }
}
