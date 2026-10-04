package com.justcal.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.justcal.app.BuildConfig
import com.justcal.app.R
import com.justcal.app.domain.*
import com.justcal.app.ui.theme.CalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsState, onChange: (SettingsDraft) -> Unit, onSave: () -> Unit,
    onRetry: () -> Unit, bottomBar: @Composable () -> Unit) {
    val focus = LocalFocusManager.current
    val direction = LocalLayoutDirection.current
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    var showMacros by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.loading) {
        if (!state.loading && listOf(state.draft.proteinGoal, state.draft.fatGoal, state.draft.carbsGoal).any { it.isNotBlank() }) {
            showMacros = true
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge) }) },
        bottomBar = { Box(Modifier.imePadding()) { bottomBar() } },
        contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
    ) { padding ->
        // Only the keyboard bounds the viewport. Content scrolls behind the floating controls;
        // end padding lets the final item settle above them.
        Column(Modifier.fillMaxSize().padding(
            start = padding.calculateStartPadding(direction), end = padding.calculateEndPadding(direction),
            top = padding.calculateTopPadding(), bottom = imeBottom,
        ).consumeWindowInsets(padding)
            .verticalScroll(rememberScrollState()).padding(horizontal = CalSpacing.page)
            .padding(top = 16.dp, bottom = (padding.calculateBottomPadding() - imeBottom).coerceAtLeast(0.dp) + 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            SectionTitle(stringResource(R.string.profile))
            Text(stringResource(R.string.profile_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = state.draft.displayName,
                onValueChange = { onChange(state.draft.copy(displayName = it)) },
                label = { Text(stringResource(R.string.display_name)) }, singleLine = true,
                enabled = !state.loading && !state.busy, modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                isError = state.submitted && !state.draft.nameValid,
                supportingText = { if (state.submitted && !state.draft.nameValid) Text(stringResource(R.string.name_error)) })
            SectionTitle(stringResource(R.string.nutrition_settings))
            OutlinedTextField(value = state.draft.goal, onValueChange = { onChange(state.draft.copy(goal = it)) },
                label = { Text(stringResource(R.string.goal_field)) }, suffix = { Text(stringResource(R.string.kcal)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.medium,
                enabled = !state.loading && !state.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.submitted && !state.draft.calorieGoalValid,
                supportingText = { if (state.submitted && !state.draft.calorieGoalValid) Text(stringResource(R.string.goal_error)) })
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { showMacros = !showMacros }, modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.macro_goals), Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.rotate(if (showMacros) 90f else 0f)) { CalIcon(R.drawable.ic_chevron) }
                    }
                }
                Text(stringResource(R.string.macro_goals_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showMacros) {
                    MacroGoalField(R.string.protein, state.draft.proteinGoal, state.submitted && !state.draft.proteinGoalValid,
                        !state.loading && !state.busy) { onChange(state.draft.copy(proteinGoal = it)) }
                    MacroGoalField(R.string.fat, state.draft.fatGoal, state.submitted && !state.draft.fatGoalValid,
                        !state.loading && !state.busy) { onChange(state.draft.copy(fatGoal = it)) }
                    MacroGoalField(R.string.carbs, state.draft.carbsGoal, state.submitted && !state.draft.carbsGoalValid,
                        !state.loading && !state.busy) { onChange(state.draft.copy(carbsGoal = it)) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(stringResource(R.string.appearance))
                Text(stringResource(R.string.brightness), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Appearance.entries.forEach { appearance ->
                        val label = when (appearance) {
                            Appearance.SYSTEM -> R.string.theme_system
                            Appearance.LIGHT -> R.string.theme_light
                            Appearance.DARK -> R.string.theme_dark
                        }
                        FilterChip(selected = state.draft.appearance == appearance,
                            onClick = { onChange(state.draft.copy(appearance = appearance)) },
                            label = { Text(stringResource(label)) }, enabled = !state.loading && !state.busy,
                            modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.color_style), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorStyle.entries.forEach { style ->
                        FilterChip(selected = state.draft.colorStyle == style,
                            onClick = { onChange(state.draft.copy(colorStyle = style)) },
                            label = { Text(stringResource(if (style == ColorStyle.JUST_CAL) R.string.color_just_cal else R.string.color_material_you)) },
                            enabled = !state.loading && !state.busy, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                Text(stringResource(R.string.color_style_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LanguageSettings()
            if (state.error) {
                Text(stringResource(R.string.storage_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            if (state.saved) Text(stringResource(R.string.settings_saved), color = MaterialTheme.colorScheme.primary)
            Button(onClick = { focus.clearFocus(); onSave() }, enabled = !state.loading && !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(if (state.busy) R.string.saving else R.string.save_changes))
            }
            HorizontalDivider()
            Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_description), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            AboutSettings()
        }
    }
}

@Composable
private fun AboutSettings() {
    val uriHandler = LocalUriHandler.current
    val repositoryUrl = stringResource(R.string.repository_url)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.about))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.app_author, stringResource(R.string.author_name)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(
            onClick = { uriHandler.openUri(repositoryUrl) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            shape = RectangleShape,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.repository), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.repository_name), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CalIcon(R.drawable.ic_chevron)
            }
        }
    }
}

@Composable
private fun MacroGoalField(label: Int, value: String, invalid: Boolean, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), enabled = enabled,
        label = { Text(stringResource(label)) }, suffix = { Text(stringResource(R.string.grams)) },
        placeholder = { Text(stringResource(R.string.goal_not_set)) },
        trailingIcon = if (value.isNotEmpty()) ({
            IconButton(onClick = { onChange("") }, enabled = enabled) {
                CalIcon(R.drawable.ic_clear, stringResource(R.string.clear_macro_goal, stringResource(label)))
            }
        }) else null,
        singleLine = true, shape = MaterialTheme.shapes.medium, isError = invalid,
        supportingText = if (invalid) ({ Text(stringResource(R.string.macro_goal_error)) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

/** Android owns the selected app locale; returning from system Settings rereads it. */
@Composable
private fun LanguageSettings() {
    val configuration = LocalConfiguration.current
    val focus = LocalFocusManager.current
    var language by remember { mutableStateOf(AppCompatDelegate.getApplicationLocales()[0]?.language.orEmpty()) }
    LaunchedEffect(configuration) {
        language = AppCompatDelegate.getApplicationLocales()[0]?.language.orEmpty()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        language = AppCompatDelegate.getApplicationLocales()[0]?.language.orEmpty()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.language))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("" to R.string.language_system, "en" to R.string.language_english, "ru" to R.string.language_russian)
                .forEach { (tag, label) ->
                    FilterChip(selected = language == tag, onClick = {
                        focus.clearFocus()
                        language = tag
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    }, label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp))
                }
        }
        Text(stringResource(R.string.language_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
