package com.justcal.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.domain.*
import com.justcal.app.ui.theme.JustCalTheme
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntryActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelBothActionsLeavesEditorDraftAndNeverSubmits() {
        var copies = 0
        var moves = 0
        compose.setContent {
            JustCalTheme {
                EditorScreen(EditorState(draft = FoodDraft(name = "Unsaved food"),
                    dayEpoch = LocalDate.of(2026, 10, 19).toEpochDay()), true,
                    onChange = { _, _ -> }, onSave = {}, onDelete = {}, onBack = {}, onRetry = {},
                    onDuplicate = { copies++ }, onChangeDate = { moves++ })
            }
        }
        listOf("Duplicate", "Change date").forEach { action ->
            compose.onNodeWithContentDescription("Entry actions").performClick()
            compose.onNodeWithText(action).performClick()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("Unsaved food").assertIsDisplayed()
        }
        compose.runOnIdle { assertEquals(0, copies); assertEquals(0, moves) }
    }

    @Test fun duplicateDefaultsToTodayAndConfirmsOnlyOnce() {
        var copies = 0
        var result: Long? = null
        val today = LocalDate.now().toEpochDay()
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            JustCalTheme {
                if (open) EntryDateDialog(today, true, { open = false }) {
                    copies++; result = it; open = false
                }
            }
        }
        compose.onNodeWithText("Duplicate").performClick()
        compose.runOnIdle { assertEquals(1, copies); assertEquals(today, result) }
        compose.onNodeWithText("Duplicate").assertDoesNotExist()
    }

    @Test fun calendarAllowsFutureDay() = selectDate("10202026", LocalDate.of(2026, 10, 20))
    @Test fun calendarAllowsPastDay() = selectDate("10182026", LocalDate.of(2026, 10, 18))

    private fun selectDate(input: String, expected: LocalDate) {
        var result: Long? = null
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            JustCalTheme {
                if (open) EntryDateDialog(LocalDate.of(2026, 10, 19).toEpochDay(), false, { open = false }) {
                    result = it; open = false
                }
            }
        }
        compose.onNodeWithContentDescription("Switch to text input mode").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement(input)
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertEquals(expected.toEpochDay(), result) }
    }
}
