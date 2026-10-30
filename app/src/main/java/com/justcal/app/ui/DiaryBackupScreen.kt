package com.justcal.app.ui

import androidx.compose.animation.AnimatedVisibility
import com.justcal.app.ui.theme.CalMotion
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import com.justcal.app.ui.CalButton as Button
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justcal.app.R
import com.justcal.app.ui.theme.CalLayout
import com.justcal.app.ui.theme.CalSpacing

@Composable
fun DiaryBackupScreen(viewModel: DiaryBackupViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var recoveryExport by rememberSaveable { mutableStateOf(false) }
    var replaceConfirmation by rememberSaveable { mutableStateOf(false) }
    var replacementAcknowledged by rememberSaveable { mutableStateOf(false) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let { uri -> viewModel.export(uri, recoveryExport) }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(viewModel::preview)
    }
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = { CalTopBar(onBack = onBack, maxWidth = CalLayout.readingWidth,
            title = { Text(stringResource(R.string.data_backup)) }) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        val direction = LocalLayoutDirection.current
        BoxWithConstraints(Modifier.calContent(CalLayout.readingWidth)) {
            // The viewport extends behind bars. Short content fills it; large text/windows scroll.
            Column(Modifier.fillMaxWidth().testTag("backup_scroll")
                .verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                .padding(start = padding.calculateStartPadding(direction) + CalSpacing.page,
                    end = padding.calculateEndPadding(direction) + CalSpacing.page,
                    top = padding.calculateTopPadding() + CalSpacing.page,
                    bottom = padding.calculateBottomPadding() + CalSpacing.page)
                .consumeWindowInsets(padding),
                verticalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.fillMaxWidth().padding(bottom = CalSpacing.section),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CalSpacing.medium)) {
                    Icon(painterResource(R.drawable.ic_diary), contentDescription = null,
                        modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.backup_headline), style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center)
                    Text(stringResource(R.string.backup_description), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Column(Modifier.fillMaxWidth().testTag("backup_actions"),
                    verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                    AnimatedVisibility(state.busy, enter = CalMotion.enter(), exit = CalMotion.exit()) { CalWorkProgressRow() }
                    if (state.error) Text(stringResource(R.string.backup_error), color = MaterialTheme.colorScheme.error)
                    AnimatedVisibility(state.exported, enter = CalMotion.enter(), exit = CalMotion.exit()) {
                        Text(stringResource(R.string.backup_exported), style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    state.result?.let {
                        Text(stringResource(R.string.backup_result, it.imported, it.skipped, it.conflicting),
                            style = MaterialTheme.typography.bodyLarge)
                    }
                    Button(onClick = { recoveryExport = false; create.launch("just-cal-diary.json") },
                        enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.export_diary), textAlign = TextAlign.Center)
                    }
                    CalTonalButton(onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.import_diary), textAlign = TextAlign.Center)
                    }
                    if (state.recoveryAvailable) {
                        Text(stringResource(R.string.recovery_description), style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = CalSpacing.small))
                        OutlinedButton(onClick = { recoveryExport = true; create.launch("just-cal-recovery.json") },
                            enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.export_recovery), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
    state.preview?.let { backup ->
        if (!replaceConfirmation) AlertDialog(
            onDismissRequest = viewModel::dismissPreview,
            title = { Text(stringResource(R.string.import_diary)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                Text(stringResource(R.string.backup_preview, backup.entries.size))
                Text(stringResource(R.string.backup_merge_hint))
                TextButton(onClick = { replacementAcknowledged = false; replaceConfirmation = true },
                    enabled = !state.busy) {
                    Text(stringResource(R.string.replace_all), color = MaterialTheme.colorScheme.error)
                }
            } },
            confirmButton = { TextButton(onClick = { viewModel.import(false) }, enabled = !state.busy) {
                Text(stringResource(R.string.merge_diary))
            } },
            dismissButton = { TextButton(onClick = viewModel::dismissPreview, enabled = !state.busy) {
                Text(stringResource(R.string.backup_cancel))
            } },
        )
        else AlertDialog(
            onDismissRequest = { if (!state.busy) replaceConfirmation = false },
            title = { Text(stringResource(R.string.replace_all)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                Text(stringResource(R.string.replace_warning, backup.entries.size))
                Row(Modifier.fillMaxWidth().toggleable(value = replacementAcknowledged,
                    enabled = !state.busy, role = Role.Checkbox,
                    onValueChange = { replacementAcknowledged = it })) {
                    Checkbox(checked = replacementAcknowledged,
                        onCheckedChange = null, enabled = !state.busy)
                    Text(stringResource(R.string.replace_acknowledge), Modifier.weight(1f))
                }
            } },
            confirmButton = { TextButton(onClick = {
                replaceConfirmation = false
                viewModel.import(true)
            }, enabled = replacementAcknowledged && !state.busy) {
                Text(stringResource(R.string.replace_all), color = MaterialTheme.colorScheme.error)
            } },
            dismissButton = { TextButton(onClick = { replaceConfirmation = false }, enabled = !state.busy) {
                Text(stringResource(R.string.backup_cancel))
            } },
        )
    }
}
