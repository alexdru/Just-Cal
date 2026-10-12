package com.justcal.app.ui

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justcal.app.camera.*
import com.justcal.app.ocr.*
import com.justcal.app.diagnostics.OcrDiagnosticSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class PackageReviewState(val busy: Boolean = false, val failed: Boolean = false, val prefill: PackageScanPrefill? = null)

@HiltViewModel
class PhotoReviewViewModel @Inject constructor(
    val images: ImagePreprocessor,
    private val engine: PackageOcrEngine,
    private val savedState: SavedStateHandle,
    private val diagnostics: OcrDiagnosticSession,
) : ViewModel() {
    private var owned: PreparedImage? = null
    private val restored = savedState.get<String>("ocrReview")?.let {
        runCatching { Json.decodeFromString<PackageScanPrefill>(it) }.getOrNull()
    }
    private val mutableState = MutableStateFlow(PackageReviewState(
        failed = savedState.get<Boolean>("ocrStarted") == true && restored == null,
        prefill = restored,
    ))
    val state = mutableState.asStateFlow()
    fun attach(image: PreparedImage) { owned = image }

    fun recognize(image: PreparedImage) {
        if (state.value.busy || state.value.prefill != null || image.request.mode != ScanMode.PACKAGE) return
        mutableState.value = PackageReviewState(busy = true)
        savedState["ocrStarted"] = true
        val attempt = diagnostics.begin(image)
        viewModelScope.launch {
            var stage = "OCR_RECOGNITION_FAILURE"
            try {
                val result = engine.recognize(images.verify(image))
                check(result.lines.isNotEmpty()) { "No readable text found" }
                stage = "NUTRITION_PARSER_FAILURE"
                val parseStarted = SystemClock.elapsedRealtime()
                val prefill = PackageScanPrefill.from(result).copy(diagnosticSessionId = attempt.id)
                attempt.success(result, prefill, SystemClock.elapsedRealtime() - parseStarted)
                savedState["ocrReview"] = Json.encodeToString(prefill)
                savedState["ocrStarted"] = false
                mutableState.value = PackageReviewState(prefill = prefill)
            } catch (cancelled: CancellationException) {
                attempt.failure(true, "RECOGNITION_CANCELLED")
                throw cancelled
            } catch (error: LinkageError) {
                attempt.failure(false, "OCR_RUNTIME_LINKAGE_FAILURE:${error.javaClass.simpleName.take(80)}")
                mutableState.value = PackageReviewState(failed = true)
            } catch (error: Exception) {
                attempt.failure(false, "$stage:${error.javaClass.simpleName.take(80)}")
                // Native exceptions may contain model/private paths; never send them to Logcat.
                mutableState.value = PackageReviewState(failed = true)
            }
        }
    }

    override fun onCleared() {
        // The engine finishes its native call and closes its own resources before returning.
        owned?.let { images.discard(it.uri) }
    }
}
