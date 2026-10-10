package com.justcal.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.justcal.app.R
import com.justcal.app.ui.theme.CalSpacing
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryScreen(state: HistoryState, onDay: (Long) -> Unit, onRetry: () -> Unit,
    bottomBar: @Composable () -> Unit) {
    var calendar by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    val formatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    Scaffold(
        topBar = { CalTopBar(title = { Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineLarge) }) },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(Modifier.padding(start = padding.calculateStartPadding(direction), end = padding.calculateEndPadding(direction))
                .calContent().consumeWindowInsets(padding),
            contentPadding = PaddingValues(CalSpacing.page, padding.calculateTopPadding() + 16.dp,
                CalSpacing.page, padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { calendar = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        CalIcon(R.drawable.ic_diary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.choose_day))
                    }
                }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.error) item {
                Text(stringResource(R.string.load_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            if (!state.loading && !state.error && state.days.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(stringResource(R.string.history_empty))
                    Text(stringResource(R.string.history_empty_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.days, key = { it.dayEpoch }) { day ->
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = { onDay(day.dayEpoch) })
                    .padding(vertical = CalSpacing.related), verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val date = LocalDate.ofEpochDay(day.dayEpoch)
                            Text(if (date == LocalDate.now()) stringResource(R.string.today) else date.format(formatter),
                                style = MaterialTheme.typography.titleMedium)
                            Text(pluralStringResource(R.plurals.entries_count, day.entriesCount, day.entriesCount),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        EnergyValue(day.totals.energyKcal)
                    }
                    MacroDetail(day.totals)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    if (calendar) {
        val today = LocalDate.now()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(onDismissRequest = { calendar = false },
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let {
                    calendar = false
                    onDay(java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
                }
            }) { Text(stringResource(R.string.open_day)) } },
            dismissButton = { TextButton(onClick = { calendar = false }) { Text(stringResource(R.string.cancel)) } }) {
            DatePicker(state = picker)
        }
    }
}
