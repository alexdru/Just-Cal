package com.justcal.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.ui.theme.JustCalTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpressiveComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun groupedFoodRowsKeepDistinctClickTargetsInBothThemes() {
        val nutrition = com.justcal.app.domain.NutritionPer100g(20000, 1000, 500, 2000)
        val day = java.time.LocalDate.now().toEpochDay()
        val entries = listOf(
            com.justcal.app.domain.DiaryEntry(1, "Овсяная каша", nutrition, 15000, day, 1),
            com.justcal.app.domain.DiaryEntry(2, "Greek yogurt", nutrition, 10000, day, 2),
        )
        val totals = com.justcal.app.domain.NutritionCalculator.total(entries)
        var dark by mutableStateOf(false)
        var clicked = 0L
        compose.setContent {
            JustCalTheme(darkTheme = dark) {
                TodayScreen(TodayState(entries = entries, totals = totals, loading = false,
                    remainingKcal = 2000.toBigDecimal() - totals.energyKcal, progress = 0.25f,
                    portions = entries.associate { it.id to com.justcal.app.domain.NutritionCalculator.consumed(it.per100g, it.eatenGramsHundredths) }),
                    onEdit = { clicked = it }, onGoal = {}, onRetry = {}, bottomBar = {})
            }
        }
        entries.forEach { entry ->
            compose.onNode(hasText(entry.name) and hasClickAction()).performScrollTo().performClick()
            compose.runOnIdle { org.junit.Assert.assertEquals(entry.id, clicked) }
        }
        fun snapshot(name: String) {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            java.io.File(context.externalCacheDir, name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        compose.waitForIdle()
        snapshot("justcal-grouped-food-light.png")
        compose.runOnIdle { dark = true }
        compose.waitForIdle()
        compose.onNode(hasText(entries[1].name) and hasClickAction()).assertExists()
        snapshot("justcal-grouped-food-dark.png")
    }

    @Test fun largeCalorieValueRemainsReachableAt150PercentInNarrowWindow() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.5f),
            ) {
                JustCalTheme {
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.width(320.dp),
                    ) {
                        TodayScreen(
                            TodayState(loading = false, totals = com.justcal.app.domain.ConsumedNutrition(
                                energyKcal = java.math.BigDecimal("12345678"),
                            )),
                            onEdit = {}, onGoal = {}, onRetry = {}, bottomBar = {},
                        )
                    }
                }
            }
        }
        val anchor = compose.onNodeWithTag("calorie_value")
        val range = anchor.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
        org.junit.Assert.assertTrue(range.maxValue() > 0f)
        // Drive fling frames explicitly instead of waiting for platform idle across devices.
        compose.mainClock.autoAdvance = false
        try {
            anchor.performTouchInput { swipeLeft() }
            compose.mainClock.advanceTimeBy(1000)
            compose.runOnIdle {
                org.junit.Assert.assertTrue("The full amount is reachable without reducing text size", range.value() > 0f)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun themeRevealDoesNotRecomposeColorConsumersEveryFrame() {
        var dark by mutableStateOf(false)
        val recompositions = java.util.concurrent.atomic.AtomicInteger()
        compose.setContent {
            JustCalTheme(darkTheme = dark) {
                val color = MaterialTheme.colorScheme.onSurface
                androidx.compose.runtime.SideEffect { recompositions.incrementAndGet() }
                androidx.compose.material3.Text("Theme consumer", color = color)
            }
        }
        val before = recompositions.get()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { dark = true }
            repeat(30) { compose.mainClock.advanceTimeByFrame() }
            compose.runOnIdle {
                org.junit.Assert.assertTrue("Theme colors must change once, not every animation frame",
                    recompositions.get() - before <= 3)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun radialThemeRevealKeepsOldFrameOutsideTheExpandingCircle() {
        var dark by mutableStateOf(false)
        compose.setContent {
            val colors = if (dark) androidx.compose.material3.darkColorScheme(surface = Color(0xFF101010))
                else androidx.compose.material3.lightColorScheme(surface = Color(0xFFF0F0F0))
            com.justcal.app.ui.theme.AnimatedBrandTheme(colors, false) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {}
            }
        }
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { dark = true }
            compose.mainClock.advanceTimeBy(200)
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            org.junit.Assert.assertTrue("New theme is visible at reveal center",
                pixels[pixels.width / 2, pixels.height / 2].red < 0.2f)
            org.junit.Assert.assertTrue("Old theme persists beyond reveal radius",
                pixels[pixels.width / 20, pixels.height / 20].red > 0.8f)
            compose.mainClock.advanceTimeBy(800)
            compose.runOnIdle { dark = false }
            compose.mainClock.advanceTimeBy(120)
            compose.runOnIdle { dark = true }
            compose.mainClock.advanceTimeBy(800)
            val finalPixels = compose.onRoot().captureToImage().toPixelMap()
            org.junit.Assert.assertTrue("Rapid replacement ends at newest palette",
                finalPixels[finalPixels.width / 20, finalPixels.height / 20].red < 0.2f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun themeColorChangesDoNotRestartNutritionTransitions() {
        var unitColor by mutableStateOf(Color.Gray)
        compose.setContent {
            JustCalTheme {
                AnimatedNutrition(
                    buildAnnotatedString {
                        append("120")
                        withStyle(SpanStyle(color = unitColor)) { append(" kcal") }
                    },
                    MaterialTheme.typography.titleLarge,
                )
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { unitColor = Color.Red }
        compose.mainClock.advanceTimeByFrame()
        // A style-only change must update in place, without an outgoing copy of the same amount.
        compose.onAllNodesWithText("120 kcal").assertCountEquals(1)
    }
}
