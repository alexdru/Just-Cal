package com.justcal.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.height
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
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
import com.justcal.app.ui.theme.ThemeRevealControls
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

    @Test fun themeCaptureWaitsForControlOutlineToSettle() {
        var dark by mutableStateOf(false)
        val settled = kotlinx.coroutines.CompletableDeferred<Unit>()
        compose.setContent {
            val colors = androidx.compose.material3.lightColorScheme(
                surface = if (dark) Color.Black else Color.White,
            )
            com.justcal.app.ui.theme.AnimatedBrandTheme(colors, false) {
                val origin = com.justcal.app.ui.theme.LocalThemeRevealOrigin.current
                androidx.compose.runtime.SideEffect {
                    origin.awaitControlMotion = { settled.await() }
                }
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {}
            }
        }
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { dark = true }
            compose.mainClock.advanceTimeBy(200)
            val waiting = compose.onRoot().captureToImage().toPixelMap()
            org.junit.Assert.assertTrue("Capture must wait while the control is still moving",
                waiting[waiting.width / 2, waiting.height / 2].red > 0.95f)
            compose.runOnIdle { settled.complete(Unit) }
            compose.mainClock.advanceTimeBy(1000)
            val finished = compose.onRoot().captureToImage().toPixelMap()
            org.junit.Assert.assertTrue("The reveal must complete after the control settles",
                finished[finished.width / 2, finished.height / 2].red < 0.05f)
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

    @Test fun paletteTouchUsesTheSameRadialRevealWithoutChangingBrightness() {
        var alternate by mutableStateOf(false)
        compose.setContent {
            val colors = androidx.compose.material3.lightColorScheme(
                surface = if (alternate) Color(0xFFD0D0F0) else Color(0xFFF0D0D0),
            )
            com.justcal.app.ui.theme.AnimatedBrandTheme(colors, false, selectionKey = alternate) {
                SettingsChoiceGroup(listOf(false, true), alternate, themeSelection = true,
                    label = { if (it) "Material You" else "Just Cal" }) { alternate = it }
            }
        }
        compose.mainClock.autoAdvance = false
        try {
            compose.onNode(hasText("Material You")).performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(80)
            compose.onNode(hasText("Material You")).performTouchInput { up() }
            compose.mainClock.advanceTimeBy(200)
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val near = pixels[pixels.width / 2, pixels.height / 10]
            val far = pixels[pixels.width / 20, pixels.height * 19 / 20]
            org.junit.Assert.assertTrue("New palette is visible near its control", near.blue > near.red + 0.05f)
            org.junit.Assert.assertTrue("Old palette persists outside the circle", far.red > far.blue + 0.05f)
            compose.mainClock.advanceTimeBy(800)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun appearancePanelColorFollowsTheSameRevealCircleAsTheScreen() {
        var alternate by mutableStateOf(false)
        compose.setContent {
            val colors = androidx.compose.material3.lightColorScheme(
                surfaceContainer = if (alternate) Color(0xFFBBBBEE) else Color(0xFFEEBBBB),
            )
            com.justcal.app.ui.theme.AnimatedBrandTheme(colors, false, selectionKey = alternate) {
                ThemeRevealControls { snapshot ->
                    CalGroup(androidx.compose.ui.Modifier.testTag("appearance_panel")) {
                        SettingsChoiceGroup(listOf(false, true), alternate, themeSelection = true,
                            interactive = !snapshot, label = { if (it) "New" else "Old" }) { alternate = it }
                        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(480.dp))
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        try {
            compose.onNode(hasText("New")).performTouchInput { click(center) }
            compose.mainClock.advanceTimeBy(120)
            val panel = compose.onNodeWithTag("appearance_panel").fetchSemanticsNode().boundsInRoot
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val far = pixels[panel.center.x.toInt(), (panel.bottom - 40).toInt()]
            org.junit.Assert.assertTrue("Appearance background outside the circle must keep its old color",
                far.red > far.blue + 0.1f)
            compose.mainClock.advanceTimeBy(800)
            val finalPixels = compose.onRoot().captureToImage().toPixelMap()
            val final = finalPixels[panel.center.x.toInt(), (panel.bottom - 40).toInt()]
            org.junit.Assert.assertTrue("Appearance background must reach the new palette at completion",
                final.blue > final.red + 0.1f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun themeRevealPreservesNativePixelDetailOutsideCircle() {
        var alternate by mutableStateOf(false)
        compose.setContent {
            val colors = androidx.compose.material3.lightColorScheme(
                surface = if (alternate) Color(0xFF101010) else Color(0xFFF0F0F0),
                onSurface = if (alternate) Color.Green else Color.Red,
            )
            com.justcal.app.ui.theme.AnimatedBrandTheme(colors, false) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.fillMaxSize()) {
                        // Physical one-pixel strokes expose downsampling on dense displays.
                        repeat(160) { x ->
                            drawRect(
                                color = if (x % 2 == 0) Color.Black else Color.White,
                                topLeft = androidx.compose.ui.geometry.Offset(20f + x, size.height - 100f),
                                size = androidx.compose.ui.geometry.Size(1f, 40f),
                            )
                        }
                    }
                    androidx.compose.material3.Text(
                        "Чёткий текст 123",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomEnd)
                            .padding(20.dp).testTag("detail_probe"),
                    )
                }
            }
        }
        val before = compose.onRoot().captureToImage().toPixelMap()
        val textBounds = compose.onNodeWithTag("detail_probe").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { alternate = true }
            compose.mainClock.advanceTimeBy(120)
            val during = compose.onRoot().captureToImage().toPixelMap()
            var largestDifference = 0f
            for (y in (before.height - 95)..(before.height - 65)) {
                for (x in 20 until 180) {
                    largestDifference = maxOf(largestDifference,
                        kotlin.math.abs(before[x, y].red - during[x, y].red))
                }
            }
            for (y in textBounds.top.toInt() until textBounds.bottom.toInt()) {
                for (x in textBounds.left.toInt() until textBounds.right.toInt()) {
                    largestDifference = maxOf(largestDifference,
                        kotlin.math.abs(before[x, y].red - during[x, y].red),
                        kotlin.math.abs(before[x, y].green - during[x, y].green))
                }
            }
            org.junit.Assert.assertTrue(
                "Unrevealed content must keep native pixel detail and old text colors: $largestDifference",
                largestDifference < 0.03f,
            )
            compose.mainClock.advanceTimeBy(800)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun appearancePanelContourDoesNotChangeOutsideRevealCircle() {
        var alternate by mutableStateOf(false)
        compose.setContent {
            val colors = androidx.compose.material3.lightColorScheme(
                surface = if (alternate) Color.White else Color.Black,
                surfaceContainer = if (alternate) Color.Black else Color.White,
            )
            com.justcal.app.ui.theme.AnimatedBrandTheme(colors, false, selectionKey = alternate) {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 40.dp),
                ) {
                    ThemeRevealControls { snapshot ->
                        CalGroup(androidx.compose.ui.Modifier.testTag("appearance_panel")) {
                            SettingsChoiceGroup(listOf(false, true), alternate, themeSelection = true,
                                interactive = !snapshot, label = { if (it) "New" else "Old" }) { alternate = it }
                            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(400.dp))
                        }
                    }
                }
            }
        }
        val panel = compose.onNodeWithTag("appearance_panel").fetchSemanticsNode().boundsInRoot
        val before = compose.onRoot().captureToImage().toPixelMap()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNode(hasText("New")).performTouchInput { click(center) }
            compose.mainClock.advanceTimeBy(120)
            val during = compose.onRoot().captureToImage().toPixelMap()
            var largestDifference = 0f
            // Include both rounded bottom corners and their antialiased contour.
            // These pixels are far from the tapped control and must remain unchanged.
            for (y in (panel.bottom.toInt() - 40)..(panel.bottom.toInt() + 2)) {
                for (x in (panel.left.toInt() - 2)..(panel.right.toInt() + 2)) {
                    largestDifference = maxOf(largestDifference,
                        kotlin.math.abs(before[x, y].red - during[x, y].red))
                }
            }
            org.junit.Assert.assertTrue(
                "The old panel contour must not acquire a transient outline: $largestDifference",
                largestDifference < 0.04f,
            )
            compose.mainClock.advanceTimeBy(800)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun rapidPaletteChangesPreserveAppearanceColorCoverageAndSingleActions() {
        val choices = listOf("Brand", "Dark", "Dynamic")
        val colors = listOf(Color(0xFFEEBBBB), Color(0xFFBBBBEE), Color(0xFFBBEEBB))
        var selected by mutableStateOf(0)
        var actions = 0
        compose.setContent {
            val palette = androidx.compose.material3.lightColorScheme(
                surface = colors[selected], surfaceContainer = colors[selected],
            )
            com.justcal.app.ui.theme.AnimatedBrandTheme(palette, false, selectionKey = selected) {
                ThemeRevealControls { snapshot ->
                    CalGroup(androidx.compose.ui.Modifier.testTag("appearance_panel")) {
                        SettingsChoiceGroup(choices.indices.toList(), selected, themeSelection = true,
                            interactive = !snapshot, label = { choices[it] }) { selected = it; actions++ }
                        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(480.dp))
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        try {
            listOf(1, 2, 1).forEach { next ->
                compose.onAllNodesWithText(choices[next]).assertCountEquals(1)
                compose.onNode(hasText(choices[next])).performTouchInput { click(center) }
                compose.mainClock.advanceTimeBy(120)
                compose.onNode(hasText(choices[next])).assertIsOn()
                val panel = compose.onNodeWithTag("appearance_panel").fetchSemanticsNode().boundsInRoot
                val pixels = compose.onRoot().captureToImage().toPixelMap()
                val far = pixels[panel.center.x.toInt(), (panel.bottom - 40).toInt()]
                org.junit.Assert.assertTrue("Interrupted reveals must retain their color coverage",
                    far.red > far.blue + 0.1f && far.red > far.green + 0.1f)
            }
            compose.mainClock.advanceTimeBy(800)
            compose.runOnIdle { org.junit.Assert.assertEquals(3, actions) }
            val panel = compose.onNodeWithTag("appearance_panel").fetchSemanticsNode().boundsInRoot
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val final = pixels[panel.center.x.toInt(), (panel.bottom - 40).toInt()]
            org.junit.Assert.assertTrue("Newest palette wins", final.blue > final.red + 0.1f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun brightnessSelectionNeverRollsBackDuringThemeReveal() =
        assertLiveThemeSelection(listOf("Light", "Dark"))

    @Test fun paletteSelectionNeverRollsBackDuringThemeReveal() =
        assertLiveThemeSelection(listOf("Just Cal", "Material You"))

    private fun assertLiveThemeSelection(choices: List<String>) {
        var selected by mutableStateOf(choices.first())
        compose.setContent {
            val colors = androidx.compose.material3.lightColorScheme(
                primary = Color.Blue,
                surface = if (selected == choices.first()) Color(0xFFF0D0D0) else Color(0xFFD0D0F0),
            )
            com.justcal.app.ui.theme.AnimatedBrandTheme(colors, false, selectionKey = selected) {
                ThemeRevealControls { snapshot ->
                    CalGroup {
                        SettingsChoiceGroup(choices, selected, themeSelection = true,
                            interactive = !snapshot, label = { it }) { selected = it }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        try {
            fun assertVisibleSelection(expected: String) {
                compose.onNode(hasText(expected)).assertIsOn()
                val pixels = compose.onRoot().captureToImage().toPixelMap()
                choices.forEach { label ->
                    val bounds = compose.onNode(hasText(label)).fetchSemanticsNode().boundsInRoot
                    // Top-center avoids label glyphs and rounded corner anti-aliasing.
                    val pixel = pixels[bounds.center.x.toInt(), (bounds.top + bounds.height / 6).toInt()]
                    val blue = pixel.blue > 0.8f && pixel.red < 0.2f
                    org.junit.Assert.assertEquals("Visible selection must match $expected for $label", label == expected, blue)
                }
            }
            choices.reversed().forEach { choice ->
                compose.onNode(hasText(choice)).performTouchInput { click(center) }
                repeat(4) {
                    compose.mainClock.advanceTimeBy(16)
                    assertVisibleSelection(choice)
                }
            }
            compose.mainClock.advanceTimeBy(800)
            assertVisibleSelection(choices.first())
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
