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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class PackageReviewState(val busy: Boolean = false, val failed: Boolean = false, val prefill: PackageScanPrefill? = null,
    val recognition: com.justcal.app.domain.RecognitionDraft? = null, val processingModel: String? = null)

data class PackageAssistanceModel(val loading: Boolean = true, val name: String? = null)

@HiltViewModel
class PhotoReviewViewModel @Inject constructor(
    val images: ImagePreprocessor,
    private val router: com.justcal.app.ai.FoodRecognitionRouter,
    private val savedState: SavedStateHandle,
    private val diagnostics: OcrDiagnosticSession,
    preferences: com.justcal.app.data.GoalPreferences,
) : ViewModel() {
    private var owned: PreparedImage? = null
    private var operation: Job? = null
    private var closing = false
    private val restored = savedState.get<String>("ocrReview")?.let {
        runCatching { Json.decodeFromString<PackageScanPrefill>(it) }.getOrNull()
    }
    private val restoredMeal = savedState.get<String>("recognitionReview")?.let {
        runCatching { Json.decodeFromString<com.justcal.app.domain.RecognitionDraft>(it) }.getOrNull()
    }
    private val mutableState = MutableStateFlow(PackageReviewState(
        failed = savedState.get<Boolean>("ocrStarted") == true && restored == null,
        prefill = restored, recognition = restoredMeal,
    ))
    val state = mutableState.asStateFlow()
    val packageAssistanceEnabled = savedState.getStateFlow("packageAssistanceEnabled", true)
    val packageModel = preferences.aiModels.distinctUntilChanged().map { choices ->
        PackageAssistanceModel(loading = false, name = router.packageModelName(choices["package"]))
    }.catch { emit(PackageAssistanceModel(loading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PackageAssistanceModel())

    fun setPackageAssistance(enabled: Boolean) {
        if (!closing && !state.value.busy) savedState["packageAssistanceEnabled"] = enabled
    }
    fun attach(image: PreparedImage) { owned = image }

    fun recognize(image: PreparedImage) {
        if (closing || state.value.busy || state.value.prefill != null || state.value.recognition != null) return
        if (image.request.mode == ScanMode.MEAL) {
            mutableState.value = PackageReviewState(busy = true)
            val attempt = diagnostics.begin(image)
            operation = viewModelScope.launch {
                try {
                    val result = router.meal(image)
                    val review = result.review
                    attempt.ai(com.justcal.app.diagnostics.DiagnosticAiAttempt(
                        stage = "MEAL_NAME", artifactHash = result.validation.sha256,
                        revision = result.descriptor.revision, runtimeVersion = com.justcal.app.ai.AI_RUNTIME_VERSION,
                        profile = result.descriptor.profile.name + "/" + com.justcal.app.ai.MealRecognition.PROFILE_VERSION, requestedBackend = "AUTO",
                        actualBackend = result.metrics.backend.name, backendAttempts = result.metrics.backendAttempts,
                        fallbackReason = result.metrics.fallbackReason, rawOutput = review.raw,
                        validationError = review.failure,
                        initializationMs = result.metrics.initializationMillis,
                        inferenceMs = result.metrics.inferenceMillis, firstResponseMs = result.metrics.firstResponseMillis),
                        status = if (review.draft == null) "FAILURE" else "PARTIAL", origin = "MEAL_SCAN")
                    val draft = review.draft?.copy(diagnosticAttemptId = attempt.id) ?: run {
                        mutableState.value = PackageReviewState(failed = true)
                        return@launch
                    }
                    savedState["recognitionReview"] = Json.encodeToString(draft)
                    mutableState.value = PackageReviewState(recognition = draft)
                }
                catch (cancelled: CancellationException) { attempt.failure(true, "MEAL_CANCELLED"); throw cancelled }
                catch (failure: Exception) {
                    val reason = failure.message?.takeIf { it in setOf(
                        "No verified meal model selected", "Selected model unavailable",
                        "Meal model has not passed image quality validation",
                        "Test this model with an image in Playground first", "AI runtime is busy",
                        "Insufficient available memory", "Unexpected meal fields",
                        "Uncertain meal must not supply a name", "Meal response exceeded budget",
                    ) } ?: failure.javaClass.simpleName
                    android.util.Log.w("JustCalAI", "Meal recognition failed: $reason")
                    attempt.failure(false, "MEAL_UNAVAILABLE:${failure.javaClass.simpleName}")
                    mutableState.value = PackageReviewState(failed = true)
                }
                catch (_: LinkageError) { attempt.failure(false, "MEAL_RUNTIME_UNAVAILABLE"); mutableState.value = PackageReviewState(failed = true) }
            }
            return
        }
        val allowModel = packageAssistanceEnabled.value
        mutableState.value = PackageReviewState(busy = true)
        savedState["ocrStarted"] = true
        val attempt = diagnostics.begin(image)
        operation = viewModelScope.launch {
            var stage = "OCR_RECOGNITION_FAILURE"
            try {
                val (result, routed) = router.packageOcr(image, allowModel = allowModel) { name ->
                    mutableState.update { it.copy(processingModel = name) }
                }
                check(result.lines.isNotEmpty()) { "No readable text found" }
                stage = "NUTRITION_PARSER_FAILURE"
                val parseStarted = SystemClock.elapsedRealtime()
                val prefill = routed.copy(diagnosticSessionId = attempt.id)
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

    fun closeReview() {
        if (closing) return
        closing = true
        operation?.cancel()
        val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        cleanup.launch {
            try { operation?.join() }
            finally { owned?.let { images.discard(it.uri) }; cleanup.cancel() }
        }
    }
    override fun onCleared() { closeReview() }
}
