package com.justcal.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justcal.app.BuildConfig
import com.justcal.app.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class UpdateState(
    val loaded: Boolean = false,
    val automatic: Boolean = true,
    val savingPreference: Boolean = false,
    val checking: Boolean = false,
    val checked: Boolean = false,
    val update: AppUpdate? = null,
    val showSheet: Boolean = false,
    val automaticPrompt: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val apkUri: String? = null,
    val error: UpdateError? = null,
)

internal sealed interface UpdateFeedback {
    data object CurrentVersion : UpdateFeedback
    data class Failure(val error: UpdateError) : UpdateFeedback
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
    private val clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(UpdateState())
    val state = mutableState.asStateFlow()
    private val mutableFeedback = MutableSharedFlow<UpdateFeedback>(extraBufferCapacity = 1)
    internal val feedback = mutableFeedback.asSharedFlow()
    private val ready = CompletableDeferred<Unit>()
    private var preferences: UpdatePreferences? = null
    private var operation: Job? = null

    init {
        viewModelScope.launch {
            try {
                preferences = repository.preferences()
                mutableState.update { it.copy(loaded = true, automatic = preferences!!.automatic) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(loaded = true, automatic = false, error = UpdateError.STORAGE) }
            } finally {
                ready.complete(Unit)
            }
        }
    }

    fun check(automatic: Boolean = false) {
        if (state.value.savingPreference || (automatic && state.value.showSheet)) return
        if (!automatic && state.value.apkUri != null && state.value.update != null) {
            showUpdate()
            return
        }
        if (operation?.isActive == true) {
            // A manual tap can reveal a running automatic check without another request.
            if (!automatic && (state.value.checking || state.value.downloading)) {
                mutableState.update { it.copy(showSheet = true, automaticPrompt = false) }
            }
            return
        }
        if (!automatic) {
            // Present the native sheet immediately, before preferences or networking can suspend.
            mutableState.update { it.copy(showSheet = true, automaticPrompt = false,
                checking = true, checked = false, error = null) }
        }
        operation = viewModelScope.launch {
            ready.await()
            try {
                val prefs = preferences ?: repository.preferences().also { preferences = it }
                if (automatic && !prefs.checkDue(clock.millis())) return@launch
                mutableState.update { it.copy(checking = true, checked = false, error = null,
                    automaticPrompt = automatic || it.automaticPrompt) }
                val now = clock.millis()
                repository.recordAttempt(now)
                preferences = (preferences ?: prefs).copy(lastAttemptMillis = now)
                val release = repository.latest()?.takeIf {
                    it.version > (ReleaseVersion.parse(BuildConfig.VERSION_NAME.substringBefore('-'))
                        ?: error("Invalid build version"))
                }
                val notifyCurrent = release == null && state.value.showSheet && !state.value.automaticPrompt
                mutableState.update { previous ->
                    previous.copy(checking = false, checked = true, update = release,
                        apkUri = if (previous.update == release) previous.apkUri else null,
                        // Dismissing a pending manual request must never reopen its sheet.
                        showSheet = (previous.showSheet && release != null) || (automatic && previous.automaticPrompt && preferences?.automatic == true &&
                            release != null && prefs.skippedVersion != release.version.toString()))
                }
                if (notifyCurrent) notifyCurrentVersion()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update { it.copy(error = (error as? UpdateException)?.reason ?: UpdateError.STORAGE) }
            } finally {
                mutableState.update { it.copy(checking = false) }
            }
        }
    }

    fun setAutomatic(enabled: Boolean) {
        if (!state.value.loaded || state.value.savingPreference) return
        mutableState.update { it.copy(savingPreference = true) }
        viewModelScope.launch {
            try {
                val prefs = preferences ?: repository.preferences()
                repository.setAutomatic(enabled)
                preferences = (preferences ?: prefs).copy(automatic = enabled)
                mutableState.update { it.copy(automatic = enabled, error = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(error = UpdateError.STORAGE) }
                mutableFeedback.tryEmit(UpdateFeedback.Failure(UpdateError.STORAGE))
            } finally {
                mutableState.update { it.copy(savingPreference = false) }
            }
            if (enabled) check(automatic = true)
        }
    }

    internal fun notifyCurrentVersion() { mutableFeedback.tryEmit(UpdateFeedback.CurrentVersion) }

    fun showUpdate() { mutableState.update { it.copy(showSheet = true, automaticPrompt = false) } }
    fun dismiss() { mutableState.update { it.copy(showSheet = false, automaticPrompt = false) } }

    fun skip() {
        val update = state.value.update ?: return
        if (operation?.isActive == true) return
        operation = viewModelScope.launch {
            try {
                repository.skip(update.version.toString())
                preferences = preferences?.copy(skippedVersion = update.version.toString())
                dismiss()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(error = UpdateError.STORAGE) }
            }
        }
    }

    fun download() {
        val update = state.value.update ?: return
        if (operation?.isActive == true) return
        mutableState.update { it.copy(downloading = true, progress = 0f, error = null, apkUri = null) }
        operation = viewModelScope.launch {
            try {
                val uri = repository.download(update) { progress ->
                    mutableState.update { it.copy(progress = progress.coerceIn(0f, 1f)) }
                }
                mutableState.update { it.copy(apkUri = uri) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update { it.copy(error = (error as? UpdateException)?.reason ?: UpdateError.NETWORK) }
            } finally {
                mutableState.update { it.copy(downloading = false) }
            }
        }
    }

    fun cancelDownload() { if (state.value.downloading) operation?.cancel() }
    fun installerFailed() { mutableState.update { it.copy(error = UpdateError.INSTALLER) } }
}
