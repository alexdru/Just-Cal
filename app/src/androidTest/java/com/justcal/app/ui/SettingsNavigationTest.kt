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

    @Test fun changingPackageModelKeepsListStillWhileSheetAnimatesOut() {
        org.junit.Assume.assumeTrue(
            androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("realAiModels") == "true",
        )
        val context = compose.activity.applicationContext
        val preferences = com.justcal.app.data.GoalPreferences(context)
        val catalog = com.justcal.app.ai.ModelCatalog(context)
        val store = com.justcal.app.ai.LocalModelStore(context, com.justcal.app.ai.LiteRtModelInspector(), catalog)
        val model = kotlinx.coroutines.runBlocking {
            store.list().first { store.validate(it).catalogId == "gemma4-e2b" }
        }
        val previous = kotlinx.coroutines.runBlocking { preferences.aiModels.first()["package"] }
        try {
            kotlinx.coroutines.runBlocking { preferences.selectAiModel("package", null) }
            tab(R.string.settings).performClick()
            compose.onNodeWithText(label(R.string.ai_models)).performScrollTo()
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
            val task = hasText(label(R.string.ai_package_engine)) and hasClickAction() and isEnabled()
            compose.waitUntil(60_000) { compose.onAllNodes(task).fetchSemanticsNodes().isNotEmpty() }
            val defaultsTop = compose.onNodeWithText(label(R.string.ai_defaults)).fetchSemanticsNode().boundsInRoot.top
            val installedTop = compose.onNodeWithText(label(R.string.ai_installed)).fetchSemanticsNode().boundsInRoot.top
            repeat(4) { index ->
                compose.onNode(task).performClick()
                compose.onNodeWithTag("ai_model_task_sheet").assertExists()
                val choice = if (index % 2 == 0) "Gemma 4 E2B" else label(R.string.ai_builtin)
                compose.onAllNodes(hasText(choice, substring = true) and hasClickAction() and isEnabled()).onLast().assertIsEnabled()
                compose.mainClock.autoAdvance = false
                compose.onAllNodes(hasText(choice, substring = true) and hasClickAction() and isEnabled()).onLast().performClick()
                compose.mainClock.advanceTimeByFrame()
                // A choice must animate the existing sheet out rather than remove its composition.
                compose.onNodeWithTag("ai_model_task_sheet").assertExists()
                var previousTop = compose.onNodeWithTag("ai_model_task_sheet").fetchSemanticsNode().boundsInWindow.top
                repeat(6) {
                    compose.mainClock.advanceTimeByFrame()
                    val sheets = compose.onAllNodesWithTag("ai_model_task_sheet").fetchSemanticsNodes()
                    if (sheets.isNotEmpty()) {
                        val top = sheets.single().boundsInWindow.top
                        org.junit.Assert.assertTrue("Closing sheet must move down", top >= previousTop - 1f)
                        previousTop = top
                    }
                    compose.onNodeWithTag("ai_model_import_progress").assertDoesNotExist()
                    org.junit.Assert.assertEquals(defaultsTop,
                        compose.onNodeWithText(label(R.string.ai_defaults)).fetchSemanticsNode().boundsInRoot.top, 1f)
                    org.junit.Assert.assertEquals(installedTop,
                        compose.onNodeWithText(label(R.string.ai_installed)).fetchSemanticsNode().boundsInRoot.top, 1f)
                }
                compose.mainClock.autoAdvance = true
                compose.waitUntil(5_000) {
                    compose.onAllNodesWithTag("ai_model_task_sheet").fetchSemanticsNodes().isEmpty()
                }
                val expected = if (index % 2 == 0) model.id else null
                compose.waitUntil(5_000) {
                    kotlinx.coroutines.runBlocking { preferences.aiModels.first()["package"] == expected }
                }
            }
        } finally {
            compose.mainClock.autoAdvance = true
            kotlinx.coroutines.runBlocking { preferences.selectAiModel("package", previous) }
        }
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
