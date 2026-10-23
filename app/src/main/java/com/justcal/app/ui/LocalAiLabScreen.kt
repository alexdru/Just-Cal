package com.justcal.app.ui

import android.graphics.Bitmap
import android.text.format.Formatter
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import com.justcal.app.ui.theme.CalSpacing
import com.justcal.app.ui.theme.CalMotion
import androidx.compose.ui.semantics.*
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
fun LocalAiLabScreen(viewModel: LocalAiLabViewModel, onBack: () -> Unit, onModels: () -> Unit = {}, onDiagnostics: () -> Unit = {}) {
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { viewModel.refreshCatalog() }
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
                    state, image, prompt, structured, catalogReady, viewModel, onBack, onModels, onDiagnostics,
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
    catalogReady: Boolean, vm: LocalAiLabViewModel, onBack: () -> Unit, onModels: () -> Unit,
    onDiagnostics: () -> Unit, onSource: (ImageSource) -> Unit,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val direction = LocalLayoutDirection.current
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val preset = stringResource(R.string.lab_nutrition_prompt)
    val labelPrompt = stringResource(R.string.ai_label_prompt)
    val mealPrompt = stringResource(R.string.ai_meal_prompt)
    var menu by remember { mutableStateOf(value = false) }
    var compare by remember { mutableStateOf(false) }
    val compareExpansion = stringResource(if (compare) R.string.expanded else R.string.collapsed)
    val task by vm.task.collectAsStateWithLifecycle()
    val editable = catalogReady && !state.busy && !imageState.busy
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = { CalTopBar(title = { Text(stringResource(R.string.local_ai_lab)) }, onBack = onBack,
            maxWidth = com.justcal.app.ui.theme.CalLayout.readingWidth) },
        contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
    ) { padding ->
        Column(Modifier.padding(bottom = imeBottom).consumeWindowInsets(padding)
            .calContent(com.justcal.app.ui.theme.CalLayout.readingWidth).testTag("ai_playground_scroll")
            .verticalScroll(rememberScrollState()).padding(
                start = padding.calculateStartPadding(direction) + 24.dp,
                end = padding.calculateEndPadding(direction) + 24.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = (padding.calculateBottomPadding() - imeBottom).coerceAtLeast(0.dp) + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(CalSpacing.section)) {
            Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                Text(stringResource(R.string.lab_intro), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(if (state.cancelling) R.string.lab_cancelling else state.phase.label()),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (state.busy || imageState.busy) CalWorkProgressRow()
                if (state.phase == AiLabPhase.IMPORTING) {
                    Text(stringResource(R.string.lab_imported, Formatter.formatFileSize(context, state.importedBytes)))
                }
                state.error?.let {
                    SelectionContainer { Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                AiSectionTitle(stringResource(R.string.lab_model), R.drawable.ic_memory)
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    CalSettingsRow(stringResource(R.string.ai_models), R.drawable.ic_memory,
                        index = 0, count = 2, enabled = editable, onClick = onModels)
                    CalGroup(index = 1, count = 2) {
                        Text(state.model?.name ?: stringResource(R.string.lab_no_model),
                            style = MaterialTheme.typography.titleMedium)
                        state.model?.let { model ->
                            Text(Formatter.formatFileSize(context, model.sizeBytes),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(stringResource(R.string.ai_auto), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box {
                            OutlinedButton(onClick = { menu = true },
                                enabled = editable && !state.loaded && state.models.isNotEmpty()) {
                                Text(stringResource(R.string.lab_select_model))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                state.models.forEach { model ->
                                    DropdownMenuItem(text = { Text(model.name) },
                                        onClick = { menu = false; vm.select(model) })
                                }
                            }
                        }
                        CalTonalButton(onClick = { if (state.loaded) vm.unload() else vm.load() },
                            enabled = editable && state.model != null, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (state.loaded) R.string.lab_unload else R.string.lab_load))
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                AiSectionTitle(stringResource(R.string.lab_image), R.drawable.ic_photo)
                imageState.image?.let { image ->
                    LabImagePreview(image, vm.images)
                    Text(stringResource(R.string.image_dimensions, image.width, image.height),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (imageState.error) Text(stringResource(R.string.image_error), color = MaterialTheme.colorScheme.error)
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    CalSettingsRow(stringResource(R.string.choose_photo), R.drawable.ic_photo,
                        index = 0, count = 2, enabled = editable) { onSource(ImageSource.PHOTO_PICKER) }
                    CalSettingsRow(stringResource(R.string.take_photo), R.drawable.ic_camera,
                        index = 1, count = 2, enabled = editable) { onSource(ImageSource.CAMERA) }
                }
                if (imageState.image != null) TextButton(onClick = vm::removeImage, enabled = editable) {
                    Text(stringResource(R.string.lab_remove_image))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                AiSectionTitle(stringResource(R.string.lab_prompt), R.drawable.ic_science)
                SettingsChoiceGroup(
                    options = listOf("ocr", "label", "meal", "custom"),
                    selected = task,
                    enabled = editable,
                    optionEnabled = { it != "ocr" || !state.loaded },
                    label = { stringResource(when (it) {
                        "ocr" -> R.string.ai_builtin
                        "label" -> R.string.ai_ocr_preset
                        "meal" -> R.string.ai_meal_preset
                        else -> R.string.ai_custom_preset
                    }) },
                ) { choice ->
                    when (choice) {
                        "ocr" -> vm.chooseTask("ocr")
                        "label" -> vm.chooseTask("label", labelPrompt)
                        "meal" -> vm.chooseTask("meal", mealPrompt)
                        else -> vm.freeForm()
                    }
                }
                if (task == "custom") SettingsChoiceGroup(
                    options = listOf(false, true), selected = structured, enabled = editable,
                    label = { stringResource(if (it) R.string.lab_nutrition_preset else R.string.lab_free_text) },
                ) { if (it) vm.preset(preset) else vm.freeForm() }
                if (task != "ocr") OutlinedTextField(prompt, vm::changePrompt, enabled = editable,
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    label = { Text(stringResource(R.string.lab_prompt)) }, minLines = 4, maxLines = 10)
                if (structured) Text(stringResource(R.string.lab_json_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CalButton(onClick = { focus.clearFocus(); vm.run() },
                    enabled = editable && (state.loaded || task == "ocr") &&
                        imageState.image != null && (task == "ocr" || prompt.isNotBlank()),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    CalIcon(R.drawable.ic_science)
                    Spacer(Modifier.width(CalSpacing.small))
                    Text(stringResource(R.string.lab_run))
                }
                if (state.busy && state.phase !in setOf(AiLabPhase.UNLOADING, AiLabPhase.REMOVING)) {
                    OutlinedButton(onClick = vm::cancel, enabled = !state.cancelling, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                AiSectionTitle(stringResource(R.string.lab_raw_response), R.drawable.ic_diary)
                CalGroup {
                    SelectionContainer {
                        Text(state.response.ifBlank { stringResource(R.string.lab_no_response) },
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                state.structuredOutput?.let { parsed ->
                    SectionTitle(stringResource(R.string.lab_parsed_result))
                    CalGroup { SelectionContainer { Text(parsed, style = MaterialTheme.typography.bodyMedium) } }
                }
                state.parsingError?.let {
                    Text(stringResource(R.string.lab_parse_error), color = MaterialTheme.colorScheme.error)
                    SelectionContainer { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                if (state.previousResponse != null) {
                    TextButton(onClick = { compare = !compare },
                        modifier = Modifier.semantics { stateDescription = compareExpansion }) {
                        Text(stringResource(R.string.ai_compare))
                    }
                    androidx.compose.animation.AnimatedVisibility(compare, enter = CalMotion.enter(), exit = CalMotion.exit()) {
                        Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                            SectionTitle(stringResource(R.string.ai_previous))
                            CalGroup {
                                state.previousModelName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                                SelectionContainer { Text(state.previousResponse, style = MaterialTheme.typography.bodyMedium) }
                            }
                            state.previousMetrics?.let { previous ->
                                LabMetrics(state.copy(metrics = previous,
                                    initializationMillis = previous.initializationMillis, actualBackend = previous.backend))
                            }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                AiSectionTitle(stringResource(R.string.lab_metrics), R.drawable.ic_update)
                LabMetrics(state)
                CalSettingsRow(stringResource(R.string.ai_diagnostics), R.drawable.ic_package,
                    enabled = editable, onClick = onDiagnostics)
            }
        }
    }
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
    Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
        CalGroup {
            Text(stringResource(R.string.lab_backend_value, (state.metrics?.backend ?: state.actualBackend ?: state.backend).name),
                style = MaterialTheme.typography.titleMedium)
            state.fallbackReason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.lab_init_ms, value(state.initializationMillis)), style = MaterialTheme.typography.bodyMedium)
            state.metrics?.let { metrics ->
                Text(stringResource(R.string.lab_duration_ms, value(metrics.inferenceMillis)), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.lab_first_response_ms, value(metrics.firstResponseMillis)), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.lab_first_token_s, value(metrics.nativeFirstTokenSeconds)), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.lab_prefill_rate, value(metrics.prefillTokensPerSecond)), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.lab_decode_rate, value(metrics.decodeTokensPerSecond)), style = MaterialTheme.typography.bodyMedium)
                metrics.benchmarkError?.let { Text(stringResource(R.string.lab_benchmark_error, it),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
        }
        Text(stringResource(R.string.ai_auto_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.lab_metrics_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
