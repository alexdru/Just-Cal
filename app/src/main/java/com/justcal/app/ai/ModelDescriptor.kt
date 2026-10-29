package com.justcal.app.ai

import kotlinx.serialization.Serializable

const val AI_RUNTIME_VERSION = "0.18.1"

@Serializable
enum class ModelTaskProfile { GENERAL_VISION, LABEL_TRANSCRIPTION, CUSTOM }

@Serializable
data class ModelDescriptor(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val repository: String,
    val revision: String,
    val filename: String,
    val bytes: Long,
    val sha256: String,
    val license: String,
    val licenseUrl: String,
    val profile: ModelTaskProfile,
    val contextTokens: Int,
    val outputTokens: Int,
    // An artifact cannot retain production approval across runtime/task changes.
    val packageAdmission: TaskQualityAdmission? = null,
    val mealNameAdmission: TaskQualityAdmission? = null,
) {
    val url get() = "https://huggingface.co/$repository/resolve/$revision/$filename"

    fun admitsPackage(): Boolean {
        val task = when (profile) {
            ModelTaskProfile.LABEL_TRANSCRIPTION -> TranscriptionNutrition.PROFILE_VERSION
            ModelTaskProfile.GENERAL_VISION -> PackageMacroAssistance.PROFILE_VERSION
            ModelTaskProfile.CUSTOM -> return false
        }
        return packageAdmission?.matches(sha256, AI_RUNTIME_VERSION, task) == true
    }

    fun admitsMealName() = profile == ModelTaskProfile.GENERAL_VISION &&
        mealNameAdmission?.matches(sha256, AI_RUNTIME_VERSION, MealRecognition.PROFILE_VERSION) == true

    /** A fresh install can be selected before its first user-requested image run.
     * Device image probes are runtime evidence, never a prerequisite or task-quality admission.
     */
    fun supportsRecognition(task: String, validation: ModelValidation): Boolean {
        val admitted = when (task) {
            "package" -> admitsPackage()
            "meal" -> admitsMealName()
            else -> false
        }
        val capabilities = validation.capabilities
        return admitted && (validation.sha256 == sha256) && (validation.runtime == AI_RUNTIME_VERSION) &&
            capabilities.vision && (capabilities.contextTokens > 0) &&
            (capabilities.dynamicContext || (capabilities.contextTokens <= 4096)) &&
            BackendSelector.runtimeSupported(capabilities.minimumRuntime) &&
            BackendSelector.candidates(capabilities).isNotEmpty()
    }
}

/** Reviewed evidence is separate from container compatibility and a local image smoke. */
@Serializable
data class TaskQualityAdmission(
    val artifactHash: String,
    val runtimeVersion: String,
    val taskVersion: String,
    val evidence: String,
) {
    fun matches(hash: String, runtime: String, task: String) =
        artifactHash == hash && runtimeVersion == runtime && taskVersion == task && evidence.isNotBlank()
}

@Serializable
data class ModelCapabilities(
    val vision: Boolean,
    val contextTokens: Int,
    val dynamicContext: Boolean,
    val minimumRuntime: String?,
    val textBackends: List<String>,
    val visionBackends: List<String>,
)

@Serializable
data class ModelValidation(
    val sha256: String,
    val capabilities: ModelCapabilities,
    val catalogId: String? = null,
    val runtime: String = AI_RUNTIME_VERSION,
    val imageProbe: String? = null,
)

object BackendSelector {
    fun candidates(capabilities: ModelCapabilities): List<AiBackend> =
        listOf(AiBackend.GPU, AiBackend.CPU).filter {
            it.name in capabilities.textBackends && it.name in capabilities.visionBackends
        }

    fun runtimeSupported(required: String?): Boolean {
        if (required == null) return true
        val parsed = required.removePrefix("v").split('.').map { it.toIntOrNull() ?: return false }
        if (parsed.size != 3) return false
        val current = AI_RUNTIME_VERSION.split('.').map(String::toInt)
        for (i in current.indices) {
            if (parsed[i] != current[i]) return parsed[i] < current[i]
        }
        return true
    }

    fun mayFallback(failure: Throwable): Boolean {
        if (failure is kotlinx.coroutines.CancellationException || failure is OutOfMemoryError) return false
        val message = failure.message.orEmpty().lowercase()
        if (listOf("memory", "allocat", "malformed", "invalid", "magic", "corrupt").any(message::contains)) return false
        return listOf("delegate", "opencl", "gpu", "backend", "accelerat").any(message::contains)
    }
}
