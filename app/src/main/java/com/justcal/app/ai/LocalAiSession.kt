package com.justcal.app.ai

import kotlin.time.Duration.Companion.seconds

import com.justcal.app.camera.PreparedImage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AiLabPhase { NO_MODEL, MODEL_SELECTED, IMPORTING, REMOVING, LOADING, READY, RUNNING, CANCELLED, UNLOADING, ERROR }

data class AiExternalResult(val raw: String, val structured: String, val metrics: AiMetrics, val modelName: String)

data class AiLabState(
    val phase: AiLabPhase = AiLabPhase.NO_MODEL,
    val models: List<LocalModel> = emptyList(),
    val model: LocalModel? = null,
    val backend: AiBackend = AiBackend.AUTO,
    val actualBackend: AiBackend? = null,
    val fallbackReason: String? = null,
    val loaded: Boolean = false,
    val cancelling: Boolean = false,
    val importedBytes: Long = 0,
    val initializationMillis: Long? = null,
    val response: String = "",
    val parsed: ExtractedNutrition? = null,
    val parsingError: String? = null,
    val metrics: AiMetrics? = null,
    val error: String? = null,
    val previousResponse: String? = null,
    val previousMetrics: AiMetrics? = null,
    val previousModelName: String? = null,
    val resultModelName: String? = null,
    val structuredOutput: String? = null,
) {
    val busy get() = phase in setOf(AiLabPhase.IMPORTING, AiLabPhase.REMOVING, AiLabPhase.LOADING, AiLabPhase.RUNNING, AiLabPhase.UNLOADING)
}

/** Testable Lab state transitions; neither Compose nor LiteRT-LM types cross this boundary. */
class LocalAiSession(private val engine: LocalAiEngine, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(AiLabState())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var closed = false

    fun refresh(models: List<LocalModel>) {
        if (closed || state.value.busy || state.value.loaded) return
        mutableState.update {
            it.copy(
                models = models,
                model = it.model?.takeIf { model -> model in models } ?: models.firstOrNull(),
                phase = if (models.isEmpty()) AiLabPhase.NO_MODEL else AiLabPhase.MODEL_SELECTED,
            )
        }
    }

    fun select(model: LocalModel) {
        if (closed || state.value.busy || state.value.loaded || (model !in state.value.models)) return
        mutableState.update { it.copy(model = model, phase = AiLabPhase.MODEL_SELECTED, error = null, actualBackend = null, fallbackReason = null,
            previousResponse = it.response.takeIf(String::isNotBlank) ?: it.previousResponse,
            previousMetrics = it.metrics ?: it.previousMetrics,
            previousModelName = if (it.response.isNotBlank()) it.resultModelName ?: it.model?.name else it.previousModelName,
            response = "", parsed = null, metrics = null, initializationMillis = null, parsingError = null, structuredOutput = null, resultModelName = null) }
    }

    fun backend(backend: AiBackend) {
        if (closed || state.value.busy || state.value.loaded) return
        mutableState.update { it.copy(backend = backend, error = null, response = "", parsed = null, actualBackend = null, fallbackReason = null,
            parsingError = null, metrics = null, initializationMillis = null, structuredOutput = null, resultModelName = null,
            phase = if (it.model == null) AiLabPhase.NO_MODEL else AiLabPhase.MODEL_SELECTED) }
    }

    fun importModel(import: suspend ((Long) -> Unit) -> LocalModel) {
        if (closed || state.value.busy || state.value.loaded) return
        launch(AiLabPhase.IMPORTING) {
            val model = import { bytes -> mutableState.update { it.copy(importedBytes = bytes) } }
            mutableState.update { it.copy(models = it.models + model, model = model, phase = AiLabPhase.MODEL_SELECTED,
                response = "", parsed = null, parsingError = null, metrics = null, initializationMillis = null, structuredOutput = null, resultModelName = null) }
        }
    }

    fun deleteModel(remove: suspend (LocalModel) -> Unit) {
        val model = state.value.model ?: return
        if (closed || state.value.busy || state.value.loaded) return
        launch(AiLabPhase.REMOVING) {
            remove(model)
            val remaining = state.value.models.filterNot { it == model }
            mutableState.update { it.copy(models = remaining, model = remaining.firstOrNull(),
                phase = if (remaining.isEmpty()) AiLabPhase.NO_MODEL else AiLabPhase.MODEL_SELECTED,
                initializationMillis = null, response = "", parsed = null, parsingError = null, metrics = null, structuredOutput = null) }
        }
    }

    fun load() {
        val current = state.value
        val model = current.model ?: return
        if (closed || current.busy || current.loaded) return
        launch(AiLabPhase.LOADING) {
            val duration = engine.load(model, current.backend)
            mutableState.update { it.copy(loaded = true, phase = AiLabPhase.READY, initializationMillis = duration,
                actualBackend = engine.actualBackend, fallbackReason = engine.fallbackReason) }
        }
    }

    fun run(image: PreparedImage, prompt: String, structured: Boolean,
        prepare: (suspend () -> PreparedImage)? = null, release: (PreparedImage) -> Unit = {},
        parseOutput: (String) -> String? = { null }) {
        if (closed || state.value.busy || !state.value.loaded || prompt.isBlank()) return
        mutableState.update { it.copy(previousResponse = it.response.takeIf(String::isNotBlank) ?: it.previousResponse,
            previousMetrics = it.metrics ?: it.previousMetrics,
            previousModelName = if (it.response.isNotBlank()) it.resultModelName ?: it.model?.name else it.previousModelName,
            response = "", parsed = null, parsingError = null, metrics = null, structuredOutput = null) }
        launch(AiLabPhase.RUNNING) {
            var prepared: PreparedImage? = null
            val metrics = try {
                withTimeout(180.seconds) {
                    val input = prepare?.invoke()?.also { prepared = it } ?: image
                    engine.generate(input, prompt, structured) { chunk ->
                        currentCoroutineContext().ensureActive()
                        require(state.value.response.length + chunk.length <= 128_000) { "Response exceeds Lab display budget" }
                        mutableState.update { it.copy(response = it.response + chunk) }
                        check(!AiResponseValidation.degenerate(state.value.response)) { "Repeated output; recognition failed" }
                    }
                }
            } finally {
                prepared?.let(release)
                if (!engine.reuseAcrossRuns) withContext(NonCancellable) {
                    engine.unload()
                    mutableState.update { it.copy(loaded = false) }
                }
            }
            val parsed = runCatching { NutritionExtraction.parse(state.value.response) }
            val taskParsed = runCatching { parseOutput(state.value.response) }
            mutableState.update { it.copy(phase = if (it.loaded) AiLabPhase.READY else AiLabPhase.MODEL_SELECTED, metrics = metrics,
                parsed = parsed.getOrNull(), resultModelName = it.model?.name,
                structuredOutput = taskParsed.getOrNull() ?: parsed.getOrNull()?.formattedJson,
                parsingError = taskParsed.exceptionOrNull()?.message ?: if (structured) parsed.exceptionOrNull()?.message
                    ?: parsed.getOrNull()?.takeIf(AiResponseValidation::allMissing)?.let { "No nutrition fields recognized" } else null) }
        }
    }

    fun runExternal(action: suspend () -> AiExternalResult) {
        if (closed || state.value.busy || state.value.loaded) return
        mutableState.update { it.copy(previousResponse = it.response.takeIf(String::isNotBlank) ?: it.previousResponse,
            previousMetrics = it.metrics ?: it.previousMetrics,
            previousModelName = if (it.response.isNotBlank()) it.resultModelName ?: it.model?.name else it.previousModelName,
            response = "", parsed = null, parsingError = null, metrics = null, structuredOutput = null) }
        launch(AiLabPhase.RUNNING) {
            val output = action()
            mutableState.update { it.copy(response = output.raw.take(128_000), structuredOutput = output.structured.take(16_000),
                metrics = output.metrics, resultModelName = output.modelName, phase = AiLabPhase.MODEL_SELECTED) }
        }
    }

    fun unload() {
        if (closed || state.value.busy || !state.value.loaded) return
        launch(AiLabPhase.UNLOADING) {
            engine.unload()
            mutableState.update { it.copy(loaded = false, phase = AiLabPhase.MODEL_SELECTED) }
        }
    }

    fun cancel() {
        if (!state.value.busy || state.value.phase in setOf(AiLabPhase.UNLOADING, AiLabPhase.REMOVING)) return
        mutableState.update { it.copy(cancelling = true) }
        operation?.cancel()
    }

    fun reportError(failure: Throwable) {
        mutableState.update { it.copy(phase = AiLabPhase.ERROR, error = failure.message?.takeUnless { "/" in it || "\\\\" in it } ?: failure.javaClass.simpleName) }
    }

    private fun launch(phase: AiLabPhase, action: suspend () -> Unit) {
        mutableState.update { it.copy(phase = phase, error = null, cancelling = false, importedBytes = 0) }
        operation = scope.launch {
            try { action() }
            catch (cancelled: CancellationException) {
                mutableState.update { it.copy(phase = AiLabPhase.CANCELLED, cancelling = false) }
                throw cancelled
            } catch (failure: Exception) { reportError(failure) }
            catch (failure: LinkageError) { reportError(failure) }
            finally { mutableState.update { it.copy(cancelling = false) } }
        }
    }

    /** Must be invoked on entry removal; joins cancellation before closing the native engine. */
    suspend fun close() {
        closed = true
        operation?.cancelAndJoin()
        engine.unload()
    }
}
