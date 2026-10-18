package com.justcal.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.justcal.app.ui.theme.CalSpacing
import com.justcal.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Material calendars encode a calendar date at UTC midnight, never device midnight. */
internal fun pickerMillis(day: Long): Long =
    LocalDate.ofEpochDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun pickerDay(millis: Long): Long =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

@Composable
internal fun EntryDateDialog(
    initialDay: Long, duplicate: Boolean, onDismiss: () -> Unit, onConfirm: (Long) -> Unit,
) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = pickerMillis(initialDay),
        yearRange = 1..9999,
    )
    // Close synchronously before dispatch, including when two confirmation clicks arrive in one frame.
    var submitted by remember { mutableStateOf(false) }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = !submitted && picker.selectedDateMillis != null, onClick = {
                if (!submitted) picker.selectedDateMillis?.let {
                    submitted = true
                    onConfirm(pickerDay(it))
                }
            }) { Text(stringResource(if (duplicate) R.string.duplicate_entry else R.string.done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = picker, title = {
            Text(stringResource(if (duplicate) R.string.duplicate_date_title else R.string.change_entry_date),
                modifier = Modifier.padding(
                    start = CalSpacing.page, top = CalSpacing.medium,
                ),
            )
        })
    }
}
