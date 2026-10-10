package com.justcal.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.domain.*
import com.justcal.app.ui.theme.JustCalTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises input progression and error recovery without touching the user's diary. */
@RunWith(AndroidJUnit4::class)
class EditorInteractionTest {
    @get:Rule val compose = createComposeRule()
    private fun field(label: String) = compose.onNode(hasText(label) and hasSetTextAction())

    private fun editor(largeText: Boolean = false) {
        compose.setContent {
            var state by remember { mutableStateOf(EditorState()) }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 2f else 1f)) {
                JustCalTheme {
                    Box(Modifier.width(320.dp)) {
                        EditorScreen(state, false, onChange = { key, value ->
                            state = state.copy(draft = state.draft.with(key, value), errors = state.errors - key)
                            val food = FoodValidator.validate(state.draft).food
                            state = state.copy(preview = food?.let { NutritionCalculator.consumed(it.per100g, it.eatenGramsHundredths) })
                        }, onSave = { state = state.copy(errors = FoodValidator.validate(state.draft).errors) },
                            onDelete = {}, onBack = {}, onRetry = {})
                    }
                }
            }
        }
    }

    @Test fun imeOrderAndSubmitReturnToFirstInvalidField() {
        editor()
        compose.onNodeWithText("Add to diary").performClick()
        field("Food name").assertIsFocused().performTextReplacement("Yogurt")
        field("Food name").performImeAction()
        field("Calories").assertIsFocused().performTextReplacement("110")
        field("Calories").performImeAction()
        field("Protein").assertIsFocused().performTextReplacement("8")
        field("Protein").performImeAction()
        field("Fat").assertIsFocused().performTextReplacement("3")
        field("Fat").performImeAction()
        field("Carbs").assertIsFocused().performTextReplacement("13")
        field("Carbs").performImeAction()
        field("Amount eaten").assertIsFocused().performTextReplacement("0")
        field("Amount eaten").performImeAction()
        compose.onNodeWithText("Add to diary").performClick()
        field("Amount eaten").assertIsFocused()
        compose.onNodeWithText("Must be greater than zero").assertIsDisplayed()
        field("Amount eaten").performTextReplacement("180")
        field("Amount eaten").performImeAction()
        compose.onNodeWithText("198 kcal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add to diary").assertIsDisplayed()
    }

    @Test fun largeTextKeepsLastFieldAndConfirmationReachable() {
        editor(largeText = true)
        field("Amount eaten").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        field("Amount eaten").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithText("Add to diary").assertIsDisplayed()
        field("Amount eaten").performImeAction()
        compose.onNodeWithText("Your portion").performScrollTo().assertIsDisplayed()
    }
}
