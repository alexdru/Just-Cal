package com.justcal.app.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.justcal.app.R
import com.justcal.app.domain.*
import com.justcal.app.ui.theme.CalSpacing
import com.justcal.app.ui.theme.CalMotion
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayState, onEdit: (Long) -> Unit,
    onGoal: (Int) -> Unit, onRetry: () -> Unit,
    onBack: (() -> Unit)? = null, bottomBar: @Composable () -> Unit,
) {
    var showGoal by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(if (onBack == null) R.string.home else R.string.history), style = MaterialTheme.typography.headlineLarge)
                        Text(state.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { onBack?.let { IconButton(onClick = it) { CalIcon(R.drawable.ic_back, stringResource(R.string.close)) } } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().consumeWindowInsets(padding),
            contentPadding = PaddingValues(
                start = CalSpacing.page, end = CalSpacing.page,
                top = padding.calculateTopPadding() + 20.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(if (onBack == null) R.string.consumed else R.string.consumed_day), style = MaterialTheme.typography.labelLarge)
                        FlowRow(verticalArrangement = Arrangement.spacedBy(0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(nutritionText(state.totals.energyKcal, 0), style = MaterialTheme.typography.displayLarge)
                            Text(stringResource(R.string.kcal), modifier = Modifier.align(Alignment.Bottom).padding(bottom = 10.dp), style = MaterialTheme.typography.titleLarge)
                        }
                        val progress by animateFloatAsState(state.progress, CalMotion.progress(), label = "daily energy progress")
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            drawStopIndicator = {},
                        )
                        Text(
                            stringResource(if (state.remainingKcal.signum() >= 0) R.string.remaining else R.string.over_goal,
                                nutritionText(state.remainingKcal.abs(), 0)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TextButton(
                            onClick = { showGoal = true },
                            contentPadding = PaddingValues(horizontal = 0.dp),
                        ) { Text(stringResource(R.string.goal, state.goalKcal.toString())) }
                    }
                }
            }
            item { MacroSummary(state.totals, targets = state.settings) }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionTitle(stringResource(R.string.diary))
                    Text(androidx.compose.ui.res.pluralStringResource(R.plurals.entries_count, state.entries.size, state.entries.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.error) item {
                Column {
                    Text(stringResource(R.string.load_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
            if (!state.loading && !state.error && state.entries.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.empty_message), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.local_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.entries, key = { it.id }) { entry ->
                FoodRow(entry, state.portions.getValue(entry.id), onClick = { onEdit(entry.id) })
            }
        }
    }
    if (showGoal) GoalDialog(state.goalKcal, onDismiss = { showGoal = false }, onSave = { onGoal(it); showGoal = false })
}

@Composable
private fun FoodRow(entry: DiaryEntry, portion: ConsumedNutrition, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium)
                Text("${entry.eatenGramsHundredths.hundredthsText()} ${stringResource(R.string.grams)}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.portion_detail, nutritionText(portion.energyKcal, 0),
                    nutritionText(portion.proteinGrams, 1), nutritionText(portion.fatGrams, 1), nutritionText(portion.carbsGrams, 1)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CalIcon(R.drawable.ic_chevron)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun GoalDialog(initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial.toString()) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val number = value.toIntOrNull()?.takeIf { it in 1..100000 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.goal_hint))
                OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true,
                    label = { Text(stringResource(R.string.goal_field)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = submitted && number == null,
                    supportingText = { if (submitted && number == null) Text(stringResource(R.string.goal_error)) })
            }
        },
        confirmButton = { TextButton(onClick = { submitted = true; number?.let(onSave) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
