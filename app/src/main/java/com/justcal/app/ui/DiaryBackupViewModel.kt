package com.justcal.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justcal.app.data.backup.DiaryBackup
import com.justcal.app.data.backup.DiaryBackupRepository
import com.justcal.app.data.backup.DiaryImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DiaryBackupState(
    val busy: Boolean = false,
    val preview: DiaryBackup? = null,
    val result: DiaryImportResult? = null,
    val error: Boolean = false,
    val exported: Boolean = false,
    val recoveryAvailable: Boolean = false,
)

@HiltViewModel
class DiaryBackupViewModel @Inject constructor(
    private val repository: DiaryBackupRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DiaryBackupState())
    val state = mutableState.asStateFlow()
    init { refreshRecovery() }

    private fun refreshRecovery() {
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.update { it.copy(recoveryAvailable = repository.recoveryAvailable()) }
        }
    }

    fun dismissPreview() { if (!state.value.busy) mutableState.update { it.copy(preview = null) } }

    fun preview(uri: Uri) = work {
        val backup = requireNotNull(context.contentResolver.openInputStream(uri)).use { repository.preview(it) }
        mutableState.update { it.copy(preview = backup) }
    }

    fun export(uri: Uri, recovery: Boolean = false) = work {
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use {
            if (recovery) repository.exportRecovery(it) else repository.export(it)
        }
        mutableState.update { it.copy(exported = true) }
    }

    fun import(replaceAll: Boolean) {
        val backup = state.value.preview ?: return
        work {
            val result = repository.importBackup(backup, replaceAll)
            mutableState.update { it.copy(preview = null, result = result,
                recoveryAvailable = repository.recoveryAvailable()) }
        }
    }

    private fun work(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, error = false, result = null, exported = false) }
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(error = true) }
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }
}
