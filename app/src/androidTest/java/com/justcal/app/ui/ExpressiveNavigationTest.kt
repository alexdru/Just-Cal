package com.justcal.app.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
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
class ExpressiveNavigationTest {
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
    private fun tab(id: Int) = compose.onNode(
        hasText(compose.activity.getString(id)) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
    )

    @Test fun swipeAndTapUseTheSamePagesAndRetainState() {
        compose.onNodeWithTag("main_pager").performTouchInput { swipeLeft() }
        tab(R.string.history).assertIsSelected()
        compose.onNodeWithTag("main_pager").performTouchInput { swipeLeft() }
        tab(R.string.settings).assertIsSelected()
        val name = compose.activity.getString(R.string.display_name)
        compose.onNode(hasText(name) and hasSetTextAction()).performTextReplacement("Expressive draft")
        // IME Back is separate from top-level Back.
        compose.activityRule.scenario.recreate()
        compose.onNode(hasText(name) and hasSetTextAction()).assertTextContains("Expressive draft")
        tab(R.string.home).performClick()
        tab(R.string.settings).performClick()
        compose.onNode(hasText(name) and hasSetTextAction()).assertTextContains("Expressive draft")
    }

    @Test fun settingsHidesFabBeforeThePagerSettlesAndGatesDynamicColors() {
        val add = compose.activity.getString(R.string.add_food)
        compose.onNodeWithContentDescription(add).assertExists()
        compose.mainClock.autoAdvance = false
        try {
            tab(R.string.settings).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
            compose.mainClock.advanceTimeBy(130)
            compose.onNodeWithContentDescription(add).assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        tab(R.string.settings).assertIsSelected()
        val dynamic = compose.onNodeWithText(compose.activity.getString(R.string.color_material_you))
        dynamic.performScrollTo()
        if (android.os.Build.VERSION.SDK_INT < 31) dynamic.assertIsNotEnabled()
        else dynamic.assertIsEnabled()
        // Lab is a normal grouped navigation row without changing the Lab itself.
        compose.onNode(hasText(compose.activity.getString(R.string.local_ai_lab)) and hasClickAction()).assertExists()
    }

    @Test fun settingsKeyboardLeavesNavigationAnchoredBehindIme() {
        tab(R.string.settings).performClick().assertIsSelected()
        val bottomBefore = tab(R.string.home).fetchSemanticsNode().boundsInRoot.bottom
        compose.onNode(hasText(compose.activity.getString(R.string.goal_field)) and hasSetTextAction())
            .performScrollTo().performClick()
        val imeType = androidx.core.view.WindowInsetsCompat.Type.ime()
        compose.waitUntil(5000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(imeType) == true
        }
        val bottom = tab(R.string.home).fetchSemanticsNode().boundsInRoot.bottom
        val ime = androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!.getInsets(imeType).bottom
        org.junit.Assert.assertEquals("The keyboard must not lift the navigation pill",
            bottomBefore, bottom, 1f)
        org.junit.Assert.assertTrue("The IME covers stationary navigation",
            bottom > compose.activity.window.decorView.height - ime)
        check(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        compose.waitUntil(5000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(imeType) != true
        }
    }

    @Test fun changingLanguageUpdatesResourcesWithoutRecreatingActivityOrSplash() {
        val oldLocale = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()
        tab(R.string.settings).performClick().assertIsSelected()
        val before = compose.activity
        try {
            val russian = before.getString(R.string.language_russian)
            compose.onNodeWithText(russian).performScrollTo().performClick()
            compose.waitUntil(5000) { compose.activity.resources.configuration.locales[0].language == "ru" }
            org.junit.Assert.assertSame(before, compose.activity)
            tab(R.string.settings).assertIsSelected()
            val english = compose.activity.getString(R.string.language_english)
            compose.onNodeWithText(english).performScrollTo().performClick()
            compose.waitUntil(5000) { compose.activity.resources.configuration.locales[0].language == "en" }
            org.junit.Assert.assertSame(before, compose.activity)
        } finally {
            compose.runOnIdle {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.forLanguageTags(oldLocale),
                )
            }
        }
    }

    @Test fun cancelledPredictiveBackCanRestartBeforeCancellationSettles() {
        tab(R.string.settings).performClick().assertIsSelected()
        val backupLabel = compose.activity.getString(R.string.data_backup)
        val backupRow = compose.onNode(hasText(backupLabel) and hasClickAction())
        // The floating pill overlaps the scroll viewport; this test targets navigation,
        // so dispatch the row action without accidentally tapping a covered root tab.
        backupRow.performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("settings_scroll").assertDoesNotExist()
        val input = object : androidx.navigationevent.NavigationEventInput() {
            fun start() = dispatchOnBackStarted(androidx.navigationevent.NavigationEvent(
                swipeEdge = androidx.navigationevent.NavigationEvent.EDGE_LEFT, progress = 0f))
            fun progress(value: Float) = dispatchOnBackProgressed(androidx.navigationevent.NavigationEvent(
                swipeEdge = androidx.navigationevent.NavigationEvent.EDGE_LEFT, progress = value))
            fun cancel() = dispatchOnBackCancelled()
            fun complete() = dispatchOnBackCompleted()
        }
        compose.runOnIdle { compose.activity.navigationEventDispatcher.addInput(input) }
        compose.mainClock.autoAdvance = false
        try {
            repeat(3) {
                compose.runOnIdle { input.start(); input.progress(0.45f) }
                compose.mainClock.advanceTimeBy(80)
                compose.runOnIdle { input.cancel() }
                compose.mainClock.advanceTimeByFrame()
            }
            compose.runOnIdle { input.start(); input.progress(0.65f) }
            compose.mainClock.advanceTimeBy(80)
            compose.runOnIdle { input.complete() }
            compose.mainClock.advanceTimeBy(1000)
            compose.mainClock.autoAdvance = true
            // Exactly one pop returns to the original retained Settings entry.
            compose.onNode(hasText(backupLabel) and hasClickAction()).assertExists()
            tab(R.string.settings).assertIsSelected()
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { compose.activity.navigationEventDispatcher.removeInput(input) }
        }
    }

    @Test fun backAtSecondaryRootReturnsHomeWithoutDiscardingSettings() {
        tab(R.string.settings).performClick()
        tab(R.string.settings).assertIsSelected()
        check(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        compose.waitUntil(5000) {
            tab(R.string.home).fetchSemanticsNode().config[SemanticsProperties.Selected]
        }
        tab(R.string.home).assertIsSelected()
    }

    @Test fun themeSelectionsApplyWithoutSaveAndSurviveRecreation() {
        tab(R.string.settings).performClick()
        val scroll = compose.onNodeWithTag("settings_scroll")
        scroll.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 1200f) }
        val light = compose.activity.getString(R.string.theme_light)
        val dark = compose.activity.getString(R.string.theme_dark)
        compose.onNodeWithText(light).performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText(dark).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText(light).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText(light).assertIsOn()
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val dynamic = compose.activity.getString(R.string.color_material_you)
            compose.onNodeWithText(dynamic).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
            compose.onNodeWithText(dynamic).assertIsOn()
            compose.waitUntil(5000) {
                kotlinx.coroutines.runBlocking {
                    com.justcal.app.data.GoalPreferences(compose.activity).settings.first().colorStyle ==
                        com.justcal.app.domain.ColorStyle.MATERIAL_YOU
                }
            }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText(dynamic).assertIsOn()
            compose.onNodeWithText(compose.activity.getString(R.string.color_just_cal))
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.save_changes)).assertDoesNotExist()
        compose.waitUntil(5000) {
            kotlinx.coroutines.runBlocking {
                com.justcal.app.data.GoalPreferences(compose.activity).settings.first().appearance ==
                    com.justcal.app.domain.Appearance.LIGHT
            }
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(light).assertIsOn()
    }
}
