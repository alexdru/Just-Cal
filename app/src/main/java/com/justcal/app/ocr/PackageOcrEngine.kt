package com.justcal.app.ocr

import com.justcal.app.camera.VerifiedImageInput
import kotlinx.serialization.Serializable

@Serializable data class OcrPoint(val x: Float, val y: Float)
@Serializable data class OcrLine(val text: String, val confidence: Float, val points: List<OcrPoint>, val alternatives: List<String> = emptyList()) {
    val left: Float get() = points.minOfOrNull { it.x } ?: 0f
    val top: Float get() = points.minOfOrNull { it.y } ?: 0f
    val right: Float get() = points.maxOfOrNull { it.x } ?: 0f
    val bottom: Float get() = points.maxOfOrNull { it.y } ?: 0f
    val height: Float get() = (bottom - top).coerceAtLeast(1f)
}
@Serializable data class OcrMetrics(val initializationMs: Long, val detectionMs: Long, val recognitionMs: Long, val totalMs: Long)
/** Microseconds separate Kotlin/image preparation from synchronous native inference. */
@Serializable data class OcrRegionProfile(
    val index: Int,
    val stage: String,
    val width: Int,
    val cropUs: Long,
    val preparationUs: Long,
    val inferenceUs: Long,
    val decodingUs: Long,
    val text: String,
    val confidence: Float,
)
@Serializable data class PackageOcrResult(
    val lines: List<OcrLine>,
    val width: Int,
    val height: Int,
    val metrics: OcrMetrics,
    val regions: List<OcrRegionProfile> = emptyList(),
    val detectedRegions: Int = lines.size,
    val fullFallback: Boolean = false,
)

/** Application-owned types only. Implementations own tensors, native sessions and image pixels. */
interface PackageOcrEngine {
    suspend fun recognize(input: VerifiedImageInput): PackageOcrResult
}
