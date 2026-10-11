package com.justcal.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DiaryMigrationTest {
    @Test fun versionOneRowsAndLocalIdsSurviveWithStablePortableIdentity() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-backup-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("""CREATE TABLE diary_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL,
                energyKcalHundredths INTEGER NOT NULL, proteinGramsHundredths INTEGER NOT NULL,
                fatGramsHundredths INTEGER NOT NULL, carbsGramsHundredths INTEGER NOT NULL,
                eatenGramsHundredths INTEGER NOT NULL, dayEpoch INTEGER NOT NULL,
                createdAtMillis INTEGER NOT NULL, source TEXT NOT NULL)""")
            old.execSQL("CREATE INDEX index_diary_entries_dayEpoch ON diary_entries(dayEpoch)")
            old.execSQL("INSERT INTO diary_entries VALUES (42,'Йогурт',20550,1234,567,2001,3751,20738,123,'PACKAGE_SCAN')")
            old.execSQL("INSERT INTO diary_entries VALUES (43,'Bread',12000,100,200,300,1000,20737,124,'MANUAL')")
            old.version = 1
        }
        fun open() = Room.databaseBuilder(context, DiaryDatabase::class.java, name)
            .addMigrations(DiaryDatabase.MIGRATION_1_2).build()
        var database = open()
        try {
            val rows = database.diaryDao().getAll()
            val yogurt = rows.single { it.id == 42L }
            assertEquals("Йогурт", yogurt.name)
            assertEquals(3751L, yogurt.eatenGramsHundredths)
            assertEquals(2, rows.map { it.portableId }.toSet().size)
            rows.forEach { assertEquals(it.portableId, UUID.fromString(it.portableId).toString()) }
            val identity = yogurt.portableId
            database.close()
            database = open()
            assertEquals(identity, database.diaryDao().get(42)!!.portableId)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
