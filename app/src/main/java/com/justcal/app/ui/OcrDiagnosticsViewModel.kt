package com.justcal.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justcal.app.diagnostics.DiagnosticReportExporter
import com.justcal.app.diagnostics.OcrDiagnosticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class OcrDiagnosticsViewModel @Inject constructor(
    private val repository: OcrDiagnosticsRepository,
    private val exporter: DiagnosticReportExporter,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val scans = repository.scans
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow(false)
    val error = mutableError.asStateFlow()
    private val mutableReport = MutableStateFlow<File?>(null)
    val report = mutableReport.asStateFlow()
    private val mutableSaved = MutableStateFlow(false)
    val saved = mutableSaved.asStateFlow()

    fun clear() = operation { repository.clear() }
    fun setBrand(id: String, brand: String) = operation { repository.setBrand(id, brand.trim().ifBlank { null }) }
    fun correctNetWeight(id: String, value: String) = operation { repository.correctNetWeight(id, value) }
    fun export(ids: Set<String>?, includeRawText: Boolean, photos: List<Uri>, includeAttachments: Boolean) = operation {
        mutableReport.value = exporter.export(ids, includeRawText, photos, includeAttachments)
    }
    fun reportHandled() { mutableReport.value = null }
    fun reportFailed() { mutableError.value = true; mutableReport.value = null }
    fun saveReport(file: File, uri: Uri) = operation {
        withContext(Dispatchers.IO) {
            val output = requireNotNull(context.contentResolver.openOutputStream(uri, "wt"))
            output.use { destination -> file.inputStream().use { it.copyTo(destination) } }
        }
        mutableSaved.value = true
        mutableReport.value = null
    }
    private fun operation(block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableError.value = false
        mutableSaved.value = false
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableError.value = true }
            finally { mutableBusy.value = false }
        }
    }
}
