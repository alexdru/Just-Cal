package com.justcal.app.ai

import com.justcal.app.camera.PreparedImage

enum class AiBackend { CPU, GPU }

data class LocalModel(val id: String, val name: String, val sizeBytes: Long)

/** App-facing values only. Native/runtime types stay inside the adapter. */
data class AiMetrics(
    val backend: AiBackend,
    val initializationMillis: Long,
    val inferenceMillis: Long,
    val firstResponseMillis: Long?,
    val nativeFirstTokenSeconds: Double? = null,
    val prefillTokensPerSecond: Double? = null,
    val decodeTokensPerSecond: Double? = null,
    val benchmarkError: String? = null,
)

interface LocalAiEngine {
    suspend fun load(model: LocalModel, backend: AiBackend): Long
    suspend fun generate(
        image: PreparedImage, prompt: String, structured: Boolean,
        onChunk: suspend (String) -> Unit,
    ): AiMetrics
    suspend fun unload()
}
