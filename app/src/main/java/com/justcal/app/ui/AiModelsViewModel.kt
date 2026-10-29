package com.justcal.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justcal.app.R
import com.justcal.app.ai.*
import com.justcal.app.data.GoalPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class InstalledAiModel(val model: LocalModel, val validation: ModelValidation?, val descriptor: ModelDescriptor?)
data class AiModelsState(
    val installed: List<InstalledAiModel> = emptyList(),
    val busy: Boolean = false, val importedBytes: Long = 0, val error: Int? = null,
    val interrupted: Boolean = false,
    val importing: Boolean = false,
)

@HiltViewModel
class AiModelsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: LocalModelStore, val catalog: ModelCatalog,
    val downloads: ModelDownloadRepository, private val preferences: GoalPreferences,
) : ViewModel() {
    private val mutable = MutableStateFlow(AiModelsState())
    val state = mutable.asStateFlow()
    val choices = preferences.aiModels.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    private var operation: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            downloads.state.map { values -> values.filterValues { it.phase == TransferPhase.INSTALLED }.keys }
                .distinctUntilChanged().collect { if (it.isNotEmpty()) refresh() }
        }
    }

    fun refresh() {
        if (mutable.value.busy) return
        operation = viewModelScope.launch {
            mutable.update { it.copy(busy = true) }
            try { reload() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(error = R.string.ai_storage_error) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }

    private suspend fun reload() {
        val installed = store.list().map { model ->
            val validation = try { store.validate(model) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                catch (_: LinkageError) { null }
            InstalledAiModel(model, validation, validation?.let { catalog.matching(it.sha256) })
        }
        preferences.aiModels.first().forEach { (task, id) ->
            if (id != null && installed.none { item -> item.model.id == id &&
                (if (task == "package") item.descriptor?.admitsPackage() == true else item.descriptor?.admitsMealName() == true) }) {
                preferences.selectAiModel(task, null)
            }
        }
        val interrupted = withContext(Dispatchers.IO) {
            java.io.File(context.noBackupFilesDir, "local-ai-interrupted.json").exists() &&
                ModelRuntimeLease.slot.availablePermits > 0
        }
        mutable.update { it.copy(installed = installed, interrupted = interrupted) }
    }

    fun import(uri: Uri) = mutate(R.string.ai_import_error, importing = true) {
        store.import(uri) { bytes -> mutable.update { it.copy(importedBytes = bytes) } }
        reload()
    }

    fun remove(model: LocalModel) = mutate(R.string.ai_remove_error) {
        store.remove(model)
        preferences.clearAiModel(model.id)
        reload()
    }

    fun selectable(task: String, installed: InstalledAiModel): Boolean {
        val validation = installed.validation ?: return false
        return installed.descriptor?.supportsRecognition(task, validation) == true
    }

    fun select(task: String, installed: InstalledAiModel?) = mutate(R.string.ai_storage_error) {
        if (installed != null) {
            require(selectable(task, installed))
        }
        preferences.selectAiModel(task, installed?.model?.id)
    }

    fun download(model: ModelDescriptor) = mutate(R.string.ai_transfer_error) {
        downloads.queue(model.id)
        try { startModelDownload(context, model) }
        catch (failure: Exception) { downloads.pause(); throw failure }
    }
    fun pause() { viewModelScope.launch { downloads.pause(); stopModelDownload(context) } }
    fun cancelDownload(id: String) = mutate(R.string.ai_storage_error) {
        val phase = downloads.state.value[id]?.phase
        if (phase in setOf(TransferPhase.QUEUED, TransferPhase.DOWNLOADING, TransferPhase.VERIFYING)) {
            stopModelDownload(context)
        }
        downloads.cancel(id)
    }
    fun cancelImport() { operation?.cancel() }
    fun dismissError() { mutable.update { it.copy(error = null) } }

    private fun mutate(error: Int, importing: Boolean = false, action: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, importing = importing, error = null, importedBytes = 0) }
        operation = viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(error = error) } }
            catch (_: LinkageError) { mutable.update { it.copy(error = error) } }
            finally { mutable.update { it.copy(busy = false, importing = false) } }
        }
    }
}
