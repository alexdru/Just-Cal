package com.justcal.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.justcal.app.R
import com.justcal.app.domain.*
import com.justcal.app.ui.theme.CalSpacing
import com.justcal.app.ui.theme.CalLayout

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    state: EditorState, editing: Boolean, onChange: (FoodField, String) -> Unit,
    onSave: () -> Unit, onDelete: () -> Unit, onBack: () -> Unit, onRetry: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val fieldFocus = remember { FoodField.entries.associateWith { FocusRequester() } }
    var validateFocus by remember { mutableStateOf(false) }
    LaunchedEffect(validateFocus, state.errors) {
        if (validateFocus) {
            FoodField.entries.firstOrNull { it in state.errors }?.let { fieldFocus.getValue(it).requestFocus() }
            validateFocus = false
        }
    }
    val direction = LocalLayoutDirection.current
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints {
        // Short windows cannot fit the app bar, enlarged field and pinned action above the IME.
        // IME Done / Back restores the chrome; keep the editing viewport unobstructed meanwhile.
        val compactIme = imeBottom > 0.dp && (maxHeight - imeBottom < 280.dp)
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (!compactIme) TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(if (editing) R.string.edit_food else R.string.add_food), style = MaterialTheme.typography.titleLarge)
                            state.dayEpoch?.let {
                                val locale = LocalConfiguration.current.locales[0]
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
                if (!compactIme) Box(
                    Modifier.fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.navigationBars.union(WindowInsets.ime)
                                .union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                        )
                        .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = CalLayout.readingWidth).fillMaxWidth()
                        .padding(horizontal = CalSpacing.page, vertical = CalSpacing.related),
                ) {
                    Button(
                        onClick = { focus.clearFocus(); validateFocus = true; onSave() },
                        enabled = !state.busy && !state.loading && !state.missing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    ) {
                        Text(stringResource(if (state.busy) R.string.saving else if (editing) R.string.save_changes else R.string.save_food))
                    }
                }
            },
        ) { padding ->
            val buttonSpace = (padding.calculateBottomPadding() - imeBottom).coerceAtLeast(0.dp) + 16.dp
            val buttonSpacePx = with(LocalDensity.current) { buttonSpace.toPx() }
            val defaultBringIntoView = LocalBringIntoViewSpec.current
            val bringIntoView = remember(defaultBringIntoView, buttonSpacePx) {
                object : BringIntoViewSpec {
                    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                        defaultBringIntoView.calculateScrollDistance(
                            offset, size, (containerSize - buttonSpacePx).coerceAtLeast(0f),
                        )
                }
            }
            // Focus scrolling clears the action overlay without clipping content behind it.
            CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
                Column(
                    // Keep the viewport behind the floating button; reserve its height at the scroll end.
                    Modifier.fillMaxSize().padding(
                        start = padding.calculateStartPadding(direction), end = padding.calculateEndPadding(direction),
                        top = padding.calculateTopPadding(), bottom = imeBottom,
                    ).consumeWindowInsets(padding).calContent(CalLayout.readingWidth)
                        .verticalScroll(rememberScrollState()).padding(horizontal = CalSpacing.page)
                        .padding(top = 16.dp, bottom = buttonSpace),
                    verticalArrangement = Arrangement.spacedBy(CalSpacing.section),
                ) {
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.missing) Text(stringResource(R.string.missing_entry), color = MaterialTheme.colorScheme.error)
                    if (state.storageError) {
                        Text(stringResource(R.string.storage_error), color = MaterialTheme.colorScheme.error)
                        if (editing) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                    val input: @Composable (FoodField, Modifier) -> Unit = { field, modifier ->
                        FoodInputField(field, state, onChange, modifier.focusRequester(fieldFocus.getValue(field)),
                            done = field == FoodField.AMOUNT,
                            onNext = { FoodField.entries.getOrNull(field.ordinal + 1)?.let { fieldFocus.getValue(it).requestFocus() } })
                    }
                    input(FoodField.NAME, Modifier.fillMaxWidth())
                    Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                        SectionTitle(stringResource(R.string.per_100g))
                        BoxWithConstraints {
                            val twoColumns = maxWidth >= (320.dp * LocalConfiguration.current.fontScale + CalSpacing.related)
                            Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                                listOf(FoodField.ENERGY to FoodField.PROTEIN, FoodField.FAT to FoodField.CARBS).forEach { (first, second) ->
                                    if (twoColumns) Row(horizontalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                                        input(first, Modifier.weight(1f))
                                        input(second, Modifier.weight(1f))
                                    } else {
                                        input(first, Modifier.fillMaxWidth())
                                        input(second, Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                        input(FoodField.AMOUNT, Modifier.fillMaxWidth())
                        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                            Column(Modifier.fillMaxWidth().padding(CalSpacing.dense), verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                                Text(stringResource(R.string.portion_preview), style = MaterialTheme.typography.labelLarge)
                                state.preview?.let { nutrition ->
                                    Text(stringResource(R.string.nutrition_with_unit, nutritionText(nutrition.energyKcal, 0), stringResource(R.string.kcal)),
                                        style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.primary)
                                    MacroDetail(nutrition)
                                } ?: Text(stringResource(R.string.preview_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
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
    modifier: Modifier = Modifier, done: Boolean = false, onNext: () -> Unit,
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
    val errorMessage = errorString?.let { stringResource(it) }
    OutlinedTextField(
        value = state.draft.value(field), onValueChange = { onChange(field, it) },
        modifier = modifier.semantics { errorMessage?.let { error(it) } }, enabled = !state.busy && !state.loading && !state.missing,
        label = { Text(stringResource(label)) }, singleLine = true, isError = error != null,
        shape = MaterialTheme.shapes.medium,
        placeholder = if (field == FoodField.NAME) ({ Text(stringResource(R.string.food_hint)) }) else null,
        suffix = if (field == FoodField.NAME) null else ({
            Text(stringResource(if (field == FoodField.ENERGY) R.string.kcal else R.string.grams))
        }),
        supportingText = if (errorMessage != null) ({ Text(errorMessage, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) })
            else if (field == FoodField.AMOUNT) ({ Text(stringResource(R.string.amount_helper)) }) else null,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (field == FoodField.NAME) KeyboardType.Text else KeyboardType.Decimal,
            capitalization = if (field == FoodField.NAME) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            imeAction = if (done) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext() },
            onDone = { focus.clearFocus() },
        ),
    )
}
