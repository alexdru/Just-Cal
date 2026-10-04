package com.justcal.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.MainActivity
import com.justcal.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun label(id: Int) = compose.activity.getString(id)
    private fun tab(id: Int) = compose.onNode(hasText(label(id)) and hasClickAction())
    private fun field(id: Int) = compose.onNode(hasText(label(id)) and hasSetTextAction())

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

        // Invalid settings must remain a draft and must not write test values to DataStore.
        field(R.string.goal_field).performTextReplacement("0")
        compose.onNodeWithText(label(R.string.save_changes)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.goal_error)).assertExists()
        field(R.string.display_name).assertTextContains("alex")
    }
}
