package com.justcal.app.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justcal.app.ai.*
import com.justcal.app.camera.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class LocalAiLabViewModel @Inject constructor(
    runtime: LiteRtLocalAiEngine,
    private val models: LocalModelStore,
    val images: ImagePreprocessor,
    private val saved: SavedStateHandle,
    private val ocr: com.justcal.app.ocr.PaddlePackageOcrEngine,
    private val diagnostics: com.justcal.app.diagnostics.OcrDiagnosticSession,
    private val catalog: ModelCatalog,
    private val cropper: LabelImageCropper,
) : ViewModel() {
    private val session = LocalAiSession(runtime, viewModelScope)
    val state = session.state
    private val mutableCatalogReady = MutableStateFlow(value = false)
    val catalogReady = mutableCatalogReady.asStateFlow()
    private val mutableImage = MutableStateFlow(ImageInputState())
    val imageState = mutableImage.asStateFlow()
    val prompt = saved.getStateFlow("labPrompt", "")
    val structured = saved.getStateFlow("labStructured", initialValue = false)
    val task = saved.getStateFlow("labTask", "custom")
    private var imageJob: Job? = null
    private var closing = false
    // Independent cleanup survives ViewModel scope cancellation and never blocks onCleared/main.
    private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        viewModelScope.launch {
            var attempt: com.justcal.app.diagnostics.OcrDiagnosticSession.Attempt? = null
            var runPrompt = ""
            var runModel: LocalModel? = null
            state.collect { value ->
                if (value.phase == AiLabPhase.RUNNING && attempt == null && task.value != "ocr") {
                    imageState.value.image?.let { attempt = diagnostics.begin(it) }
                    runPrompt = prompt.value
                    runModel = value.model
                } else if (value.phase != AiLabPhase.RUNNING && attempt != null) {
                    val completed = attempt!!
                    attempt = null
                    val validation = runModel?.let { runCatching { models.validate(it) }.getOrNull() }
                    val descriptor = validation?.let { catalog.matching(it.sha256) }
                    val memory = android.os.Debug.MemoryInfo().also { android.os.Debug.getMemoryInfo(it) }
                    completed.ai(com.justcal.app.diagnostics.DiagnosticAiAttempt(
                        stage = value.phase.name, artifactHash = validation?.sha256.orEmpty(),
                        revision = descriptor?.revision, runtimeVersion = AI_RUNTIME_VERSION,
                        profile = descriptor?.profile?.name ?: "CUSTOM", requestedBackend = value.backend.name,
                        actualBackend = value.metrics?.backend?.name ?: value.actualBackend?.name,
                        backendAttempts = value.metrics?.backendAttempts.orEmpty(), fallbackReason = value.fallbackReason,
                        prompt = runPrompt.take(16_000),
                        rawOutput = value.response.take(16_000), initializationMs = value.initializationMillis,
                        inferenceMs = value.metrics?.inferenceMillis, firstResponseMs = value.metrics?.firstResponseMillis,
                        pssKiB = memory.totalPss),
                        if (value.phase == AiLabPhase.CANCELLED) "CANCELLED" else if (value.phase == AiLabPhase.ERROR) "FAILURE" else "EXPERIMENT")
                }
            }
        }
        viewModelScope.launch {
            try { session.refresh(models.list()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { session.reportError(failure) }
            finally { mutableCatalogReady.value = true }
        }
    }

    fun refreshCatalog() {
        if (!state.value.busy && !state.value.loaded) viewModelScope.launch { session.refresh(models.list()) }
    }
    fun chooseTask(value: String, text: String = "") {
        saved["labTask"] = value
        saved["labStructured"] = false
        saved["labPrompt"] = text
    }
    fun changePrompt(value: String) { saved["labPrompt"] = value.take(16_000) }
    fun preset(value: String) { saved["labPrompt"] = value; saved["labStructured"] = true }
    fun freeForm() { saved["labStructured"] = false; saved["labTask"] = "custom" }
    fun select(model: LocalModel) = session.select(model)
    fun load() = session.load()
    fun unload() = session.unload()
    fun run() { imageState.value.image?.let { image ->
        if (task.value == "ocr") session.runExternal {
            val attempt = diagnostics.begin(image)
            val result = try { ocr.recognize(images.verify(image)) }
                catch (cancelled: CancellationException) { attempt.failure(true, "PLAYGROUND_OCR_CANCELLED"); throw cancelled }
                catch (failure: Exception) { attempt.failure(false, "PLAYGROUND_OCR_FAILURE"); throw failure }
            attempt.success(result, com.justcal.app.ocr.PackageScanPrefill.from(result), 0, origin = "PLAYGROUND")
            AiExternalResult(result.lines.joinToString("\n") { it.text },
                kotlinx.serialization.json.Json.encodeToString(com.justcal.app.ocr.NutritionLabelParser.parse(result)),
                AiMetrics(AiBackend.CPU, result.metrics.initializationMs, result.metrics.totalMs, null), "PP-OCRv5")
        } else session.run(image, prompt.value, structured.value, prepare = if (task.value == "label") ({
            val result = ocr.recognize(images.verify(image))
            cropper.crop(image, result) ?: error("Nutrition block unavailable; take a closer photo")
        }) else null, release = { prepared -> if (prepared.uri != image.uri) images.discard(prepared.uri) },
            parseOutput = { raw -> when (task.value) {
                "label" -> kotlinx.serialization.json.Json.encodeToString(com.justcal.app.ocr.NutritionLabelParser.parseUnscoredTranscription(raw))
                "meal" -> kotlinx.serialization.json.Json.encodeToString(MealRecognition.parse(raw))
                else -> null
            } })
    } }
    fun cancel() = session.cancel()

    fun prepareImage(uri: Uri, source: ImageSource, request: ScanRequest) {
        if (closing || state.value.busy || imageState.value.busy) return
        mutableImage.value = mutableImage.value.copy(busy = true, error = false)
        imageJob = viewModelScope.launch {
            try {
                val prepared = images.prepare(uri, source, request)
                mutableImage.value.image?.let { (uri) -> images.discard(uri) }
                mutableImage.value = ImageInputState(image = prepared)
            } catch (cancelled: CancellationException) {
                mutableImage.value = mutableImage.value.copy(busy = false)
                throw cancelled
            } catch (_: Exception) { mutableImage.value = mutableImage.value.copy(busy = false, error = true) }
        }
    }

    fun cancelImage() { imageJob?.cancel(); mutableImage.value = mutableImage.value.copy(busy = false, error = false) }
    fun retryImage() { mutableImage.value = mutableImage.value.copy(error = false) }
    fun removeImage() {
        if (state.value.busy || imageState.value.busy) return
        mutableImage.value.image?.let { (uri) -> images.discard(uri) }
        mutableImage.value = ImageInputState()
    }

    fun closeLab() {
        if (closing) return
        closing = true
        imageJob?.cancel()
        cleanup.launch {
            try { session.close() }
            catch (failure: Exception) { Log.e("LocalAiLab", "Native cleanup failed: ${failure.javaClass.simpleName}") }
            catch (failure: LinkageError) { Log.e("LocalAiLab", "Native cleanup unavailable: ${failure.javaClass.simpleName}") }
            finally {
                imageJob?.join()
                mutableImage.value.image?.let { (uri) -> images.discard(uri) }
                cleanup.cancel()
            }
        }
    }

    override fun onCleared() { closeLab() }
}
