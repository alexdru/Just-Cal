package com.justcal.app.camera

import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlin.math.sqrt

@Serializable enum class ScanMode { PACKAGE, MEAL }
@Serializable enum class ImageSource { CAMERA, PHOTO_PICKER }

@Serializable
data class ScanRequest(val mode: ScanMode, val dayEpoch: Long)

/** Private image reference. Dimensions describe upright pixels returned by the decoder. */
@Serializable
data class PreparedImage(
    val uri: String,
    val source: ImageSource,
    val request: ScanRequest,
    val width: Int,
    val height: Int,
    val mimeType: String,
)

/** Created only after the user reviews the prepared image. No nutrition is inferred. */
data class VerifiedImageInput(val image: PreparedImage)

data class ImageSize(val width: Int, val height: Int)

/** Resource budget, not a model input shape. Keeps label detail without decoding a 48/200 MP photo. */
object ImageDecodeBudget {
    const val MAX_EDGE = 4096
    const val MAX_PIXELS = 12_000_000L
    const val MAX_INPUT_BYTES = 64L * 1024 * 1024

    fun fit(width: Int, height: Int, maxEdge: Int = MAX_EDGE, maxPixels: Long = MAX_PIXELS): ImageSize {
        require(width > 0 && height > 0 && maxEdge > 0 && maxPixels > 0)
        val scale = min(1.0, min(maxEdge.toDouble() / maxOf(width, height),
            sqrt(maxPixels.toDouble() / (width.toLong() * height))))
        return ImageSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
    }
}
