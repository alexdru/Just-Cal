package com.justcal.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.justcal.app.R
import com.justcal.app.domain.*
import com.justcal.app.ui.theme.CalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorState, editing: Boolean, onChange: (FoodField, String) -> Unit,
    onSave: () -> Unit, onDelete: () -> Unit, onBack: () -> Unit, onRetry: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(if (editing) R.string.edit_food else R.string.add_food), style = MaterialTheme.typography.titleLarge)
                        state.dayEpoch?.let {
                            val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
                            Text(java.time.LocalDate.ofEpochDay(it).format(
                                java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale)),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { CalIcon(R.drawable.ic_back, stringResource(R.string.close)) } },
                actions = {
                    if (editing && !state.missing) IconButton(onClick = { confirmDelete = true }, enabled = !state.busy && !state.loading) {
                        CalIcon(R.drawable.ic_delete, stringResource(R.string.delete_food))
                    }
                },
            )
        },
        bottomBar = {
            Surface {
                Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(horizontal = CalSpacing.page, vertical = 12.dp)) {
                    Button(onClick = { focus.clearFocus(); onSave() },
                        enabled = !state.busy && !state.loading && !state.missing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    ) {
                        Text(stringResource(if (state.busy) R.string.saving else if (editing) R.string.save_changes else R.string.save_food))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).padding(horizontal = CalSpacing.page, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.missing) Text(stringResource(R.string.missing_entry), color = MaterialTheme.colorScheme.error)
            if (state.storageError) {
                Text(stringResource(R.string.storage_error), color = MaterialTheme.colorScheme.error)
                if (editing) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            FoodInputField(FoodField.NAME, state, onChange, Modifier.fillMaxWidth())
            SectionTitle(stringResource(R.string.per_100g))
            FoodInputField(FoodField.ENERGY, state, onChange, Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 2) {
                FoodInputField(FoodField.PROTEIN, state, onChange, Modifier.weight(1f).widthIn(min = 140.dp))
                FoodInputField(FoodField.FAT, state, onChange, Modifier.weight(1f).widthIn(min = 140.dp))
                FoodInputField(FoodField.CARBS, state, onChange, Modifier.fillMaxWidth())
            }
            FoodInputField(FoodField.AMOUNT, state, onChange, Modifier.fillMaxWidth(), done = true)
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.portion_preview), style = MaterialTheme.typography.labelLarge)
                    state.preview?.let { nutrition ->
                        Text("${nutritionText(nutrition.energyKcal, 0)} ${stringResource(R.string.kcal)}",
                            style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                        MacroSummary(nutrition)
                    } ?: Text(stringResource(R.string.preview_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_title)) },
        text = { Text(stringResource(R.string.delete_message)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun FoodInputField(
    field: FoodField, state: EditorState, onChange: (FoodField, String) -> Unit,
    modifier: Modifier = Modifier, done: Boolean = false,
) {
    val focus = LocalFocusManager.current
    val label = when (field) {
        FoodField.NAME -> R.string.food_name; FoodField.ENERGY -> R.string.energy
        FoodField.PROTEIN -> R.string.protein; FoodField.FAT -> R.string.fat
        FoodField.CARBS -> R.string.carbs; FoodField.AMOUNT -> R.string.amount
    }
    val error = state.errors[field]
    val errorString = when (error) {
        InputError.REQUIRED -> R.string.error_required; InputError.NUMBER -> R.string.error_number
        InputError.PRECISION -> R.string.error_precision; InputError.NON_NEGATIVE -> R.string.error_non_negative
        InputError.POSITIVE -> R.string.error_positive; InputError.TOO_LARGE -> R.string.error_large
        null -> null
    }
    OutlinedTextField(
        value = state.draft.value(field), onValueChange = { onChange(field, it) },
        modifier = modifier, enabled = !state.busy && !state.loading && !state.missing,
        label = { Text(stringResource(label)) }, singleLine = true, isError = error != null,
        shape = MaterialTheme.shapes.medium,
        placeholder = if (field == FoodField.NAME) ({ Text(stringResource(R.string.food_hint)) }) else null,
        suffix = if (field == FoodField.NAME) null else ({
            Text(stringResource(if (field == FoodField.ENERGY) R.string.kcal else R.string.grams))
        }),
        supportingText = if (errorString != null) ({ Text(stringResource(errorString)) })
            else if (field == FoodField.AMOUNT) ({ Text(stringResource(R.string.amount_helper)) }) else null,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (field == FoodField.NAME) KeyboardType.Text else KeyboardType.Decimal,
            capitalization = if (field == FoodField.NAME) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            imeAction = if (done) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) },
            onDone = { focus.clearFocus() },
        ),
    )
}
