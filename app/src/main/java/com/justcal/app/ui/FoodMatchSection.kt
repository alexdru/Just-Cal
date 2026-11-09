package com.justcal.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.justcal.app.R
import com.justcal.app.domain.*
import com.justcal.app.domain.Retention
import com.justcal.app.ui.theme.CalMotion
import com.justcal.app.ui.theme.CalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodMatchSuggestions(
    state: EditorState,
    baseline: FoodMatchEvidence?,
    onAction: (String) -> Unit,
    onInspect: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    val matching = state.matching
    val memory = matching.memory
    val enabled = !state.busy && !state.loading
    val applied = memory.undo != null && memory.origin.endsWith("SUBSTITUTION")
    var showAlternatives by rememberSaveable { mutableStateOf(false) }
    val hasUsefulContent = applied || matching.candidates.isNotEmpty() || matching.error || matching.unavailable
    if (!hasUsefulContent) return

    Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
        if (applied && !showAlternatives) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(CalSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(CalSpacing.small),
                ) {
                    Text(stringResource(R.string.match_values_applied), style = MaterialTheme.typography.labelLarge)
                    Text(memory.association?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                        TextButton(onClick = { onAction("undo") }, enabled = enabled) {
                            Text(stringResource(R.string.match_undo))
                        }
                        TextButton(onClick = { showAlternatives = true }, enabled = enabled) {
                            Text(stringResource(R.string.match_other_matches))
                        }
                    }
                }
            }
        } else {
            Text(stringResource(R.string.match_suggestions), style = MaterialTheme.typography.titleLarge)
            when {
                matching.error -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.match_error_short),
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { onAction("refresh") }, enabled = enabled) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
                matching.unavailable -> Text(
                    stringResource(R.string.match_unavailable),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            val visible = matching.candidates.take(if (matching.showAll) 5 else 2)
            if (visible.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    visible.forEachIndexed { index, candidate ->
                        SegmentedListItem(
                            onClick = { onInspect(candidate.key) },
                            enabled = enabled,
                            leadingContent = {
                                CalLeadingIcon(
                                    if (candidate.historyEntryUuid == null) R.drawable.ic_package
                                    else R.drawable.ic_diary,
                                )
                            },
                            trailingContent = {
                                TextButton(onClick = { onSelect(candidate.key) }, enabled = enabled) {
                                    Text(stringResource(R.string.match_use))
                                }
                            },
                            supportingContent = {
                                Text(
                                    candidateSummary(candidate),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(CalSpacing.medium),
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                            shapes = ListItemDefaults.segmentedShapes(
                                index,
                                visible.size,
                                ListItemDefaults.shapes(shape = MaterialTheme.shapes.extraSmall),
                            ),
                        ) {
                            Text(
                                candidate.revision.name,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
            if (!matching.showAll && matching.candidates.size > 2) {
                TextButton(onClick = { onAction("more") }, enabled = enabled) {
                    Text(stringResource(R.string.match_more))
                }
            }
            if (matching.stale) {
                TextButton(onClick = { onAction("refresh") }, enabled = enabled) {
                    Text(stringResource(R.string.match_update_suggestions))
                }
            }
            if (matching.truncated) {
                Text(
                    stringResource(R.string.match_bounded_short),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (matching.comparisonOpen && matching.inspected != null) {
        FoodMatchComparisonSheet(
            state = state,
            baseline = baseline,
            onDismiss = { onAction("collapse") },
            onAction = onAction,
            onInspect = onInspect,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodMatchComparisonSheet(
    state: EditorState,
    baseline: FoodMatchEvidence?,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit,
    onInspect: (String) -> Unit,
) {
    val matching = state.matching
    val candidate = matching.inspected ?: return
    var menu by rememberSaveable { mutableStateOf(false) }
    var forgetConfirmation by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CalSpacing.page)
                .padding(bottom = CalSpacing.section),
            verticalArrangement = Arrangement.spacedBy(CalSpacing.related),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                Column(Modifier.weight(1f)) {
                    Text(candidate.revision.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        candidateSummary(candidate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (candidate.historyEntryUuid == null) {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            CalIcon(R.drawable.ic_more, stringResource(R.string.entry_actions))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.match_versions)) },
                                onClick = { menu = false; onAction("versions") },
                                enabled = matching.moreVersions,
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.match_archive)) },
                                onClick = { menu = false; onAction("archive") },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.match_forget)) },
                                onClick = { menu = false; forgetConfirmation = true },
                            )
                        }
                    }
                }
            }

            when {
                forgetConfirmation -> {
                    Text(stringResource(R.string.match_forget_warning), style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                        TextButton(onClick = { forgetConfirmation = false }) {
                            Text(stringResource(R.string.backup_cancel))
                        }
                        TextButton(onClick = {
                            forgetConfirmation = false
                            onAction("forget")
                        }) {
                            Text(stringResource(R.string.match_forget), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                matching.pending != null -> ReplacementPrompt(
                    onCancel = { onAction("cancel") },
                    onConfirm = { onAction("confirm") },
                )
                else -> {
                    FoodComparison(state.draft, baseline, candidate.revision)
                    CalButton(
                        onClick = { onAction("apply") },
                        enabled = !state.busy && !state.loading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text(stringResource(R.string.match_use_saved))
                    }
                    if (candidate.historyEntryUuid != null) {
                        Text(
                            stringResource(R.string.match_history_selected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    matching.alternateVersions.forEach { version ->
                        TextButton(
                            onClick = { onInspect(version.key) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                version.revision.name + " · " +
                                    nutritionText(version.revision.energy.hundredthsText().toBigDecimal(), 2),
                            )
                        }
                    }
                    if (candidate.historyEntryUuid == null && matching.moreVersions) {
                        TextButton(onClick = { onAction("versions") }) {
                            Text(stringResource(R.string.match_versions))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun candidateSummary(candidate: FoodCandidate): String {
    val revision = candidate.revision
    val source = if (candidate.historyEntryUuid == null) {
        stringResource(R.string.match_catalog)
    } else {
        stringResource(R.string.match_history_short)
    }
    val metadata = listOfNotNull(
        revision.brand,
        revision.manufacturer,
        revision.deliveryService,
    ).joinToString(" · ")
    val nutritionValues = listOf(
        nutritionText(revision.energy.hundredthsText().toBigDecimal(), 2),
        nutritionText(revision.protein.hundredthsText().toBigDecimal(), 2),
        nutritionText(revision.fat.hundredthsText().toBigDecimal(), 2),
        nutritionText(revision.carbs.hundredthsText().toBigDecimal(), 2),
    ).joinToString(" / ")
    val nutrition = stringResource(R.string.match_nutrition_summary, nutritionValues)
    val warning = when (candidate.match?.category) {
        FoodMatchCategory.CONFLICTING -> stringResource(R.string.match_conflicting)
        FoodMatchCategory.INSUFFICIENT -> stringResource(R.string.match_insufficient)
        else -> ""
    }
    return listOf(source, metadata, nutrition, warning).filter { it.isNotBlank() }.joinToString("\n")
}

@Composable
fun FoodRetentionControls(
    state: EditorState,
    onRetention: (Retention) -> Unit,
    onDetails: (ProductDetails) -> Unit,
) {
    val memory = state.matching.memory
    val effective = memory.effectiveRetention(state.draft, state.matching.unavailable)
    val enabled = !state.busy && !state.loading
    val selectedProduct = memory.association != null && !memory.fromHistory && !state.matching.unavailable
    val exactReuse = selectedProduct && effective == Retention.REUSE
    var retentionOptions by rememberSaveable { mutableStateOf(false) }
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
        when {
            memory.association == null || memory.fromHistory || state.matching.unavailable -> {
                val checked = memory.retention == Retention.NEW_PRODUCT
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = {
                            onRetention(if (it) Retention.NEW_PRODUCT else Retention.DIARY_ONLY)
                        },
                    ),
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                    Text(
                        stringResource(
                            if (memory.fromHistory) R.string.match_promote else R.string.match_remember,
                        ),
                        Modifier.weight(1f),
                    )
                }
            }
            exactReuse -> {
                Text(
                    stringResource(R.string.match_reusing_product),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(CalSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(CalSpacing.small),
                    ) {
                        Text(
                            stringResource(R.string.match_changes_diary_only),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { retentionOptions = !retentionOptions }, enabled = enabled) {
                            Text(stringResource(R.string.match_reuse_options))
                        }
                        AnimatedVisibility(
                            retentionOptions,
                            enter = CalMotion.enter(),
                            exit = CalMotion.exit(),
                        ) {
                            Column {
                                RetentionOption(
                                    selected = effective == Retention.NEW_REVISION,
                                    label = stringResource(R.string.match_new_version),
                                    enabled = enabled,
                                ) { onRetention(Retention.NEW_REVISION) }
                                RetentionOption(
                                    selected = effective == Retention.NEW_PRODUCT,
                                    label = stringResource(R.string.match_different_product),
                                    enabled = enabled,
                                ) { onRetention(Retention.NEW_PRODUCT) }
                                RetentionOption(
                                    selected = effective == Retention.DIARY_ONLY,
                                    label = stringResource(R.string.match_diary_only),
                                    enabled = enabled,
                                ) { onRetention(Retention.DIARY_ONLY) }
                            }
                        }
                    }
                }
            }
        }

        if (effective != Retention.DIARY_ONLY) {
            TextButton(onClick = { detailsExpanded = !detailsExpanded }, enabled = enabled) {
                Text(stringResource(R.string.match_details))
            }
            AnimatedVisibility(detailsExpanded, enter = CalMotion.enter(), exit = CalMotion.exit()) {
                CalGroup {
                    val details = memory.details
                    listOf(
                        Triple(R.string.match_brand, details.brand, { value: String -> details.copy(brand = value) }),
                        Triple(R.string.match_manufacturer, details.manufacturer, { value: String -> details.copy(manufacturer = value) }),
                        Triple(R.string.match_retailer, details.retailer, { value: String -> details.copy(retailer = value) }),
                        Triple(R.string.match_delivery, details.deliveryService, { value: String -> details.copy(deliveryService = value) }),
                        Triple(R.string.match_package_mass, details.packageGrams, { value: String -> details.copy(packageGrams = value) }),
                    ).forEach { (label, value, update) ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { onDetails(update(it.take(200))) },
                            label = { Text(stringResource(label)) },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        stringResource(R.string.match_package_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun RetentionOption(
    selected: Boolean,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick,
        ),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, Modifier.weight(1f))
    }
}

@Composable
fun DuplicateProductDialog(
    state: EditorState,
    onDismiss: () -> Unit,
    onReuse: (String) -> Unit,
    onKeepSeparate: () -> Unit,
    onDiaryOnly: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.match_duplicate_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                Text(stringResource(R.string.match_duplicates))
                state.matching.duplicates.forEach { candidate ->
                    TextButton(
                        onClick = { onReuse(candidate.key) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.match_reuse_product, candidate.revision.name))
                    }
                }
                TextButton(onClick = onKeepSeparate, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.match_keep_separate))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDiaryOnly) {
                Text(stringResource(R.string.match_diary_only))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.backup_cancel))
            }
        },
    )
}

@Composable
fun ReplacementDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.match_replace_title)) },
        text = { Text(stringResource(R.string.match_replace_question)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.match_replace))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.backup_cancel))
            }
        },
    )
}

@Composable
private fun ReplacementPrompt(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(CalSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(CalSpacing.small),
        ) {
            Text(stringResource(R.string.match_replace_question))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.backup_cancel))
                }
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.match_replace))
                }
            }
        }
    }
}
