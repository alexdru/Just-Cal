package com.justcal.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.MainActivity
import com.justcal.app.R
import com.justcal.app.data.DiaryDatabase
import com.justcal.app.data.GoalPreferences
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
class DiaryNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun label(id: Int) = compose.activity.getString(id)
    private fun tab(id: Int) = compose.onNode(hasText(label(id)) and hasClickAction())
    private fun field(id: Int) = compose.onNode(hasText(label(id)) and hasSetTextAction())
    private fun back() = compose.onNodeWithContentDescription(label(R.string.close)).performClick()
    private fun fab() = compose.onNodeWithContentDescription(label(R.string.add_food))
    private fun dateText(date: LocalDate, style: FormatStyle): String =
        date.format(DateTimeFormatter.ofLocalizedDate(style).withLocale(compose.activity.resources.configuration.locales[0]))

    @Test fun bothDayPathsRetainTheirStackAndFabDateAndSupportCrud() {
        val date = LocalDate.now().withDayOfMonth(1)
        val name = "Diary verification " + UUID.randomUUID()
        val existing = Room.databaseBuilder(compose.activity.applicationContext, DiaryDatabase::class.java, "just-cal.db").build()
        val baseline = try {
            runBlocking { NutritionCalculator.total(existing.diaryDao().observeDay(date.toEpochDay()).first().map { it.toDomain() }) }
        } finally { existing.close() }
        val addedTotal = (baseline.energyKcal + 250.toBigDecimal()).display(0)
        val editedTotal = (baseline.energyKcal + 500.toBigDecimal()).display(0)
        try {
            fab().performClick()
            compose.onNodeWithText(label(R.string.add_manually)).performClick()
            compose.onNodeWithText(dateText(LocalDate.now(), FormatStyle.MEDIUM)).assertExists()
            back()
            tab(R.string.history).performClick()
            fab().performClick()
            compose.onNodeWithText(label(R.string.add_manually)).performClick()
            compose.onNodeWithText(dateText(LocalDate.now(), FormatStyle.MEDIUM)).assertExists()
            back()

            compose.onNodeWithText(label(R.string.choose_day)).performClick()
            compose.onNode(hasText(dateText(date, FormatStyle.FULL), substring = true) and hasClickAction()).performClick()
            compose.onNodeWithText(label(R.string.open_day)).performClick()
            compose.onNodeWithText(dateText(date, FormatStyle.MEDIUM)).assertExists()
            tab(R.string.settings).performClick()
            fab().assertDoesNotExist()
            tab(R.string.home).performClick()
            tab(R.string.history).performClick()
            compose.onNodeWithText(dateText(date, FormatStyle.MEDIUM)).assertExists()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText(dateText(date, FormatStyle.MEDIUM)).assertExists()

            fab().performClick()
            compose.onNodeWithText(label(R.string.add_manually)).performClick()
            compose.onNodeWithText(dateText(date, FormatStyle.MEDIUM)).assertExists()
            for ((id, value) in listOf(R.string.food_name to name, R.string.energy to "200",
                R.string.protein to "10", R.string.fat to "5", R.string.carbs to "20", R.string.amount to "125")) {
                field(id).performScrollTo().performTextReplacement(value)
            }
            compose.onNodeWithText(label(R.string.save_food)).performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText(addedTotal) and !hasClickAction()).assertExists()
            // The history list and direct calendar both open the existing, identical day destination.
            back()
            compose.onNode(hasText(if (date == LocalDate.now()) label(R.string.today) else dateText(date, FormatStyle.MEDIUM)) and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithText(dateText(date, FormatStyle.MEDIUM)).assertExists()
            compose.onNodeWithText(name).performScrollTo().performClick()
            field(R.string.amount).performScrollTo().performTextReplacement("250")
            compose.onNodeWithText(label(R.string.save_changes)).performClick()
            compose.waitUntil(5000) { compose.onAllNodes(hasText(editedTotal) and !hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(name).performScrollTo().performClick()
            compose.onNodeWithContentDescription(label(R.string.delete_food)).performClick()
            compose.onNodeWithText(label(R.string.delete_confirm)).performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isEmpty() }
            back()
            compose.onNodeWithText(label(R.string.choose_day)).assertExists()
        } finally {
            // Clean only this test's entry if an assertion interrupted the UI deletion.
            val database = Room.databaseBuilder(compose.activity.applicationContext, DiaryDatabase::class.java, "just-cal.db").build()
            try {
                runBlocking {
                    database.diaryDao().observeAll().first().filter { it.name == name }.forEach { database.diaryDao().delete(it.id) }
                }
            } finally { database.close() }
        }
    }

    @Test fun optionalGoalsSaveClearIndependentlyAndUpdateRetainedDayAndHome() {
        val preferences = GoalPreferences(compose.activity.applicationContext)
        val original = runBlocking { preferences.settings.first() }
        try {
            runBlocking { preferences.save(AppSettings(goalKcal = 2200, appearance = original.appearance)) }
            tab(R.string.history).performClick()
            compose.onNodeWithText(label(R.string.choose_day)).performClick()
            compose.onNodeWithText(label(R.string.open_day)).performClick()
            tab(R.string.settings).performClick()
            fab().assertDoesNotExist()
            compose.onNodeWithText(label(R.string.macro_goals)).performScrollTo().performClick()
            field(R.string.protein).performScrollTo().performTextReplacement("160")
            compose.onNodeWithText(label(R.string.save_changes)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.waitUntil(5000) { runBlocking { preferences.settings.first().proteinGoalGramsHundredths == 16000L } }
            val proteinOnly = runBlocking { preferences.settings.first() }
            assertNull(proteinOnly.fatGoalGramsHundredths)
            assertNull(proteinOnly.carbsGoalGramsHundredths)
            tab(R.string.home).performClick()
            compose.onNode(hasText("/ 160", substring = true)).assertExists()
            compose.onAllNodes(hasText("/ 0", substring = true)).assertCountEquals(0)
            tab(R.string.history).performClick()
            compose.onNode(hasText("/ 160", substring = true)).assertExists()
            tab(R.string.settings).performClick()
            field(R.string.fat).performScrollTo().performTextReplacement("65,25")
            field(R.string.carbs).performScrollTo().performTextReplacement("230.01")
            field(R.string.goal_field).performScrollTo().performTextReplacement("2300")
            compose.onNodeWithText(label(R.string.save_changes)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.waitUntil(5000) { runBlocking { preferences.settings.first().carbsGoalGramsHundredths == 23001L } }
            assertEquals(6525L, runBlocking { preferences.settings.first() }.fatGoalGramsHundredths)
            for ((id, cleared) in listOf(R.string.protein to 0, R.string.fat to 1, R.string.carbs to 2)) {
                field(id).performScrollTo()
                compose.onNodeWithContentDescription(compose.activity.getString(R.string.clear_macro_goal, label(id))).performClick()
                compose.onNodeWithText(label(R.string.save_changes)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
                compose.waitUntil(5000) {
                    val saved = runBlocking { preferences.settings.first() }
                    when (cleared) {
                        0 -> saved.proteinGoalGramsHundredths == null
                        1 -> saved.fatGoalGramsHundredths == null
                        else -> saved.carbsGoalGramsHundredths == null
                    }
                }
                val saved = runBlocking { preferences.settings.first() }
                if (cleared == 0) {
                    assertEquals(6525L, saved.fatGoalGramsHundredths)
                    assertEquals(23001L, saved.carbsGoalGramsHundredths)
                }
            }
            compose.activityRule.scenario.recreate()
            field(R.string.goal_field).performScrollTo().assertTextContains("2300")
            tab(R.string.home).performClick()
            compose.onAllNodes(hasText("/", substring = true)).assertCountEquals(0)
            compose.onNodeWithText(compose.activity.getString(R.string.goal, "2300")).assertExists()
            tab(R.string.history).performClick()
            compose.onAllNodes(hasText("/", substring = true)).assertCountEquals(0)
        } finally {
            runBlocking { preferences.save(original) }
        }
    }
}
