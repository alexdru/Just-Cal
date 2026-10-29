package com.justcal.app.ai

import android.graphics.Bitmap
import com.justcal.app.camera.*
import com.justcal.app.ocr.*
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlin.math.*

object OcrCoordinateTransform {
    fun toPrepared(point: OcrPoint, width: Int, height: Int, rotation: Int): OcrPoint = when (rotation) {
        0 -> point
        90 -> OcrPoint(point.y, height - point.x)
        180 -> OcrPoint(width - point.x, height - point.y)
        270 -> OcrPoint(width - point.y, point.x)
        else -> error("Unsupported OCR coordinate rotation")
    }
}

// Ingredient phrases such as "соль пищевая" are not nutrition headings. Match the
// beginning of a real heading, including the first line of a wrapped RU heading.
private val nutritionHeading = Regex(
    """^\s*(?:пищев[а-яё]*?\s*(?:ценност[а-яё]*|и\s*энергетическ[а-яё]*)|nutrition(?:al)?\s+(?:facts|information|values?)\b|nutrition\s*$)""",
    RegexOption.IGNORE_CASE,
)

internal fun findNutritionCropHeading(lines: List<OcrLine>): OcrLine? = lines.firstOrNull { (text, confidence) ->
    (confidence >= .8f) && nutritionHeading.containsMatchIn(text)
}

internal data class NutritionCropBlock(val heading: OcrLine, val lines: List<OcrLine>)

private val cropBoundary = Regex(
    "рекомендац|изготовител|хранить|хранен|состав|cooking|manufacturer|storage|ingredients",
    RegexOption.IGNORE_CASE,
)
private val portionHeading = Regex(
    """^\s*(?:вс[её]\s+блюдо|на\s+вс[её]\s+блюдо|в\s+одной\s+порци|per\s+serving|serving\s+size)""",
    RegexOption.IGNORE_CASE,
)
private val cropNutrient = Regex(
    "белк|жир|углевод|энерг|ккал|кдж|protein|fat|carb|energy|kcal|kj",
    RegexOption.IGNORE_CASE,
)

internal fun selectNutritionCropBlock(lines: List<OcrLine>): NutritionCropBlock? {
    val heading = findNutritionCropHeading(lines) ?: return null
    val end = (lines.asSequence().filter { (it.top > heading.bottom) && cropBoundary.containsMatchIn(it.text) }
        .minOfOrNull { it.top }) ?: (heading.bottom + heading.height * 8)
    val candidates = lines.filter {
        (it.top >= heading.top - heading.height * .2f) &&
            (it.top < minOf(end, heading.bottom + heading.height * 8))
    }
    // Remove only clearly separate columns identified by an explicit alternate-basis
    // heading. Never guess a split through an overlapping OCR box or a nutrient row.
    val separatePortions = candidates.filter {
        (it.confidence >= .8f) && portionHeading.containsMatchIn(it.text) &&
            ((it.left > heading.right + heading.height) || (it.right < heading.left - heading.height))
    }
    val inScope = candidates.filter { line ->
        separatePortions.all { portion ->
            if (portion.left > heading.right) line.right < portion.left
            else line.left > portion.right
        }
    }
    val lastNutrient = inScope.filter { cropNutrient.containsMatchIn(it.text) }.maxOfOrNull { it.bottom }
        ?: return null
    return NutritionCropBlock(heading, inScope.filter { it.top <= lastNutrient })
}

/** At most one bounded nutrition block; no tiling or guessed whole-label region. */
class LabelImageCropper @Inject constructor(private val images: ImagePreprocessor) {
    companion object {
        const val PROFILE_VERSION = "nutrition-crop-v2"
    }

    suspend fun crop(image: PreparedImage, result: PackageOcrResult): PreparedImage? {
        val produced = java.util.concurrent.atomic.AtomicReference<PreparedImage?>()
        try { return withContext(Dispatchers.IO) {
        val block = selectNutritionCropBlock(result.lines) ?: return@withContext null
        val heading = block.heading
        val points = block.lines.flatMap { it.points }.map {
            OcrCoordinateTransform.toPrepared(it, image.width, image.height, result.coordinateRotationDegrees)
        }
        if (points.isEmpty()) return@withContext null
        val margin = maxOf(16, (heading.height * .4f).toInt())
        val left = (floor(points.minOf { it.x }).toInt() - margin).coerceIn(0, image.width - 1)
        val top = (floor(points.minOf { it.y }).toInt() - margin).coerceIn(0, image.height - 1)
        val right = (ceil(points.maxOf { it.x }).toInt() + margin).coerceIn(left + 1, image.width)
        val bottom = (ceil(points.maxOf { it.y }).toInt() + margin).coerceIn(top + 1, image.height)
        val source = images.decode(image)
        try {
            val bitmap = Bitmap.createBitmap(source, left, top, right - left, bottom - top,
                android.graphics.Matrix().apply { postRotate(result.coordinateRotationDegrees.toFloat()) }, true)
            val file = images.newCaptureFile()
            try {
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
                images.prepare(android.net.Uri.fromFile(file), ImageSource.CAMERA, image.request).also { produced.set(it) }
            } catch (failure: Throwable) { file.delete(); throw failure }
            finally { if (bitmap !== source) bitmap.recycle() }
        } finally { source.recycle() }
        } } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { produced.get()?.let { images.discard(it.uri) } }
            throw failure
        }
    }
}
