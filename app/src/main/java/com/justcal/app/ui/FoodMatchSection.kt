package com.justcal.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.selected
import com.justcal.app.R
import com.justcal.app.domain.*
import com.justcal.app.domain.Retention
import com.justcal.app.ui.theme.CalMotion
import com.justcal.app.ui.theme.CalSpacing

@Composable
fun FoodMatchSection(
    state: EditorState, baseline: FoodMatchEvidence?, onAction: (String) -> Unit,
    onInspect: (String) -> Unit, onReuse: (String) -> Unit,
) {
    val s = state.matching
    val enabled = !state.busy && !state.loading
    var forget by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
        Text(stringResource(R.string.match_previous_foods), style = MaterialTheme.typography.titleLarge)
        AnimatedVisibility(true, enter = CalMotion.enter(), exit = CalMotion.exit()) {
            Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                if (s.indexing) Text(stringResource(R.string.match_indexing))
                if (s.searching) CalLoadingRow()
                if (s.error) Text(stringResource(R.string.match_error), color = MaterialTheme.colorScheme.error)
                if (s.stale) Text(stringResource(R.string.match_stale))
                if (s.truncated) Text(stringResource(R.string.match_bounded), style = MaterialTheme.typography.bodySmall)
                if (!s.indexing && !s.searching && s.candidates.isEmpty()) Text(stringResource(R.string.match_empty))
                val initial = s.candidates.take(if (s.showAll) 5 else 3)
                val selected = s.inspected
                val visible = if (selected != null && initial.none { it.key == selected.key })
                    initial.dropLast(1) + selected else initial
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    visible.forEachIndexed { index, c ->
                        val expanded = s.memory.inspectedKey == c.key
                        val expansion = stringResource(if (expanded) R.string.expanded else R.string.collapsed)
                        SegmentedListItem(
                            onClick = { if (expanded) onAction("collapse") else onInspect(c.key) }, enabled = enabled,
                            leadingContent = { CalLeadingIcon(if (c.historyEntryUuid == null) R.drawable.ic_package else R.drawable.ic_diary) },
                            trailingContent = { Box(Modifier.rotate(if (expanded) -90f else 90f)) { CalIcon(R.drawable.ic_chevron) } },
                            supportingContent = { Text(candidateDescription(c, baseline, details = false), style = MaterialTheme.typography.bodyMedium) },
                            modifier = Modifier.fillMaxWidth().semantics { stateDescription = expansion },
                            contentPadding = PaddingValues(CalSpacing.medium),
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shapes = ListItemDefaults.segmentedShapes(index, visible.size,
                                ListItemDefaults.shapes(shape = MaterialTheme.shapes.extraSmall)),
                        ) { Text(c.revision.name, style = MaterialTheme.typography.titleMedium) }
                        AnimatedVisibility(s.memory.inspectedKey == c.key && s.inspected != null,
                            enter = CalMotion.enter(), exit = CalMotion.exit()) {
                            s.inspected?.let { candidate ->
                                Column(Modifier.fillMaxWidth().padding(CalSpacing.medium),
                                    verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                                    Text(candidateDescription(candidate, baseline, details = true),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    FoodComparison(baseline, candidate.revision)
                                    Text(stringResource(R.string.match_review_only), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(stringResource(R.string.match_choose_source), style = MaterialTheme.typography.titleMedium)
                                    CalButton(onClick = { onAction("apply") }, enabled = enabled, modifier = Modifier.fillMaxWidth().semantics {
                                        this.selected = s.memory.association?.id == candidate.revision.id && s.memory.dirty.isEmpty()
                                    }) {
                                        CalIcon(if (s.memory.association?.id == candidate.revision.id && s.memory.dirty.isEmpty()) R.drawable.ic_check else R.drawable.ic_copy)
                                        Spacer(Modifier.width(CalSpacing.small))
                                        Text(stringResource(R.string.match_use_saved))
                                    }
                                    if (baseline != null) OutlinedButton(onClick = { onAction("scan") }, enabled = enabled,
                                        modifier = Modifier.fillMaxWidth().semantics {
                                            this.selected = s.memory.origin == "SCAN_OR_MANUAL" && s.memory.dirty.isEmpty()
                                        }) {
                                        CalIcon(if (s.memory.origin == "SCAN_OR_MANUAL" && s.memory.dirty.isEmpty()) R.drawable.ic_check else R.drawable.ic_package)
                                        Spacer(Modifier.width(CalSpacing.small))
                                        Text(stringResource(R.string.match_use_scan))
                                    }
                                    if (s.pending != null) FoodReplacementPrompt(enabled, onAction)
                                    if (candidate.historyEntryUuid == null) {
                                        var menu by remember { mutableStateOf(false) }
                                        Box {
                                            IconButton(onClick = { menu = true }, enabled = enabled) {
                                                CalIcon(R.drawable.ic_more, stringResource(R.string.entry_actions))
                                            }
                                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                                DropdownMenuItem(text = { Text(stringResource(R.string.match_versions)) },
                                                    onClick = { menu = false; onAction("versions") }, enabled = s.moreVersions)
                                                DropdownMenuItem(text = { Text(stringResource(R.string.match_previous_versions)) },
                                                    onClick = { menu = false; onAction("previous_versions") },
                                                    enabled = s.alternateOffset > s.alternateVersions.size)
                                                DropdownMenuItem(text = { Text(stringResource(R.string.match_archive)) },
                                                    onClick = { menu = false; onAction("archive") })
                                                DropdownMenuItem(text = { Text(stringResource(R.string.match_forget)) },
                                                    onClick = { menu = false; forget = true })
                                            }
                                        }
                                        s.alternateVersions.forEach { version ->
                                            TextButton(onClick = { onInspect(version.key) }, enabled = enabled) {
                                                Text(version.revision.name + " · " + nutritionText(version.revision.energy.hundredthsText().toBigDecimal(), 2))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!s.showAll && s.candidates.size > 3) TextButton(onClick = { onAction("more") }, enabled = enabled) {
                    Text(stringResource(R.string.match_more))
                }
                if (s.memory.inspectedKey != null && s.inspected == null && !s.searching)
                    Text(stringResource(R.string.match_unavailable))
                if (s.unavailable) Text(stringResource(R.string.match_unavailable))
                if (s.pending != null && s.inspected == null) FoodReplacementPrompt(enabled, onAction)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                    if (baseline != null && s.inspected == null) TextButton(onClick = { onAction("scan") }, enabled = enabled) { Text(stringResource(R.string.match_use_scan)) }
                    if (s.memory.undo != null) TextButton(onClick = { onAction("undo") }, enabled = enabled) { Text(stringResource(R.string.match_undo)) }
                    TextButton(onClick = { onAction("refresh") }, enabled = enabled) { Text(stringResource(R.string.match_refresh)) }
                    TextButton(onClick = { onAction("continue") }, enabled = enabled) { Text(stringResource(R.string.match_continue)) }
                }
                s.memory.association?.let {
                    Text(stringResource(if (s.memory.fromHistory) R.string.match_history_selected else R.string.match_selected),
                        style = MaterialTheme.typography.bodySmall)
                }
                FoodDuplicateChoices(state, onAction, onReuse)
            }
        }
    }
    if (forget) AlertDialog(onDismissRequest = { forget = false },
        title = { Text(stringResource(R.string.match_forget)) },
        text = { Text(stringResource(R.string.match_forget_warning)) },
        confirmButton = { TextButton(onClick = { forget = false; onAction("forget") }) { Text(stringResource(R.string.match_forget)) } },
        dismissButton = { TextButton(onClick = { forget = false }) { Text(stringResource(R.string.backup_cancel)) } })
}

@Composable
private fun FoodReplacementPrompt(enabled: Boolean, onAction: (String) -> Unit) {
    CalGroup {
        Text(stringResource(R.string.match_replace_question), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
            TextButton(onClick = { onAction("confirm") }, enabled = enabled) { Text(stringResource(R.string.match_replace)) }
            TextButton(onClick = { onAction("cancel") }, enabled = enabled) { Text(stringResource(R.string.backup_cancel)) }
        }
    }
}

@Composable
internal fun FoodDuplicateChoices(state: EditorState, onAction: (String) -> Unit, onReuse: (String) -> Unit) {
    val s = state.matching
    val enabled = !state.busy && !state.loading
    if (s.duplicates.isNotEmpty()) CalGroup {
                    Text(stringResource(R.string.match_duplicates))
                    s.duplicates.forEach { candidate ->
                        TextButton(onClick = { onReuse(candidate.key) }, enabled = enabled) {
                            Text(stringResource(R.string.match_reuse_product, candidate.revision.name))
                        }
                    }
                    TextButton(onClick = { onAction("separate") }, enabled = enabled) { Text(stringResource(R.string.match_keep_separate)) }
                    TextButton(onClick = { onAction("diary") }, enabled = enabled) { Text(stringResource(R.string.match_diary_only)) }
                }
}

@Composable
private fun candidateDescription(c: FoodCandidate, baseline: FoodMatchEvidence?, details: Boolean): String {
    val r = c.revision
    val source = stringResource(if (c.historyEntryUuid == null) R.string.match_catalog else R.string.match_history)
    val category = c.match?.category?.let {
        stringResource(when (it) { FoodMatchCategory.POSSIBLE -> R.string.match_possible
            FoodMatchCategory.CONFLICTING -> R.string.match_conflicting; FoodMatchCategory.INSUFFICIENT -> R.string.match_insufficient })
    }.orEmpty()
    val metadata = listOfNotNull(r.brand, r.manufacturer, r.deliveryService).joinToString(" · ")
    val exact = if (baseline?.basis == MatchNutritionBasis.PER_100_G) {
        val saved = listOf(r.energy, r.protein, r.fat, r.carbs)
        stringResource(R.string.match_equal_fields, baseline.nutrients.withIndex().count { (i, n) -> n.comparable && n.hundredths == saved[i] })
    } else ""
    val unknownManufacturer = if (baseline != null && baseline.manufacturer.text.isNullOrBlank())
        stringResource(R.string.match_manufacturer_unknown) else ""
    val history = if (c.historyEntryUuid == null) "" else stringResource(R.string.match_history_count, c.historyCount,
        c.lastDay?.let { java.time.LocalDate.ofEpochDay(it).format(java.time.format.DateTimeFormatter.ofLocalizedDate(
            java.time.format.FormatStyle.MEDIUM).withLocale(androidx.compose.ui.platform.LocalConfiguration.current.locales[0])) }.orEmpty())
    val reasons = c.match?.reasons.orEmpty().map { reason ->
        stringResource(when (reason) {
            FoodMatchReason.MISSING_NUTRITION -> R.string.match_missing_nutrition
            FoodMatchReason.NON_COMPARABLE_BASIS -> R.string.match_basis_unknown
            FoodMatchReason.IDENTITY_CONFLICT -> R.string.match_identity_conflict
            FoodMatchReason.RECIPE_CONFLICT -> R.string.match_recipe_conflict
            FoodMatchReason.NUTRITION_CONFLICT -> R.string.match_nutrition_conflict
            FoodMatchReason.RETAILER_DIFFERS -> R.string.match_retailer_differs
            FoodMatchReason.NAME_TRUNCATED -> R.string.match_name_bounded
            FoodMatchReason.WEAK_EVIDENCE -> R.string.match_insufficient
            FoodMatchReason.AI_EVIDENCE -> R.string.match_ai_evidence
        })
    }.joinToString(" · ")
    return (if (details) listOf(exact, unknownManufacturer, history, reasons)
        else listOf(source, metadata, category)).filter { it.isNotBlank() }.joinToString("\n")
}

@Composable
fun FoodRetentionControls(state: EditorState, onRetention: (Retention) -> Unit, onDetails: (ProductDetails) -> Unit) {
    val m = state.matching.memory
    val effective = m.effectiveRetention(state.draft, state.matching.unavailable)
    val enabled = !state.busy && !state.loading
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
        if (m.association == null || m.fromHistory || state.matching.unavailable) {
            val checked = m.retention == Retention.NEW_PRODUCT
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled, Role.Checkbox,
                onValueChange = { onRetention(if (it) Retention.NEW_PRODUCT else Retention.DIARY_ONLY) })) {
                Checkbox(checked, onCheckedChange = null, enabled = enabled)
                Text(stringResource(if (m.fromHistory) R.string.match_promote else R.string.match_remember), Modifier.weight(1f))
            }
        } else {
            Text(stringResource(R.string.match_changed_hint), style = MaterialTheme.typography.bodySmall)
            FlowRow {
                FilterChip(effective == Retention.NEW_REVISION, onClick = { onRetention(Retention.NEW_REVISION) },
                    label = { Text(stringResource(R.string.match_new_version)) }, enabled = enabled)
                FilterChip(effective == Retention.NEW_PRODUCT, onClick = { onRetention(Retention.NEW_PRODUCT) },
                    label = { Text(stringResource(R.string.match_different_product)) }, enabled = enabled)
                FilterChip(effective == Retention.DIARY_ONLY, onClick = { onRetention(Retention.DIARY_ONLY) },
                    label = { Text(stringResource(R.string.match_diary_only)) }, enabled = enabled)
            }
        }
        TextButton(onClick = { expanded = !expanded }, enabled = enabled) { Text(stringResource(R.string.match_details)) }
        AnimatedVisibility(expanded, enter = CalMotion.enter(), exit = CalMotion.exit()) {
            CalGroup {
                val d = m.details
                listOf(Triple(R.string.match_brand, d.brand, { v: String -> d.copy(brand = v) }),
                    Triple(R.string.match_manufacturer, d.manufacturer, { v: String -> d.copy(manufacturer = v) }),
                    Triple(R.string.match_retailer, d.retailer, { v: String -> d.copy(retailer = v) }),
                    Triple(R.string.match_delivery, d.deliveryService, { v: String -> d.copy(deliveryService = v) }),
                    Triple(R.string.match_package_mass, d.packageGrams, { v: String -> d.copy(packageGrams = v) }))
                    .forEach { (label, value, update) ->
                        OutlinedTextField(value, onValueChange = { onDetails(update(it.take(200))) },
                            label = { Text(stringResource(label)) }, enabled = enabled, modifier = Modifier.fillMaxWidth())
                    }
                Text(stringResource(R.string.match_package_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
