package com.justcal.app.ai

import com.justcal.app.camera.PreparedImage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AiLabPhase { NO_MODEL, MODEL_SELECTED, IMPORTING, REMOVING, LOADING, READY, RUNNING, CANCELLED, UNLOADING, ERROR }

data class AiLabState(
    val phase: AiLabPhase = AiLabPhase.NO_MODEL,
    val models: List<LocalModel> = emptyList(),
    val model: LocalModel? = null,
    val backend: AiBackend = AiBackend.CPU,
    val loaded: Boolean = false,
    val cancelling: Boolean = false,
    val importedBytes: Long = 0,
    val initializationMillis: Long? = null,
    val response: String = "",
    val parsed: ExtractedNutrition? = null,
    val parsingError: String? = null,
    val metrics: AiMetrics? = null,
    val error: String? = null,
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
        mutableState.update { it.copy(model = model, phase = AiLabPhase.MODEL_SELECTED, error = null,
            response = "", parsed = null, metrics = null, initializationMillis = null, parsingError = null) }
    }

    fun backend(backend: AiBackend) {
        if (closed || state.value.busy || state.value.loaded) return
        mutableState.update { it.copy(backend = backend, error = null, response = "", parsed = null,
            parsingError = null, metrics = null, initializationMillis = null,
            phase = if (it.model == null) AiLabPhase.NO_MODEL else AiLabPhase.MODEL_SELECTED) }
    }

    fun importModel(import: suspend ((Long) -> Unit) -> LocalModel) {
        if (closed || state.value.busy || state.value.loaded) return
        launch(AiLabPhase.IMPORTING) {
            val model = import { bytes -> mutableState.update { it.copy(importedBytes = bytes) } }
            mutableState.update { it.copy(models = it.models + model, model = model, phase = AiLabPhase.MODEL_SELECTED,
                response = "", parsed = null, parsingError = null, metrics = null, initializationMillis = null) }
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
                initializationMillis = null, response = "", parsed = null, parsingError = null, metrics = null) }
        }
    }

    fun load() {
        val current = state.value
        val model = current.model ?: return
        if (closed || current.busy || current.loaded) return
        launch(AiLabPhase.LOADING) {
            val duration = engine.load(model, current.backend)
            mutableState.update { it.copy(loaded = true, phase = AiLabPhase.READY, initializationMillis = duration) }
        }
    }

    fun run(image: PreparedImage, prompt: String, structured: Boolean) {
        if (closed || state.value.busy || !state.value.loaded || prompt.isBlank()) return
        mutableState.update { it.copy(response = "", parsed = null, parsingError = null, metrics = null) }
        launch(AiLabPhase.RUNNING) {
            val metrics = engine.generate(image, prompt, structured) { chunk ->
                currentCoroutineContext().ensureActive()
                require(state.value.response.length + chunk.length <= 128_000) { "Response exceeds Lab display budget" }
                mutableState.update { it.copy(response = it.response + chunk) }
            }
            val parsed = runCatching { NutritionExtraction.parse(state.value.response) }
            mutableState.update { it.copy(phase = AiLabPhase.READY, metrics = metrics,
                parsed = parsed.getOrNull(), parsingError = if (structured) parsed.exceptionOrNull()?.message else null) }
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
        mutableState.update { it.copy(phase = AiLabPhase.ERROR, error = failure.message ?: failure.javaClass.simpleName) }
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
