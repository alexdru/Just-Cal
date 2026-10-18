package com.justcal.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.MainActivity
import com.justcal.app.R
import com.justcal.app.data.DiaryDatabase
import com.justcal.app.data.RoomDiaryRepository
import com.justcal.app.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiaryEntryMenuTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun label(id: Int) = compose.activity.getString(id)
    private fun click(id: Int) = compose.onNodeWithText(label(id)).performClick()
    private fun dateText(date: LocalDate, style: FormatStyle) =
        date.format(DateTimeFormatter.ofLocalizedDate(style).withLocale(compose.activity.resources.configuration.locales[0]))
    private fun hold(name: String, index: Int = 0) =
        compose.onAllNodesWithText(name)[index].performScrollTo().performTouchInput { longClick() }

    @Test fun longPressActionsWorkOnHomeAndRetainedDayWithoutOpeningEditor() {
        val name = "Entry menu verification " + UUID.randomUUID()
        val today = LocalDate.now()
        val destination = today.plusDays(1)
        val database = Room.databaseBuilder(compose.activity.applicationContext, DiaryDatabase::class.java, "just-cal.db").build()
        val repository = RoomDiaryRepository(database.diaryDao())
        val baseline = runBlocking { NutritionCalculator.total(repository.observeDay(today.toEpochDay()).first()) }
        try {
            val id = runBlocking {
                repository.save(DiaryEntry(name = name, per100g = NutritionPer100g(20000, 1000, 500, 2000),
                    eatenGramsHundredths = 12500, dayEpoch = today.toEpochDay(), createdAtMillis = 1,
                    source = EntrySource.PACKAGE_SCAN))
            }
            val original = runBlocking { requireNotNull(database.diaryDao().get(id)) }
            compose.waitUntil(5000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
            // Opening and cancelling each confirmation is read-only.
            for (action in listOf(R.string.duplicate_entry, R.string.change_entry_date, R.string.delete_food)) {
                hold(name)
                click(action)
                click(R.string.cancel)
                assertEquals(listOf(original), runBlocking { database.diaryDao().getAll().filter { it.name == name } })
                compose.onNodeWithText(label(R.string.edit_food)).assertDoesNotExist()
            }
            hold(name)
            click(R.string.duplicate_entry)
            click(R.string.duplicate_entry)
            compose.waitUntil(5000) { runBlocking { database.diaryDao().getAll().count { it.name == name } == 2 } }
            val copy = runBlocking { database.diaryDao().getAll().single { it.name == name && it.id != id } }
            assertNotEquals(original.portableId, copy.portableId)
            assertTotal((baseline.energyKcal + 500.toBigDecimal()).display(0))

            // The newest row is the duplicate; move it directly from Home.
            hold(name)
            click(R.string.change_entry_date)
            compose.onNode(hasText(dateText(destination, FormatStyle.FULL), substring = true, ignoreCase = true) and hasClickAction()).performClick()
            click(R.string.done)
            compose.waitUntil(5000) { runBlocking { database.diaryDao().get(copy.id)?.dayEpoch == destination.toEpochDay() } }
            assertEquals(copy.copy(dayEpoch = destination.toEpochDay()), runBlocking { database.diaryDao().get(copy.id) })
            assertTotal((baseline.energyKcal + 250.toBigDecimal()).display(0))
            compose.onNodeWithText(label(R.string.edit_food)).assertDoesNotExist()

            // Delete the original from Home, then delete the copy from Day Detail.
            hold(name)
            click(R.string.delete_food)
            click(R.string.delete_confirm)
            compose.waitUntil(5000) { runBlocking { repository.get(id) == null } }
            assertTotal(baseline.energyKcal.display(0))
            compose.onNode(hasText(label(R.string.history)) and hasClickAction()).performClick()
            compose.onNode(hasText(dateText(destination, FormatStyle.MEDIUM)) and hasClickAction()).performScrollTo().performClick()
            hold(name)
            click(R.string.delete_food)
            click(R.string.delete_confirm)
            compose.waitUntil(5000) { runBlocking { repository.get(copy.id) == null } }
            compose.onNodeWithText(dateText(destination, FormatStyle.MEDIUM)).assertExists()
            compose.onNodeWithText(label(R.string.home)).performClick()
            compose.onNode(hasText(label(R.string.history)) and hasClickAction()).performClick()
            compose.onNodeWithText(dateText(destination, FormatStyle.MEDIUM)).assertExists()
        } finally {
            runBlocking { database.diaryDao().getAll().filter { it.name == name }.forEach { database.diaryDao().delete(it.id) } }
            database.close()
        }
    }

    private fun assertTotal(value: String) {
        compose.onNode(hasText(value) and hasAnyAncestor(hasTestTag("calorie_value"))).assertExists()
    }
}
