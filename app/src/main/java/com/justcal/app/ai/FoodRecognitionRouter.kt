package com.justcal.app.ai

import kotlin.time.Duration.Companion.seconds

import com.justcal.app.camera.*
import com.justcal.app.data.GoalPreferences
import com.justcal.app.ocr.*
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Optional VLM work never discards a completed OCR result on its own deadline/failure. */
internal suspend fun packageEnrichmentOrBaseline(
    baseline: PackageScanPrefill,
    enrich: suspend () -> PackageScanPrefill,
): PackageScanPrefill = try {
    enrich()
} catch (_: TimeoutCancellationException) {
    // An inner VLM deadline is recoverable; cancellation of the caller is not.
    currentCoroutineContext().ensureActive()
    baseline
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    currentCoroutineContext().ensureActive()
    baseline
} catch (_: LinkageError) {
    currentCoroutineContext().ensureActive()
    baseline
}

data class MealRecognitionResult(
    val review: MealRecognition.Review,
    val validation: ModelValidation,
    val descriptor: ModelDescriptor,
    val metrics: AiMetrics,
)

/** Production admission is explicit per artifact. Playground success never enables a task. */
class FoodRecognitionRouter @Inject constructor(
    private val ocr: PackageOcrEngine, private val images: ImagePreprocessor,
    private val store: LocalModelStore, private val catalog: ModelCatalog,
    private val preferences: GoalPreferences, private val runtime: LiteRtLocalAiEngine,
    private val cropper: LabelImageCropper,
    private val packageWorker: PackageInferenceClient,
) {
    /** UI availability uses the same task/metadata checks as production, without loading a model. */
    suspend fun packageModelName(id: String?): String? = validatedPackageModel(id)?.second?.name

    private suspend fun validatedPackageModel(id: String?): Pair<LocalModel, ModelDescriptor>? {
        if (id == null) return null
        val model = store.list().firstOrNull { (modelId) -> modelId == id } ?: return null
        val validation = try { store.validate(model) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return null }
            catch (_: LinkageError) { return null }
        val descriptor = catalog.matching(validation.sha256)
        if (descriptor?.supportsRecognition("package", validation) != true) return null
        return model to descriptor
    }

    suspend fun packageOcr(
        image: PreparedImage,
        allowModel: Boolean = true,
        onModelProcessing: (String) -> Unit = {},
        onBaseline: suspend (PackageOcrResult, PackageScanPrefill, Long) -> Unit = { _, _, _ -> },
        onDiagnostic: suspend (com.justcal.app.diagnostics.DiagnosticAiAttempt) -> Unit = {},
    ): Pair<PackageOcrResult, PackageScanPrefill> {
        val result = ocr.recognize(images.verify(image))
        val parseStarted = System.nanoTime()
        val baseline = PackageScanPrefill.from(result)
        val parsingMs = (System.nanoTime() - parseStarted) / 1_000_000
        onBaseline(result, baseline, parsingMs)
        // Opting out bypasses all optional model lookup, validation, loading and inference.
        if (!allowModel || LabelRegionSelection.complete(result)) return result to baseline
        val (model, descriptor) = validatedPackageModel(preferences.aiModels.first()["package"])
            ?: return result to baseline
        val macroTask = descriptor.profile == ModelTaskProfile.GENERAL_VISION
        if (macroTask && !PackageMacroAssistance.shouldRun(baseline.label)) return result to baseline
        val enriched = packageEnrichmentOrBaseline(baseline) {
            var crop: PreparedImage? = null
            try {
                crop = cropper.crop(image, result) ?: return@packageEnrichmentOrBaseline baseline
                onModelProcessing(descriptor.name)
                var latest = com.justcal.app.diagnostics.DiagnosticAiAttempt(
                    stage = "PACKAGE_MODEL_STARTING", artifactHash = descriptor.sha256,
                    revision = descriptor.revision, runtimeVersion = AI_RUNTIME_VERSION,
                    profile = descriptor.profile.name, requestedBackend = "AUTO")
                onDiagnostic(latest)
                val completed = try {
                    packageWorker.generate(model, crop) { event ->
                        latest = event
                        onDiagnostic(event)
                    }
                } catch (deadline: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    onDiagnostic(latest.copy(validationError = "PACKAGE_MODEL_DEADLINE"))
                    throw deadline
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    onDiagnostic(latest.copy(validationError = if (failure is PackageWorkerDied)
                        "PACKAGE_MODEL_PROCESS_DIED" else "PACKAGE_MODEL_FAILED:${failure.javaClass.simpleName}",
                        processId = (failure as? PackageWorkerDied)?.pid ?: latest.processId))
                    throw failure
                }
                onDiagnostic(completed)
                val raw = completed.rawOutput.orEmpty()
                val recognition = try {
                    if (macroTask) PackageMacroAssistance.merge(baseline.label, raw)
                    else TranscriptionNutrition.merge(baseline.label, raw)
                } catch (failure: Exception) {
                    onDiagnostic(completed.copy(validationError = "PACKAGE_MODEL_OUTPUT_REJECTED:${failure.javaClass.simpleName}"))
                    throw failure
                }
                baseline.copy(recognition = recognition)
            } finally {
                withContext(NonCancellable) {
                    crop?.let { images.discard(it.uri) }
                }
            }
        }
        return result to enriched
    }

    suspend fun meal(image: PreparedImage): MealRecognitionResult {
        val id = preferences.aiModels.first()["meal"] ?: error("No verified meal model selected")
        val model = store.list().firstOrNull { it.id == id } ?: error("Selected model unavailable")
        val validation = store.validate(model)
        val descriptor = catalog.matching(validation.sha256)
        require(descriptor?.admitsMealName() == true) { "Meal model has not passed image quality validation" }
        require(descriptor.supportsRecognition("meal", validation)) {
            "Selected model is incompatible with image recognition"
        }
        val raw = StringBuilder()
        val metrics: AiMetrics
        try {
            runtime.load(model, AiBackend.AUTO)
            metrics = withTimeout(90.seconds) {
                runtime.generate(image,
                    MealRecognition.PROMPT,
                    false) {
                    require(raw.length + it.length <= 2048) { "Meal response exceeded budget" }
                    raw.append(it)
                }
            }
        } finally { withContext(NonCancellable) { runtime.unload() } }
        return MealRecognitionResult(MealRecognition.review(raw.toString()), validation, descriptor, metrics)
    }
}
