package com.justcal.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.MainActivity
import com.justcal.app.R
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var original: com.justcal.app.domain.AppSettings
    @org.junit.Before fun retainOriginalPreferences() {
        original = kotlinx.coroutines.runBlocking {
            com.justcal.app.data.GoalPreferences(
                androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            ).settings.first()
        }
    }
    @org.junit.After fun restorePreferences() {
        compose.activityRule.scenario.close()
        kotlinx.coroutines.runBlocking {
            com.justcal.app.data.GoalPreferences(
                androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            ).save(original)
        }
    }
    private fun label(id: Int) = compose.activity.getString(id)
    private fun tab(id: Int) = compose.onNode(hasText(label(id)) and hasClickAction())
    private fun field(id: Int) = compose.onNode(hasText(label(id)) and hasSetTextAction())

    @Test fun autosavePreservesTypedWhitespaceAndDecimalSeparatorAndTracksExternalGoal() {
        val preferences = com.justcal.app.data.GoalPreferences(compose.activity)
        tab(R.string.settings).performClick()
        field(R.string.display_name).performTextReplacement("Alice ")
        compose.waitUntil(5000) {
            kotlinx.coroutines.runBlocking { preferences.settings.first().displayName == "Alice" }
        }
        field(R.string.display_name).assertTextContains("Alice ")
        // Existing saved macro goals expand this section automatically.
        if (compose.onAllNodes(hasText(label(R.string.fat)) and hasSetTextAction()).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithText(label(R.string.macro_goals)).performScrollTo().performClick()
        }
        field(R.string.fat).performScrollTo().performTextReplacement("65,25")
        compose.waitUntil(5000) {
            kotlinx.coroutines.runBlocking { preferences.settings.first().fatGoalGramsHundredths == 6525L }
        }
        field(R.string.fat).assertTextContains("65,25")
        kotlinx.coroutines.runBlocking { preferences.setGoal(2500) }
        compose.waitUntil(5000) {
            field(R.string.goal_field).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text == "2500"
        }
        field(R.string.display_name).assertTextContains("Alice ")
        val macroHeader = compose.onNodeWithText(label(R.string.macro_goals))
        macroHeader.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        tab(R.string.home).performClick()
        tab(R.string.settings).performClick()
        compose.onAllNodes(hasText(label(R.string.fat)) and hasSetTextAction()).assertCountEquals(0)
        macroHeader.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        field(R.string.fat).assertTextContains("65,25")
        compose.activityRule.scenario.recreate()
        field(R.string.fat).assertTextContains("65,25")
    }

    @Test fun draftSurvivesFieldChangesTabsAndActivityRecreation() {
        tab(R.string.settings).performClick()
        compose.waitUntil(5000) {
            compose.onAllNodes(hasText(label(R.string.display_name)) and hasSetTextAction()).fetchSemanticsNodes().size == 1
        }
        field(R.string.display_name).performTextReplacement("")
        field(R.string.display_name).performKeyInput {
            pressKey(Key.A); pressKey(Key.L); pressKey(Key.E); pressKey(Key.X)
        }
        field(R.string.goal_field).performTextReplacement("2345")
        field(R.string.display_name).assertTextContains("alex")
        tab(R.string.home).performClick()
        tab(R.string.settings).performClick()
        field(R.string.display_name).assertTextContains("alex")
        field(R.string.goal_field).assertTextContains("2345")

        compose.activityRule.scenario.recreate()
        field(R.string.display_name).assertTextContains("alex")
        field(R.string.goal_field).assertTextContains("2345")

        compose.waitUntil(5000) {
            kotlinx.coroutines.runBlocking {
                com.justcal.app.data.GoalPreferences(compose.activity).settings.first().goalKcal == 2345
            }
        }
        // Invalid required input stays a draft; the most recent valid goal remains persisted.
        field(R.string.goal_field).performTextReplacement("0")
        compose.onNodeWithText(label(R.string.goal_error)).assertExists()
        field(R.string.display_name).assertTextContains("alex")
        compose.activityRule.scenario.recreate()
        field(R.string.goal_field).assertTextContains("0")
        org.junit.Assert.assertEquals(2345, kotlinx.coroutines.runBlocking {
            com.justcal.app.data.GoalPreferences(compose.activity).settings.first().goalKcal
        })
    }
}
