package com.justcal.app.ui

import androidx.compose.ui.test.*
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
    private fun awaitNode(matcher: SemanticsMatcher) {
        compose.waitUntil(5000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun hiddenProfileSurvivesGoalsTabsAndActivityRecreation() {
        val preferences = com.justcal.app.data.GoalPreferences(compose.activity)
        kotlinx.coroutines.runBlocking { preferences.save(original.copy(displayName = "Retained profile")) }
        tab(R.string.settings).performClick().assertIsSelected()
        compose.onAllNodes(hasText(label(R.string.display_name)) and hasSetTextAction()).assertCountEquals(0)
        field(R.string.goal_field).performTextReplacement("2345")
        compose.onNodeWithText(label(R.string.macro_goals)).performScrollTo().performClick()
        field(R.string.fat).performScrollTo().performTextReplacement("65,25")
        compose.waitUntil(5000) {
            kotlinx.coroutines.runBlocking {
                val saved = preferences.settings.first()
                saved.goalKcal == 2345 && saved.fatGoalGramsHundredths == 6525L
            }
        }
        org.junit.Assert.assertEquals("Retained profile", kotlinx.coroutines.runBlocking { preferences.settings.first().displayName })
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        field(R.string.goal_field).performScrollTo()
        tab(R.string.home).performClick().assertIsSelected()
        tab(R.string.settings).performClick().assertIsSelected()
        compose.activityRule.scenario.recreate()
        awaitNode(hasText(label(R.string.goal_field)) and hasSetTextAction())
        field(R.string.goal_field).performScrollTo().assertTextContains("2345")
        compose.onAllNodes(hasText(label(R.string.display_name)) and hasSetTextAction()).assertCountEquals(0)
        org.junit.Assert.assertEquals("Retained profile", kotlinx.coroutines.runBlocking { preferences.settings.first().displayName })
        field(R.string.goal_field).performTextReplacement("0")
        compose.onNodeWithText(label(R.string.goal_error)).assertExists()
        compose.activityRule.scenario.recreate()
        awaitNode(hasText(label(R.string.goal_field)) and hasSetTextAction())
        field(R.string.goal_field).performScrollTo().assertTextContains("0")
        org.junit.Assert.assertEquals(2345, kotlinx.coroutines.runBlocking { preferences.settings.first().goalKcal })
    }

    @Test fun aiTaskSheetAndPlaygroundPromptRemainUsableWithKeyboard() {
        tab(R.string.settings).performClick()
        compose.onNodeWithText(label(R.string.ai_models)).performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(60_000) {
            compose.onAllNodes(hasText(label(R.string.ai_package_engine)) and hasClickAction() and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(label(R.string.ai_package_engine)).performClick()
        compose.onNodeWithText(label(R.string.ai_task_hint)).assertExists()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription(label(R.string.close)).performClick()
        compose.onNodeWithText(label(R.string.local_ai_lab)).performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(60_000) {
            compose.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        val prompt = compose.onNode(hasSetTextAction())
        prompt.performScrollTo().performClick().performTextReplacement("Read only the visible package label.")
        compose.waitUntil(5000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        compose.waitForIdle()
        val keyboardBottom = compose.activity.window.decorView.height -
            (androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0)
        org.junit.Assert.assertTrue("Prompt must remain above the keyboard",
            prompt.fetchSemanticsNode().boundsInWindow.bottom <= keyboardBottom + 1)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithContentDescription(label(R.string.close)).performClick()
    }

    @Test fun aiModelsAndPlaygroundAreChildSettingsDestinations() {
        tab(R.string.settings).performClick().assertIsSelected()
        compose.onNodeWithText(label(R.string.ai_models)).performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        awaitNode(hasText(label(R.string.lab_import_model)))
        compose.onNodeWithText(label(R.string.lab_import_model)).assertExists()
        compose.onNodeWithContentDescription(label(R.string.close)).performClick()
        compose.onNodeWithText(label(R.string.local_ai_lab)).performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        awaitNode(hasText(label(R.string.lab_model)))
        compose.onNodeWithText(label(R.string.lab_model)).assertExists()
        compose.onNodeWithContentDescription(label(R.string.close)).performClick()
        compose.onNode(hasText(label(R.string.settings)) and hasClickAction()).assertExists()
    }
}
