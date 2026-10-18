package com.justcal.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.data.backup.*
import com.justcal.app.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class DiaryBackupPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val recoveryDirectory = java.io.File(context.cacheDir, "backup-test-${java.util.UUID.randomUUID()}")
    private val isolatedContext = object : android.content.ContextWrapper(context) {
        override fun getNoBackupFilesDir(): java.io.File = recoveryDirectory
    }
    private lateinit var database: DiaryDatabase
    private lateinit var backup: DiaryBackupRepository
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java).build()
        recoveryDirectory.mkdirs()
        backup = DiaryBackupRepository(database, isolatedContext)
    }
    @After fun tearDown() { database.close(); recoveryDirectory.deleteRecursively() }
    private suspend fun add(name: String = "Yogurt") = RoomDiaryRepository(database.diaryDao()).save(
        DiaryEntry(name = name, per100g = NutritionPer100g(20550, 1234, 567, 2001),
            eatenGramsHundredths = 3751, dayEpoch = 20738, createdAtMillis = 123))

    @Test fun freshImportRepeatAndConflictPreserveIdentityAndData() = runBlocking {
        val id = add()
        val original = database.diaryDao().getAll().single()
        val out = ByteArrayOutputStream()
        backup.export(out)
        val preview = backup.preview(ByteArrayInputStream(out.toByteArray()))
        database.diaryDao().deleteAll()
        assertEquals(1, backup.importBackup(preview).imported)
        assertEquals(original.portableId, database.diaryDao().getAll().single().portableId)
        assertEquals(1, backup.importBackup(preview).skipped)
        assertEquals(1, backup.importBackup(preview.copy(entries = preview.entries.map { it.copy(name = "Conflicting") })).conflicting)
        assertEquals("Yogurt", database.diaryDao().getAll().single().name)
        val restoredId = database.diaryDao().getAll().single().id
        val repository = RoomDiaryRepository(database.diaryDao())
        repository.save(requireNotNull(repository.get(restoredId)).copy(name = "Edited"))
        assertEquals(original.portableId, database.diaryDao().getAll().single().portableId)
        assertTrue(id > 0)
    }

    @Test fun duplicateMoveTotalsAndBackupRoundTrip() = runBlocking {
        val repository = RoomDiaryRepository(database.diaryDao())
        val id = add()
        val original = requireNotNull(database.diaryDao().get(id))
        val today = java.time.LocalDate.now().toEpochDay()
        val destination = today + 30
        val copyId = repository.duplicate(id, today, 456)
        val secondId = repository.duplicate(id, destination, 789)
        val copy = requireNotNull(database.diaryDao().get(copyId))
        val second = requireNotNull(database.diaryDao().get(secondId))
        assertEquals(3, setOf(original.id, copy.id, second.id).size)
        assertEquals(3, setOf(original.portableId, copy.portableId, second.portableId).size)
        assertEquals(original.copy(id = copy.id, portableId = copy.portableId, dayEpoch = today, createdAtMillis = 456), copy)
        val initialTotal = NutritionCalculator.total(listOf(original.toDomain()))
        repository.changeDate(id, destination)
        val moved = requireNotNull(database.diaryDao().get(id))
        assertEquals(original.copy(dayEpoch = destination), moved)
        val all = database.diaryDao().getAll()
        assertEquals(initialTotal + initialTotal, NutritionCalculator.total(repository.observeDay(destination).first()))
        assertEquals(initialTotal, NutritionCalculator.total(repository.observeDay(today).first()))
        assertTrue(repository.observeDay(original.dayEpoch).first().isEmpty())
        val out = ByteArrayOutputStream()
        backup.export(out)
        val snapshot = backup.preview(ByteArrayInputStream(out.toByteArray()))
        database.diaryDao().deleteAll()
        assertEquals(3, backup.importBackup(snapshot).imported)
        val restored = database.diaryDao().getAll()
        assertEquals(all.associateBy { it.portableId }.mapValues { it.value.copy(id = 0) },
            restored.associateBy { it.portableId }.mapValues { it.value.copy(id = 0) })
        assertEquals(3, backup.importBackup(snapshot).skipped)
    }

    @Test fun replacementCreatesRecoverableBackupAndInvalidImportLeavesData() = runBlocking {
        add("Existing")
        val empty = DiaryBackup(exportedAtMillis = 1, appVersion = "test", entries = emptyList())
        assertThrows(IllegalArgumentException::class.java) { DiaryImportValidator.validate(empty.copy(schemaVersion = 999)) }
        assertEquals(1, database.diaryDao().getAll().size)
        assertEquals(0, backup.importBackup(empty, replaceAll = true).imported)
        assertTrue(database.diaryDao().getAll().isEmpty())
        assertTrue(backup.recoveryAvailable())
        val output = ByteArrayOutputStream()
        backup.exportRecovery(output)
        val recovery = backup.preview(ByteArrayInputStream(output.toByteArray()))
        assertEquals("Existing", recovery.entries.single().name)
        assertEquals(1, backup.importBackup(recovery).imported)
    }

    @Test fun sqliteInsertFailureRollsBackEarlierImportedRows() = runBlocking {
        add()
        val output = ByteArrayOutputStream()
        backup.export(output)
        val original = backup.preview(ByteArrayInputStream(output.toByteArray())).entries.single()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_bad BEFORE INSERT ON diary_entries WHEN NEW.name = 'bad' BEGIN SELECT RAISE(ABORT, 'test'); END")
        val records = listOf(original.copy(id = java.util.UUID.randomUUID().toString(), name = "new"),
            original.copy(id = java.util.UUID.randomUUID().toString(), name = "bad"))
        var failed = false
        try { backup.importBackup(DiaryBackup(exportedAtMillis = 1, appVersion = "test", entries = records)) }
        catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertEquals(listOf("Yogurt"), database.diaryDao().getAll().map { it.name })
        failed = false
        try { backup.importBackup(DiaryBackup(exportedAtMillis = 1, appVersion = "test", entries = records), replaceAll = true) }
        catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertEquals(listOf("Yogurt"), database.diaryDao().getAll().map { it.name })
        val recoveryOutput = ByteArrayOutputStream()
        backup.exportRecovery(recoveryOutput)
        assertEquals("Yogurt", backup.preview(ByteArrayInputStream(recoveryOutput.toByteArray())).entries.single().name)
    }
}
