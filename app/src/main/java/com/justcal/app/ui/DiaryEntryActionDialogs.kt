package com.justcal.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.justcal.app.R

@Composable
internal fun DiaryEntryActionDialogs(
    state: DiaryEntryActionState, onConfirm: (Long?) -> Unit, onDismiss: () -> Unit,
    acknowledgeResult: () -> Unit, onMessage: (String) -> Unit,
) {
    val message = state.result?.let {
        stringResource(when (it) {
            EntryAction.DUPLICATE -> R.string.entry_duplicated
            EntryAction.CHANGE_DATE -> R.string.entry_date_changed
            EntryAction.DELETE -> R.string.food_deleted
        })
    }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.result) {
        if (message != null) {
            acknowledgeResult()
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onMessage(message)
        }
    }
    if (state.busy || state.action == null) return
    if (state.error) {
        AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(stringResource(R.string.entry_action_error)) },
            confirmButton = { TextButton(onClick = { onConfirm(state.dayEpoch) }) { Text(stringResource(R.string.retry)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    } else if (state.action == EntryAction.DELETE) {
        EntryDeleteDialog(onDismiss, onConfirm = { onConfirm(state.dayEpoch) })
    } else {
        EntryDateDialog(
            initialDay = requireNotNull(state.dayEpoch),
            duplicate = state.action == EntryAction.DUPLICATE,
            onDismiss = onDismiss,
            onConfirm = { onConfirm(it) },
        )
    }
}
