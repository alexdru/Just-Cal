package com.justcal.app.ui

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justcal.app.R
import com.justcal.app.ai.*
import com.justcal.app.ui.theme.CalLayout
import com.justcal.app.ui.theme.CalSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelsScreen(vm: AiModelsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val transfers by vm.downloads.state.collectAsStateWithLifecycle()
    val choices by vm.choices.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val direction = LocalLayoutDirection.current
    val uri = LocalUriHandler.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::import) }
    var deleting by remember { mutableStateOf<LocalModel?>(null) }
    var details by remember { mutableStateOf<InstalledAiModel?>(null) }
    var catalogDetails by remember { mutableStateOf<ModelDescriptor?>(null) }
    var choosingTask by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDownload by rememberSaveable { mutableStateOf<String?>(null) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingDownload?.let { id -> vm.catalog.models.firstOrNull { it.id == id }?.let(vm::download) }
        pendingDownload = null
    }
    fun download(model: ModelDescriptor) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingDownload = model.id
            notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else vm.download(model)
    }
    fun modelName(id: String?, fallback: String): String = state.installed.firstOrNull { it.model.id == id }
        ?.let { it.descriptor?.name ?: it.model.name } ?: fallback

    val available = vm.catalog.models.filter { model -> state.installed.none { it.validation?.sha256 == model.sha256 } }
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = { CalTopBar(title = { Text(stringResource(R.string.ai_models)) }, onBack = onBack, maxWidth = CalLayout.readingWidth) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        LazyColumn(
            Modifier.calContent(CalLayout.readingWidth).consumeWindowInsets(padding).testTag("ai_models_scroll"),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction) + CalSpacing.page,
                end = padding.calculateEndPadding(direction) + CalSpacing.page,
                top = padding.calculateTopPadding() + CalSpacing.medium,
                bottom = padding.calculateBottomPadding() + CalSpacing.page,
            ),
            verticalArrangement = Arrangement.spacedBy(CalSpacing.section),
        ) {
            if (state.interrupted || state.importing || state.error != null) item(key = "status") {
                Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                    if (state.interrupted) Text(stringResource(R.string.ai_interrupted), color = MaterialTheme.colorScheme.error)
                    if (state.importing) {
                        CalWorkProgressRow(Modifier.testTag("ai_model_import_progress"))
                        if (state.importedBytes > 0) Text(stringResource(R.string.lab_imported,
                            Formatter.formatFileSize(context, state.importedBytes)))
                        TextButton(onClick = vm::cancelImport) { Text(stringResource(R.string.cancel)) }
                    }
                    state.error?.let {
                        Text(stringResource(it), color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        TextButton(onClick = { vm.dismissError(); vm.refresh() }) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
            item(key = "defaults") {
                Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                    SectionTitle(stringResource(R.string.ai_defaults))
                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        CalSettingsRow(stringResource(R.string.ai_package_engine), R.drawable.ic_package,
                            subtitle = modelName(choices["package"], "").takeIf(String::isNotBlank)
                                ?.let { stringResource(R.string.ai_package_with_model, it) } ?: stringResource(R.string.ai_builtin),
                            index = 0, count = 2, enabled = !state.busy) { choosingTask = "package" }
                        CalSettingsRow(stringResource(R.string.ai_meal_engine), R.drawable.ic_meal,
                            subtitle = modelName(choices["meal"], stringResource(R.string.ai_disabled)),
                            index = 1, count = 2, enabled = !state.busy) { choosingTask = "meal" }
                    }
                }
            }
            item(key = "installed") {
                Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                    SectionTitle(stringResource(R.string.ai_installed))
                    if (state.installed.isEmpty() && !state.busy) Text(stringResource(R.string.ai_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        state.installed.forEachIndexed { index, installed ->
                            CalSettingsRow(installed.descriptor?.name ?: installed.model.name,
                                if (installed.validation == null) R.drawable.ic_attention else R.drawable.ic_memory,
                                subtitle = Formatter.formatFileSize(context, installed.model.sizeBytes) + " · " +
                                    stringResource(if (installed.validation == null) R.string.lab_error else R.string.ai_installed_status),
                                index = index, count = state.installed.size) { details = installed }
                        }
                    }
                }
            }
            if (available.isNotEmpty()) item(key = "catalog") {
                Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                    SectionTitle(stringResource(R.string.ai_available))
                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        available.forEachIndexed { index, model ->
                            Column(Modifier.calGroupedRow(index, available.size)) {
                                CalSettingsRow(model.name, R.drawable.ic_download,
                                    subtitle = Formatter.formatFileSize(context, model.bytes) + " · " +
                                        stringResource(if (model.profile == ModelTaskProfile.LABEL_TRANSCRIPTION)
                                            R.string.ai_profile_label else R.string.ai_profile_vision),
                                    index = index, count = available.size) { catalogDetails = model }
                                transfers[model.id]?.takeIf { it.phase != TransferPhase.INSTALLED }?.let { transfer ->
                                    Column(Modifier.padding(horizontal = CalSpacing.medium).padding(bottom = CalSpacing.medium),
                                        verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                                        ModelTransferStatus(transfer, model)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                                            if (transfer.active()) TextButton(onClick = vm::pause) { Text(stringResource(R.string.ai_pause)) }
                                            else TextButton(onClick = { download(model) }, enabled = !state.busy &&
                                                transfers.values.none { it.active() }) {
                                                Text(stringResource(if (transfer.phase == TransferPhase.FAILED) R.string.retry else R.string.ai_resume))
                                            }
                                            TextButton(onClick = { vm.cancelDownload(model.id) }, enabled = !state.busy) {
                                                Text(stringResource(R.string.cancel))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "import") {
                CalSettingsRow(stringResource(R.string.lab_import_model), R.drawable.ic_upload_file,
                    subtitle = stringResource(R.string.ai_import_hint), enabled = !state.busy) {
                    picker.launch(arrayOf("*/*"))
                }
            }
            item(key = "privacy") { Text(stringResource(R.string.ai_privacy), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    choosingTask?.let { task ->
        val models = state.installed.filter {
            if (task == "package") it.descriptor?.admitsPackage() == true else it.descriptor?.admitsMealName() == true
        }
        val sheetState = rememberBottomSheetState(
            SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
        val scope = rememberCoroutineScope()
        fun select(installed: InstalledAiModel?) {
            vm.select(task, installed)
            scope.launch {
                sheetState.hide()
                if (!sheetState.isVisible) choosingTask = null
            }
        }
        ModalBottomSheet(
            onDismissRequest = { choosingTask = null },
            sheetState = sheetState,
            sheetMaxWidth = CalLayout.readingWidth,
            modifier = Modifier.testTag("ai_model_task_sheet"),
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(CalSpacing.page)
                .navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                SectionTitle(stringResource(if (task == "package") R.string.ai_package_engine else R.string.ai_meal_engine))
                if (task == "package") Text(stringResource(R.string.ai_package_macros_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val count = models.size + 1
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    SegmentedListItem(selected = choices[task] == null,
                        onClick = { select(null) },
                        enabled = !state.busy,
                        shapes = ListItemDefaults.segmentedShapes(0, count),
                        leadingContent = { CalLeadingIcon(if (task == "package") R.drawable.ic_package else R.drawable.ic_edit) },
                        trailingContent = { RadioButton(selected = choices[task] == null, onClick = null) }) {
                        Text(stringResource(if (task == "package") R.string.ai_builtin else R.string.ai_disabled))
                    }
                    models.forEachIndexed { index, installed ->
                        SegmentedListItem(selected = choices[task] == installed.model.id,
                            onClick = { select(installed) },
                            enabled = !state.busy && vm.selectable(task, installed),
                            shapes = ListItemDefaults.segmentedShapes(index + 1, count),
                            leadingContent = { CalLeadingIcon(R.drawable.ic_memory) },
                            trailingContent = { RadioButton(selected = choices[task] == installed.model.id, onClick = null) }) {
                            val name = installed.descriptor?.name ?: installed.model.name
                            Text(if (task == "package") stringResource(R.string.ai_package_with_model, name) else name)
                        }
                    }
                }
                Text(stringResource(R.string.ai_task_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    catalogDetails?.let { model ->
        ModalBottomSheet(onDismissRequest = { catalogDetails = null }, sheetMaxWidth = CalLayout.readingWidth) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(CalSpacing.page)
                .navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                AiSectionTitle(model.name, R.drawable.ic_memory)
                Text(Formatter.formatFileSize(context, model.bytes))
                Text(stringResource(model.purposeLabel()))
                Text(stringResource(R.string.ai_privacy), style = MaterialTheme.typography.bodySmall)
                transfers[model.id]?.let { ModelTransferStatus(it, model) }
                val transfer = transfers[model.id]
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                    if (transfer?.active() == true) {
                        CalButton(onClick = vm::pause) { Text(stringResource(R.string.ai_pause)) }
                    } else if (state.installed.none { it.validation?.sha256 == model.sha256 }) {
                        CalButton(onClick = { download(model) }, enabled = !state.busy && transfers.values.none { it.active() }) {
                            CalIcon(R.drawable.ic_download)
                            Spacer(Modifier.width(CalSpacing.small))
                            Text(stringResource(if (transfer?.phase == TransferPhase.FAILED) R.string.retry
                                else if ((transfer?.bytes ?: 0) > 0) R.string.ai_resume else R.string.ai_download))
                        }
                    }
                    if (transfer != null && transfer.phase != TransferPhase.INSTALLED) {
                        TextButton(onClick = { vm.cancelDownload(model.id) }, enabled = !state.busy) { Text(stringResource(R.string.cancel)) }
                    }
                }
                Text("${model.repository}\n${model.revision}", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { uri.openUri(model.licenseUrl) }) {
                    Text(model.license + " · " + stringResource(R.string.ai_model_source))
                }
            }
        }
    }
    details?.let { installed ->
        ModalBottomSheet(onDismissRequest = { details = null }, sheetMaxWidth = CalLayout.readingWidth,
            sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(CalSpacing.page)
                .navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                AiSectionTitle(installed.descriptor?.name ?: installed.model.name, R.drawable.ic_memory)
                Text(Formatter.formatFileSize(context, installed.model.sizeBytes))
                installed.validation?.let { validation ->
                    Text("LiteRT-LM ${validation.runtime}")
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text("SHA-256: ${validation.sha256}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                installed.descriptor?.let { descriptor ->
                    Text("${descriptor.repository}\n${descriptor.revision}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { uri.openUri(descriptor.licenseUrl) }) { Text(descriptor.license) }
                }
                Text(stringResource(installed.descriptor?.purposeLabel() ?: R.string.ai_unverified),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { details = null; deleting = installed.model }, enabled = !state.busy) {
                    CalIcon(R.drawable.ic_delete)
                    Spacer(Modifier.width(CalSpacing.small))
                    Text(stringResource(R.string.lab_delete_model))
                }
            }
        }
    }
    deleting?.let {
        AlertDialog(onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.lab_delete_model)) },
            text = { Text(stringResource(R.string.lab_delete_confirm)) },
            confirmButton = { TextButton(onClick = { val model = deleting; deleting = null; model?.let(vm::remove) }) {
                Text(stringResource(R.string.delete_confirm))
            } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

private fun ModelDescriptor.purposeLabel(): Int = when {
    admitsPackage() && profile == ModelTaskProfile.GENERAL_VISION -> R.string.ai_package_macros_and_name
    admitsMealName() -> R.string.ai_name_only
    admitsPackage() -> R.string.ai_profile_label
    else -> R.string.ai_unverified
}

private fun ModelTransfer.active() = phase in setOf(TransferPhase.QUEUED, TransferPhase.DOWNLOADING, TransferPhase.VERIFYING)

@Composable
private fun ModelTransferStatus(transfer: ModelTransfer, model: ModelDescriptor) {
    val context = LocalContext.current
    when (transfer.phase) {
        TransferPhase.DOWNLOADING -> {
            CalProgress(progress = { transfer.bytes.toFloat() / model.bytes }, modifier = Modifier.fillMaxWidth())
            Text(Formatter.formatFileSize(context, transfer.bytes) + " / " + Formatter.formatFileSize(context, model.bytes))
        }
        TransferPhase.QUEUED, TransferPhase.VERIFYING -> CalWorkProgressRow()
        TransferPhase.PAUSED -> Text(stringResource(R.string.ai_paused,
            Formatter.formatFileSize(context, transfer.bytes)))
        TransferPhase.FAILED -> Text(stringResource(R.string.ai_transfer_error), color = MaterialTheme.colorScheme.error)
        TransferPhase.INSTALLED -> Text(stringResource(R.string.ai_installed_status))
    }
}

@Composable
fun AiSectionTitle(title: String, @androidx.annotation.DrawableRes icon: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(CalSpacing.related),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { CalIcon(icon) }
        SectionTitle(title)
    }
}
