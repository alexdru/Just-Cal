package com.justcal.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.ui.theme.JustCalTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsChoiceGroupTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectionIsExclusiveCannotBeClearedAndRespectsDisabledState() {
        var calls = 0
        var enabled by mutableStateOf(value = true)
        compose.setContent {
            var selected by remember { mutableStateOf("System") }
            JustCalTheme {
                SettingsChoiceGroup(
                    options = listOf("System", "Light", "Dark"),
                    selected = selected,
                    enabled = enabled,
                    label = { it },
                ) {
                    selected = it
                    calls++
                }
            }
        }
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        compose.onNodeWithText("System").assert(radio).assertIsOn().performClick().assertIsOn()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("Dark").performClick().assertIsOn()
        compose.onNodeWithText("System").assertIsOff()
        compose.runOnIdle { assertEquals(1, calls); enabled = false }
        compose.onNodeWithText("Light").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Dark").assertIsOn()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun individualUnavailableChoiceDoesNotDisableTheSupportedTheme() {
        var calls = 0
        compose.setContent {
            JustCalTheme {
                SettingsChoiceGroup(options = listOf("Just Cal", "Material You"), selected = "Just Cal",
                    optionEnabled = { it != "Material You" }, label = { it }) { calls++ }
            }
        }
        compose.onNodeWithText("Just Cal").assertIsEnabled().assertIsOn()
        compose.onNodeWithText("Material You").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun narrowGroupKeepsLargeLocalizedTextReachable() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.5f)) {
                JustCalTheme {
                    Box(Modifier.width(180.dp)) {
                        SettingsChoiceGroup(
                            listOf("Как в системе", "English", "Русский"), "Как в системе",
                            label = { it },
                        ) {}
                    }
                }
            }
        }
        listOf("Как в системе", "English", "Русский").forEach {
            compose.onNodeWithText(it).assertIsDisplayed().assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
        }
    }
}
