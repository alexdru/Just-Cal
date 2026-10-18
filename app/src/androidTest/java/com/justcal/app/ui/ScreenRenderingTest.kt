package com.justcal.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.domain.DiaryEntry
import com.justcal.app.domain.NutritionCalculator
import com.justcal.app.domain.NutritionPer100g
import com.justcal.app.ui.theme.AnimatedBrandTheme
import com.justcal.app.ui.theme.JustCalTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun populatedHomeKeepsOpaqueHeaderWhileSummaryAndEntriesScrollBehindIt() {
        val nutrition = NutritionPer100g(20000, 1000, 500, 2000)
        val entries = (1L..12L).map { id ->
            DiaryEntry(id, "Запись $id — обед с овощами и гарниром", nutrition, 15000,
                java.time.LocalDate.now().toEpochDay(), id)
        }
        val totals = NutritionCalculator.total(entries)
        var clicked = 0L
        compose.setContent {
            JustCalTheme {
                TodayScreen(
                    TodayState(entries = entries, totals = totals, loading = false,
                        remainingKcal = 2000.toBigDecimal() - totals.energyKcal,
                        progress = (totals.energyKcal.toFloat() / 2000f).coerceIn(0f, 1f),
                        portions = entries.associate { it.id to NutritionCalculator.consumed(it.per100g, it.eatenGramsHundredths) }),
                    onEdit = { clicked = it }, onGoal = {}, onRetry = {}, bottomBar = {},
                )
            }
        }
        val header = compose.onNodeWithTag("cal_top_bar").fetchSemanticsNode().boundsInRoot
        val before = compose.onRoot().captureToImage().toPixelMap()
        fun assertHeaderUnchanged() {
            val after = compose.onRoot().captureToImage().toPixelMap()
            var difference = 0f
            for (y in header.top.toInt() until header.bottom.toInt()) {
                for (x in header.left.toInt() until header.right.toInt()) {
                    difference = maxOf(difference,
                        kotlin.math.abs(before[x, y].red - after[x, y].red),
                        kotlin.math.abs(before[x, y].green - after[x, y].green))
                }
            }
            assertTrue("Scrolling content must never bleed through the app bar: $difference", difference < 0.03f)
        }
        // Put the colored summary behind the app bar, rather than testing an empty diary.
        compose.onNodeWithTag("today_list").performScrollToIndex(1)
        compose.onNodeWithTag("today_list").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 80f) }
        assertHeaderUnchanged()
        saveScreenshot("populated-home-summary-scroll.png")
        compose.onNodeWithTag("today_list").performScrollToNode(hasText(entries.last().name))
        compose.onNodeWithText(entries.last().name).performClick()
        compose.runOnIdle { assertEquals(entries.last().id, clicked) }
        assertHeaderUnchanged()
        saveScreenshot("populated-home-entry-scroll.png")
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.externalCacheDir, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun navigationShadowRemainsVisibleOutsideThemeRevealCircle() {
        var alternate by mutableStateOf(false)
        compose.setContent {
            val colors = lightColorScheme(
                surface = Color.White, surfaceContainerHigh = Color(0xFFDDDDDD),
                primary = if (alternate) Color.Red else Color.Blue,
            )
            AnimatedBrandTheme(colors, false) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        FloatingNavigation(MainTab.SETTINGS, {}, null)
                    }
                }
            }
        }
        val pill = compose.onNodeWithTag("floating_navigation_pill").fetchSemanticsNode().boundsInRoot
        val before = compose.onRoot().captureToImage().toPixelMap()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { alternate = true }
            compose.mainClock.advanceTimeBy(120)
            val during = compose.onRoot().captureToImage().toPixelMap()
            var hasShadow = false
            var difference = 0f
            for (y in pill.bottom.toInt() + 1..pill.bottom.toInt() + 8) {
                for (x in pill.center.x.toInt() - 20..pill.center.x.toInt() + 20) {
                    hasShadow = hasShadow || before[x, y].red < 0.99f
                    difference = maxOf(difference, kotlin.math.abs(before[x, y].red - during[x, y].red))
                }
            }
            assertTrue("The fixture must have a visible navigation shadow", hasShadow)
            assertTrue("The unrevealed shadow must not disappear: $difference", difference < 0.025f)
            compose.mainClock.advanceTimeBy(800)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun inactiveChoicesHaveVisibleConnectedInnerCorners() {
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                surfaceContainer = Color.White, surfaceContainerHighest = Color.Gray,
            )) {
                Box(Modifier.fillMaxWidth()) {
                    SettingsChoiceGroup(listOf("System", "Light", "Dark"), "Dark", label = { it }) {}
                }
            }
        }
        // The inner edge of an unchecked leading segment is nearly square, not capsule-clipped.
        val pixels = compose.onNodeWithText("System").captureToImage().toPixelMap()
        val inset = pixels.height / 12
        assertTrue("Inactive connected segments must remain visibly filled",
            pixels[pixels.width - inset, inset].red < 0.85f)
    }
}
