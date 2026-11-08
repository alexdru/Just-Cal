package com.justcal.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.justcal.app.R
import com.justcal.app.domain.*
import com.justcal.app.ocr.PackageScanPrefill
import com.justcal.app.ocr.matchEvidence
import com.justcal.app.ui.theme.CalLayout
import com.justcal.app.ui.theme.CalSpacing

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoodMatchingScreen(
    state: EditorState, scan: PackageScanPrefill?, onChange: (FoodField, String) -> Unit,
    onAction: (String) -> Unit, onInspect: (String) -> Unit, onReuse: (String) -> Unit,
    onBack: () -> Unit, onCheck: () -> Unit,
) {
    val evidence = scan?.matchEvidence(state.recognition)
    val enabled = !state.busy && !state.loading
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val ime = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val direction = LocalLayoutDirection.current
    val filled = listOf(state.draft.energy, state.draft.protein, state.draft.fat, state.draft.carbs).count { it.isNotBlank() }
    val origin = when (state.matching.memory.origin) {
        "CATALOG_SUBSTITUTION" -> R.string.match_source_catalog
        "HISTORY_SUBSTITUTION" -> R.string.match_source_history
        else -> if (scan == null) R.string.match_source_manual else R.string.match_source_scan
    }
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { CalTopBar(onBack = onBack, maxWidth = CalLayout.readingWidth,
            title = { Column {
                Text(stringResource(R.string.match_add_product), style = MaterialTheme.typography.titleLarge)
                state.dayEpoch?.let { day ->
                    Text(java.time.LocalDate.ofEpochDay(day).format(java.time.format.DateTimeFormatter.ofLocalizedDate(
                        java.time.format.FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0])) +
                        " · " + stringResource(if (scan == null) R.string.match_title else R.string.match_package_scan),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }) },
        bottomBar = {
            Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)
                .union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)))
                .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = CalLayout.readingWidth).fillMaxWidth()) {
                CalButton(onClick = { focus.clearFocus(); keyboard?.hide(); onCheck() }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = CalSpacing.page, vertical = CalSpacing.related)) {
                    Text(stringResource(R.string.match_check_values))
                }
            }
        },
    ) { padding ->
        val clearance = (padding.calculateBottomPadding() - ime).coerceAtLeast(CalSpacing.medium) + CalSpacing.medium
        val clearancePx = with(LocalDensity.current) { clearance.toPx() }
        val defaultSpec = LocalBringIntoViewSpec.current
        val focusSpec = remember(defaultSpec, clearancePx) { object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) =
                defaultSpec.calculateScrollDistance(offset, size, (containerSize - clearancePx).coerceAtLeast(0f))
        } }
        CompositionLocalProvider(LocalBringIntoViewSpec provides focusSpec) {
        Column(Modifier.fillMaxSize().padding(start = padding.calculateStartPadding(direction),
            end = padding.calculateEndPadding(direction), bottom = ime).consumeWindowInsets(padding)
            .calContent(CalLayout.readingWidth).verticalScroll(rememberScrollState()).testTag("food_editor_scroll")
            .padding(horizontal = CalSpacing.page)
            .padding(top = padding.calculateTopPadding() + CalSpacing.medium,
                bottom = (padding.calculateBottomPadding() - ime).coerceAtLeast(CalSpacing.medium) + CalSpacing.section),
            verticalArrangement = Arrangement.spacedBy(CalSpacing.section)) {
            if (scan != null) CalGroup {
                Row(horizontalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                    CalIcon(R.drawable.ic_package)
                    Text(stringResource(R.string.match_recognized), style = MaterialTheme.typography.labelLarge)
                }
                Text(evidence?.name.orEmpty().ifBlank { stringResource(R.string.match_missing) }, style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.match_scan_count, evidence?.nutrients?.count { it.comparable } ?: 0),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val missing = listOf(R.string.energy, R.string.protein, R.string.fat, R.string.carbs).mapIndexedNotNull { index, label ->
                    if (evidence?.nutrients?.get(index)?.comparable != true) stringResource(label) else null
                }.joinToString(", ")
                if (missing.isNotEmpty()) Text(stringResource(R.string.match_missing_fields, missing),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FoodMatchSection(state, evidence, onAction = { action ->
                if (action == "continue") { onAction("cancel"); onCheck() } else onAction(action)
            }, onInspect, onReuse)
            CalGroup {
                CalValueRow(stringResource(R.string.match_current_entry), if (state.matching.memory.dirty.isEmpty()) stringResource(origin)
                    else stringResource(R.string.match_source_edited, stringResource(origin)))
                Text(state.draft.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.match_current_count, filled), style = MaterialTheme.typography.bodyMedium)
                FoodInputField(FoodField.AMOUNT, state, onChange, Modifier.fillMaxWidth(), done = true, onNext = {})
                Text(stringResource(R.string.match_amount_independent), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.match_explicit_save), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
    }
}
