package com.justcal.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import com.justcal.app.ui.theme.CalMotion
import com.justcal.app.ui.theme.ThemeRevealControls
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.ui.platform.LocalDensity
import com.justcal.app.ui.theme.CalLayout
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.justcal.app.BuildConfig
import com.justcal.app.R
import com.justcal.app.domain.*
import com.justcal.app.ui.theme.CalSpacing

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(state: SettingsState, onChange: (SettingsDraft) -> Unit,
    onRetry: () -> Unit, onLab: () -> Unit, bottomBar: @Composable () -> Unit,
    onBackup: () -> Unit = {}, onDiagnostics: () -> Unit = {}) {
    val focus = LocalFocusManager.current
    val direction = LocalLayoutDirection.current
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    var showMacros by rememberSaveable { mutableStateOf(false) }
    val macroAngle by animateFloatAsState(if (showMacros) 90f else 0f, CalMotion.fast(), label = "macro disclosure")
    val macroExpansion = stringResource(if (showMacros) R.string.expanded else R.string.collapsed)
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            CalTopBar(maxWidth = CalLayout.readingWidth, title = {
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge)
            })
        },
        bottomBar = bottomBar,
        contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
    ) { padding ->
        // Only the keyboard bounds the viewport. Content scrolls behind the floating controls;
        // end padding lets the final item settle above them.
        val overlaySpace = (padding.calculateBottomPadding() - imeBottom).coerceAtLeast(0.dp) + CalSpacing.medium
        val overlayPx = with(LocalDensity.current) { overlaySpace.toPx() }
        val defaultSpec = LocalBringIntoViewSpec.current
        val focusSpec = remember(defaultSpec, overlayPx) {
            object : BringIntoViewSpec {
                override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                    defaultSpec.calculateScrollDistance(offset, size, (containerSize - overlayPx).coerceAtLeast(0f))
            }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides focusSpec) {
            Column(Modifier.padding(
                start = padding.calculateStartPadding(direction), end = padding.calculateEndPadding(direction),
                top = padding.calculateTopPadding(), bottom = imeBottom,
            ).calContent(CalLayout.readingWidth).consumeWindowInsets(padding)
                .testTag("settings_scroll").verticalScroll(rememberScrollState()).padding(horizontal = CalSpacing.page)
                .padding(top = CalSpacing.medium, bottom = overlaySpace),
                verticalArrangement = Arrangement.spacedBy(CalSpacing.section)) {
                if (state.loading) CalLoadingRow()
                Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                    SectionTitle(stringResource(R.string.profile))
                    CalGroup {
                    OutlinedTextField(value = state.draft.displayName,
                        onValueChange = { onChange(state.draft.copy(displayName = it)) },
                        label = { Text(stringResource(R.string.display_name)) }, singleLine = true,
                        enabled = !state.loading, modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        isError = state.submitted && !state.draft.nameValid,
                        supportingText = { if (state.submitted && !state.draft.nameValid) Text(stringResource(R.string.name_error)) })
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                    SectionTitle(stringResource(R.string.nutrition_settings))
                    CalGroup {
                    OutlinedTextField(value = state.draft.goal, onValueChange = { onChange(state.draft.copy(goal = it)) },
                        label = { Text(stringResource(R.string.goal_field)) }, suffix = { Text(stringResource(R.string.kcal)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.medium,
                        enabled = !state.loading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = state.submitted && !state.draft.calorieGoalValid,
                        supportingText = { if (state.submitted && !state.draft.calorieGoalValid) Text(stringResource(R.string.goal_error)) })
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { showMacros = !showMacros }, modifier = Modifier.fillMaxWidth().semantics { stateDescription = macroExpansion },
                            contentPadding = PaddingValues(vertical = 8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.macro_goals), Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium)
                                Box(Modifier.rotate(macroAngle)) { CalIcon(R.drawable.ic_chevron) }
                            }
                        }
                        AnimatedVisibility(showMacros, enter = CalMotion.enter(), exit = CalMotion.exit()) {
                            Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                            MacroGoalField(R.string.protein, state.draft.proteinGoal, state.submitted && !state.draft.proteinGoalValid,
                                !state.loading) { onChange(state.draft.copy(proteinGoal = it)) }
                            MacroGoalField(R.string.fat, state.draft.fatGoal, state.submitted && !state.draft.fatGoalValid,
                                !state.loading) { onChange(state.draft.copy(fatGoal = it)) }
                            MacroGoalField(R.string.carbs, state.draft.carbsGoal, state.submitted && !state.draft.carbsGoalValid,
                                !state.loading) { onChange(state.draft.copy(carbsGoal = it)) }
                            }
                        }
                    }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                    SectionTitle(stringResource(R.string.appearance))
                    ThemeRevealControls { snapshot ->
                    CalGroup {
                    Text(stringResource(R.string.brightness), style = MaterialTheme.typography.titleMedium)
                    SettingsChoiceGroup(
                        options = Appearance.entries,
                        themeSelection = true,
                        interactive = !snapshot,
                        selected = state.draft.appearance,
                        enabled = !state.loading,
                        label = { stringResource(when (it) {
                            Appearance.SYSTEM -> R.string.theme_system
                            Appearance.LIGHT -> R.string.theme_light
                            Appearance.DARK -> R.string.theme_dark
                        }) },
                    ) { onChange(state.draft.copy(appearance = it)) }
                    Text(stringResource(R.string.color_style), style = MaterialTheme.typography.titleMedium)
                    SettingsChoiceGroup(
                        options = ColorStyle.entries,
                        themeSelection = true,
                        interactive = !snapshot,
                        selected = state.draft.colorStyle,
                        optionEnabled = { it != ColorStyle.MATERIAL_YOU || android.os.Build.VERSION.SDK_INT >= 31 },
                        enabled = !state.loading,
                        label = { stringResource(if (it == ColorStyle.JUST_CAL) R.string.color_just_cal else R.string.color_material_you) },
                    ) { onChange(state.draft.copy(colorStyle = it)) }
                    }
                    }
                }
                if (state.error) {
                    Text(stringResource(R.string.storage_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                LanguageSettings()
                Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                    SectionTitle(stringResource(R.string.data_backup))
                    CalGroup {
                        CalSettingsRow(stringResource(R.string.data_backup), R.drawable.ic_diary) {
                            focus.clearFocus(); onBackup()
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        CalSettingsRow(stringResource(R.string.ocr_diagnostics), R.drawable.ic_package) {
                            focus.clearFocus(); onDiagnostics()
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                    Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.privacy_description), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CalGroup {
                        CalSettingsRow(stringResource(R.string.local_ai_lab), R.drawable.ic_settings) {
                            focus.clearFocus(); onLab()
                        }
                    }
                }
                UpdateSettings()
                AboutSettings()
            }
        }
    }
}

@Composable
private fun AboutSettings() {
    val uriHandler = LocalUriHandler.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    var fontLicense by remember { mutableStateOf<String?>(null) }
    val repositoryUrl = stringResource(R.string.repository_url)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.about))
        CalGroup {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.app_license),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.app_license_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        CalSettingsRow(stringResource(R.string.font_credits), R.drawable.ic_settings) {
            fontLicense = resources.openRawResource(R.raw.roboto_flex_ofl).bufferedReader().use { it.readText() }
        }
        Text(stringResource(R.string.app_author, stringResource(R.string.author_name)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CalSettingsRow(stringResource(R.string.repository), R.drawable.ic_diary,
            subtitle = stringResource(R.string.repository_name)) { uriHandler.openUri(repositoryUrl) }
        }
    }
    fontLicense?.let { license ->
        AlertDialog(onDismissRequest = { fontLicense = null },
            title = { Text("Roboto Flex · SIL OFL 1.1") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(license, style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(onClick = { fontLicense = null }) { Text(stringResource(R.string.done)) } })
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
        CalGroup {
        SettingsChoiceGroup(
            options = listOf("", "en", "ru"),
            selected = language,
            label = { stringResource(when (it) {
                "en" -> R.string.language_english
                "ru" -> R.string.language_russian
                else -> R.string.language_system
            }) },
        ) { tag ->
            focus.clearFocus()
            language = tag
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
        }
    }
}
