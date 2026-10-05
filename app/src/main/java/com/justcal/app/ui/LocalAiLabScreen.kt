package com.justcal.app.ui

import android.graphics.Bitmap
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import com.justcal.app.R
import com.justcal.app.ai.*
import com.justcal.app.camera.*
import java.text.NumberFormat
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable private data object LabForm : NavKey
@Serializable private data class LabImageSource(val source: ImageSource, val request: ScanRequest, val previousUri: String?) : NavKey

/** Nested saved image flow retains the Lab's loaded runtime and Navigation 3 predictive back. */
@Composable
fun LocalAiLabScreen(viewModel: LocalAiLabViewModel, onBack: () -> Unit) {
    val stack = rememberNavBackStack(LabForm)
    val image by viewModel.imageState.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val structured by viewModel.structured.collectAsStateWithLifecycle()
    val catalogReady by viewModel.catalogReady.collectAsStateWithLifecycle()
    NavDisplay(
        backStack = stack,
        onBack = {
            viewModel.cancelImage()
            if (stack.size > 1) stack.removeLastOrNull()
        },
        entryProvider = entryProvider {
            entry<LabForm> { _ ->
                LabFormScreen(
                    state, image, prompt, structured, catalogReady, viewModel, onBack,
                ) { source ->
                    if (!state.busy && !image.busy) {
                        stack.add(
                            LabImageSource(
                                source,
                                ScanRequest(ScanMode.PACKAGE, LocalDate.now().toEpochDay()),
                                image.image?.uri,
                            ),
                        )
                    }
                }
            }
            entry<LabImageSource> { (source, request, previousUri) ->
                val route = LabImageSource(source, request, previousUri)
                val imageUri = image.image?.uri
                LaunchedEffect(imageUri) {
                    if ((imageUri != null) && (imageUri != previousUri) && (stack.lastOrNull() == route)) {
                        stack.removeLastOrNull()
                    }
                }
                ImageSourceScreen(
                    request, source, image, viewModel.images,
                    onImage = { uri, inputSource -> viewModel.prepareImage(uri, inputSource, request) },
                    onRetry = viewModel::retryImage,
                ) {
                    viewModel.cancelImage()
                    if (stack.lastOrNull() == route) stack.removeLastOrNull()
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabFormScreen(
    state: AiLabState, imageState: ImageInputState, prompt: String, structured: Boolean,
    catalogReady: Boolean, vm: LocalAiLabViewModel, onBack: () -> Unit, onSource: (ImageSource) -> Unit,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val preset = stringResource(R.string.lab_nutrition_prompt)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importModel)
    }
    var menu by remember { mutableStateOf(value = false) }
    var confirmDelete by remember { mutableStateOf(value = false) }
    val editable = catalogReady && !state.busy && !imageState.busy
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.local_ai_lab)) },
            navigationIcon = { IconButton(onClick = onBack) { CalIcon(R.drawable.ic_back, stringResource(R.string.close)) } }) },
        contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.lab_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(if (state.cancelling) R.string.lab_cancelling else state.phase.label()),
                style = MaterialTheme.typography.titleMedium)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.phase == AiLabPhase.IMPORTING) {
                Text(stringResource(R.string.lab_imported, Formatter.formatFileSize(context, state.importedBytes)))
            }
            state.error?.let {
                SelectionContainer {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            SectionTitle(stringResource(R.string.lab_model))
            state.model?.let { model ->
                Text(model.name, style = MaterialTheme.typography.titleMedium)
                Text(Formatter.formatFileSize(context, model.sizeBytes), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = editable && !state.loaded) {
                    Text(stringResource(R.string.lab_import_model))
                }
                Box {
                    OutlinedButton(onClick = { menu = true }, enabled = editable && !state.loaded && state.models.isNotEmpty()) {
                        Text(stringResource(R.string.lab_select_model))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        state.models.forEach { model ->
                            DropdownMenuItem(text = { Text(model.name) }, onClick = { menu = false; vm.select(model) })
                        }
                    }
                }
                TextButton(onClick = { confirmDelete = true }, enabled = editable && !state.loaded && state.model != null) {
                    Text(stringResource(R.string.lab_delete_model))
                }
            }
            Text(stringResource(R.string.lab_model_hint), style = MaterialTheme.typography.bodySmall)
            SectionTitle(stringResource(R.string.lab_backend))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiBackend.entries.forEach { backend ->
                    FilterChip(selected = state.backend == backend, onClick = { vm.backend(backend) },
                        enabled = editable && !state.loaded, label = { Text(backend.name) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            Text(stringResource(R.string.lab_backend_hint), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::load, enabled = editable && state.model != null && !state.loaded) {
                    Text(stringResource(R.string.lab_load))
                }
                OutlinedButton(onClick = vm::unload, enabled = editable && state.loaded) {
                    Text(stringResource(R.string.lab_unload))
                }
            }
            SectionTitle(stringResource(R.string.lab_image))
            imageState.image?.let { image ->
                LabImagePreview(image, vm.images)
                Text(stringResource(R.string.image_dimensions, image.width, image.height))
            }
            if (imageState.error) Text(stringResource(R.string.image_error), color = MaterialTheme.colorScheme.error)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSource(ImageSource.PHOTO_PICKER) }, enabled = editable) {
                    Text(stringResource(R.string.choose_photo))
                }
                OutlinedButton(onClick = { onSource(ImageSource.CAMERA) }, enabled = editable) {
                    Text(stringResource(R.string.take_photo))
                }
                TextButton(onClick = vm::removeImage, enabled = editable && imageState.image != null) {
                    Text(stringResource(R.string.lab_remove_image))
                }
            }
            SectionTitle(stringResource(R.string.lab_prompt))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !structured, onClick = vm::freeForm, enabled = editable,
                    label = { Text(stringResource(R.string.lab_free_prompt)) }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = structured, onClick = { vm.preset(preset) }, enabled = editable,
                    label = { Text(stringResource(R.string.lab_nutrition_preset)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
            OutlinedTextField(prompt, vm::changePrompt, enabled = editable, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.lab_prompt)) }, minLines = 4, maxLines = 10)
            if (structured) Text(stringResource(R.string.lab_json_hint), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { focus.clearFocus(); vm.run() }, enabled = editable && state.loaded &&
                    imageState.image != null && prompt.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.lab_run))
                }
                if (state.busy && (state.phase !in setOf(AiLabPhase.UNLOADING, AiLabPhase.REMOVING))) {
                    OutlinedButton(onClick = vm::cancel, enabled = !state.cancelling) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
            SectionTitle(stringResource(R.string.lab_raw_response))
            SelectionContainer {
                Text(state.response.ifBlank { stringResource(R.string.lab_no_response) })
            }
            state.parsed?.let { parsed ->
                SectionTitle(stringResource(R.string.lab_parsed_result))
                SelectionContainer { Text(parsed.formattedJson, style = MaterialTheme.typography.bodyMedium) }
            }
            state.parsingError?.let {
                Text(stringResource(R.string.lab_parse_error), color = MaterialTheme.colorScheme.error)
                SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            SectionTitle(stringResource(R.string.lab_metrics))
            LabMetrics(state)
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.lab_delete_model)) },
        text = { Text(stringResource(R.string.lab_delete_confirm)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteModel() }) { Text(stringResource(R.string.delete_confirm)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun LabImagePreview(image: PreparedImage, images: ImagePreprocessor) {
    var bitmap by remember(image.uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(image.uri) { mutableStateOf(value = false) }
    LaunchedEffect(image.uri) {
        try { bitmap = images.decode(image, preview = true) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    DisposableEffect(image.uri) { onDispose { bitmap?.recycle(); bitmap = null } }
    if (failed) Text(stringResource(R.string.image_error), color = MaterialTheme.colorScheme.error)
    else bitmap?.let { Image(it.asImageBitmap(), stringResource(R.string.photo_description),
        Modifier.fillMaxWidth().heightIn(max = 240.dp), contentScale = ContentScale.Fit) }
}

@Composable
private fun LabMetrics(state: AiLabState) {
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 } }
    val unavailable = stringResource(R.string.lab_unavailable)
    fun value(value: Number?) = value?.let(format::format) ?: unavailable
    Text(stringResource(R.string.lab_backend_value, state.backend.name))
    Text(stringResource(R.string.lab_init_ms, value(state.initializationMillis)))
    state.metrics?.let { metrics ->
        Text(stringResource(R.string.lab_duration_ms, value(metrics.inferenceMillis)))
        Text(stringResource(R.string.lab_first_response_ms, value(metrics.firstResponseMillis)))
        Text(stringResource(R.string.lab_first_token_s, value(metrics.nativeFirstTokenSeconds)))
        Text(stringResource(R.string.lab_prefill_rate, value(metrics.prefillTokensPerSecond)))
        Text(stringResource(R.string.lab_decode_rate, value(metrics.decodeTokensPerSecond)))
        metrics.benchmarkError?.let { Text(stringResource(R.string.lab_benchmark_error, it), style = MaterialTheme.typography.bodySmall) }
    }
    Text(stringResource(R.string.lab_metrics_hint), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun AiLabPhase.label() = when (this) {
    AiLabPhase.NO_MODEL -> R.string.lab_no_model
    AiLabPhase.MODEL_SELECTED -> R.string.lab_model_selected
    AiLabPhase.IMPORTING -> R.string.lab_importing
    AiLabPhase.REMOVING -> R.string.lab_removing
    AiLabPhase.LOADING -> R.string.lab_loading
    AiLabPhase.READY -> R.string.lab_ready
    AiLabPhase.RUNNING -> R.string.lab_running
    AiLabPhase.CANCELLED -> R.string.lab_cancelled
    AiLabPhase.UNLOADING -> R.string.lab_unloading
    AiLabPhase.ERROR -> R.string.lab_error
}
