package com.justcal.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiaryPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: DiaryDatabase
    private fun open() = Room.databaseBuilder(context, DiaryDatabase::class.java, "diary-test.db").build()
    @Before fun setUp() { context.deleteDatabase("diary-test.db"); database = open() }
    @After fun tearDown() { database.close(); context.deleteDatabase("diary-test.db") }

    @Test fun crudSurvivesReopeningAndKeepsDaysSeparate() = runBlocking {
        var repository = RoomDiaryRepository(database.diaryDao())
        val entry = DiaryEntry(name = "Yogurt", per100g = NutritionPer100g(20550, 1234, 567, 2001),
            eatenGramsHundredths = 3750, dayEpoch = 20730, createdAtMillis = 123)
        val id = repository.save(entry)
        repository.save(entry.copy(dayEpoch = 20729))
        assertEquals(listOf(id), repository.observeDay(20730).first().map { it.id })

        database.close()
        database = open()
        repository = RoomDiaryRepository(database.diaryDao())
        assertEquals(entry.copy(id = id), repository.get(id))

        repository.save(requireNotNull(repository.get(id)).copy(name = "Edited", eatenGramsHundredths = 5000))
        val updated = repository.observeDay(20730).first().single()
        assertEquals("Edited", updated.name)
        assertEquals("102.75", NutritionCalculator.consumed(updated.per100g, updated.eatenGramsHundredths).energyKcal.display(2))
        repository.delete(id)
        assertTrue(repository.observeDay(20730).first().isEmpty())
        assertEquals(1, repository.observeDay(20729).first().size)
    }
}
