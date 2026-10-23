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

/** At most one bounded nutrition block; no tiling or guessed whole-label region. */
class LabelImageCropper @Inject constructor(private val images: ImagePreprocessor) {
    suspend fun crop(image: PreparedImage, result: PackageOcrResult): PreparedImage? {
        val produced = java.util.concurrent.atomic.AtomicReference<PreparedImage?>()
        try { return withContext(Dispatchers.IO) {
        val heading = result.lines.firstOrNull {
            it.confidence >= .8f && Regex("пищев|nutrition", RegexOption.IGNORE_CASE).containsMatchIn(it.text)
        } ?: return@withContext null
        val end = result.lines.filter { it.top > heading.bottom &&
            Regex("рекомендац|изготовител|хранить|cooking|manufacturer|storage", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
            .minOfOrNull { it.top } ?: (heading.bottom + heading.height * 8)
        val candidates = result.lines.filter { it.bottom >= heading.top &&
            it.top < minOf(end, heading.bottom + heading.height * 8) }
        val nutrition = Regex("белк|жир|углевод|энерг|ккал|кдж|protein|fat|carb|energy|kcal|kj", RegexOption.IGNORE_CASE)
        val lastNutrient = candidates.filter { nutrition.containsMatchIn(it.text) }.maxOfOrNull { it.bottom }
            ?: return@withContext null
        val lines = candidates.filter { it.top <= lastNutrient }
        val points = lines.flatMap { it.points }.map {
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
