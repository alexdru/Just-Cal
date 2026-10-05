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
) : ViewModel() {
    private val session = LocalAiSession(runtime, viewModelScope)
    val state = session.state
    private val mutableCatalogReady = MutableStateFlow(value = false)
    val catalogReady = mutableCatalogReady.asStateFlow()
    private val mutableImage = MutableStateFlow(ImageInputState())
    val imageState = mutableImage.asStateFlow()
    val prompt = saved.getStateFlow("labPrompt", "")
    val structured = saved.getStateFlow("labStructured", initialValue = false)
    private var imageJob: Job? = null
    private var closing = false
    // Independent cleanup survives ViewModel scope cancellation and never blocks onCleared/main.
    private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        viewModelScope.launch {
            try { session.refresh(models.list()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { session.reportError(failure) }
            finally { mutableCatalogReady.value = true }
        }
    }

    fun changePrompt(value: String) { saved["labPrompt"] = value.take(16_000) }
    fun preset(value: String) { saved["labPrompt"] = value; saved["labStructured"] = true }
    fun freeForm() { saved["labStructured"] = false }
    fun select(model: LocalModel) = session.select(model)
    fun backend(value: AiBackend) = session.backend(value)
    fun importModel(uri: Uri) { if (catalogReady.value) session.importModel { progress -> models.import(uri, progress) } }
    fun deleteModel() = session.deleteModel(models::remove)
    fun load() = session.load()
    fun unload() = session.unload()
    fun run() { imageState.value.image?.let { session.run(it, prompt.value, structured.value) } }
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
            catch (failure: Exception) { Log.e("LocalAiLab", "Native cleanup failed", failure) }
            catch (failure: LinkageError) { Log.e("LocalAiLab", "Native cleanup unavailable", failure) }
            finally {
                imageJob?.join()
                mutableImage.value.image?.let { (uri) -> images.discard(uri) }
                cleanup.cancel()
            }
        }
    }

    override fun onCleared() { closeLab() }
}
