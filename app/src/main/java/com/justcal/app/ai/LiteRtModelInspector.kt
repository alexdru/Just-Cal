package com.justcal.app.ai

import com.google.ai.edge.litertlm.ModelInfo
import com.google.ai.edge.litertlm.Modality
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** JNI metadata is converted to app-owned values and closed before engine construction. */
class LiteRtModelInspector @Inject constructor() {
    suspend fun inspect(file: File, expectedHash: String? = null): ModelValidation = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        require(expectedHash == null || hash == expectedHash) { "Model integrity check failed" }
        val capabilities = try {
            ModelInfo.from(file.absolutePath).use { info ->
                require(info is ModelInfo.Llm) { "Not a language model" }
                ModelCapabilities(
                    info.inputModalities().vision, info.maxContextTokens(), info.isDynamicContext(),
                    info.minRuntimeVersion(), info.supportedBackends(Modality.TEXT).map { it.name },
                    info.supportedBackends(Modality.VISION).map { it.name },
                )
            }
        } catch (failure: Exception) {
            // Native errors can contain private paths. Do not expose them in consumer UI/logs.
            throw IllegalArgumentException("Unsupported or damaged LiteRT-LM container", failure)
        }
        require(capabilities.vision) { "This model has no image input" }
        require(capabilities.contextTokens > 0) { "Unknown model context budget" }
        require(BackendSelector.runtimeSupported(capabilities.minimumRuntime)) { "Model requires a newer runtime" }
        require(BackendSelector.candidates(capabilities).isNotEmpty()) { "No supported image backend pair" }
        ModelValidation(hash, capabilities)
    }
}
