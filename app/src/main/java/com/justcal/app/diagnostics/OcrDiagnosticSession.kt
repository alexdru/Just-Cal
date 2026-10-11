package com.justcal.app.diagnostics

import android.os.SystemClock
import com.justcal.app.camera.PreparedImage
import com.justcal.app.ocr.PackageOcrResult
import com.justcal.app.ocr.PackageScanPrefill
import java.util.UUID
import javax.inject.Inject

/** Per-attempt monotonic measurements; no OCR preprocessing or nutrition arithmetic changes. */
class OcrDiagnosticSession @Inject constructor(private val repository: OcrDiagnosticsRepository) {
    fun begin(image: PreparedImage) = Attempt(repository, image, repository.detailed.value)
    class Attempt(private val repository: OcrDiagnosticsRepository, private val image: PreparedImage, private val detailed: Boolean) {
        val id: String = UUID.randomUUID().toString()
        private val generation = repository.generation
        private val consentGeneration = repository.consentGeneration
        private val timestamp = System.currentTimeMillis()
        private val started = SystemClock.elapsedRealtime()
        init {
            repository.record(repository.metadata(id, timestamp, "RUNNING", elapsed()).copy(
                width = image.width, height = image.height, detailedConsent = detailed), generation, consentGeneration)
        }
        fun success(result: PackageOcrResult, prefill: PackageScanPrefill, parsingMs: Long) {
            val label = prefill.label
            val draft = label.draft()
            val fields = mapOf("NAME" to draft.name, "NET_WEIGHT" to draft.amount,
                "ENERGY" to draft.energy, "PROTEIN" to draft.protein, "FAT" to draft.fat, "CARBS" to draft.carbs)
            val missing = fields.filterValues { it.isBlank() }.keys.toList()
            val uncertain = mapOf("NAME" to label.name, "NET_WEIGHT" to label.netGrams,
                "ENERGY" to label.energyKcal, "PROTEIN" to label.proteinGrams, "FAT" to label.fatGrams, "CARBS" to label.carbsGrams)
                .filterValues { it != null && !it.confident }.keys.toList()
            val ambiguous = uncertain + if (label.basis.name in listOf("AMBIGUOUS", "PER_SERVING", "UNKNOWN")) listOf("NUTRITION_BASIS") else emptyList()
            val confidence = result.lines.map { it.confidence }.filter { it.isFinite() }
            repository.record(repository.metadata(id, timestamp, if (missing.isEmpty() && ambiguous.isEmpty()) "SUCCESS" else "PARTIAL", elapsed()).copy(
                initializationMs = result.metrics.initializationMs, detectionMs = result.metrics.detectionMs,
                recognitionMs = result.metrics.recognitionMs, parsingMs = parsingMs,
                width = result.width, height = result.height, detectedRegions = result.detectedRegions,
                confidenceMean = confidence.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
                confidenceMin = confidence.minOrNull(), confidenceMax = confidence.maxOrNull(),
                missingFields = missing, ambiguousFields = ambiguous,
                errors = if (ambiguous.isNotEmpty()) listOf("PARSER_UNSUPPORTED_OR_AMBIGUOUS_BASIS") else if (missing.isNotEmpty()) listOf("MISSING_OR_UNCERTAIN_FIELDS") else emptyList(),
                productName = draft.name.takeIf { detailed && it.isNotBlank() },
                brand = if (detailed) PackagingBrand.extract(prefill.rawText) else null,
                rawText = prefill.rawText.takeIf { detailed }, parsedFields = if (detailed) fields else emptyMap(),
                detailedConsent = detailed), generation, consentGeneration)
        }
        fun failure(cancelled: Boolean, category: String) {
            repository.record(repository.metadata(id, timestamp, if (cancelled) "CANCELLED" else "FAILURE", elapsed()).copy(
                width = image.width, height = image.height, errors = listOf(category), detailedConsent = detailed), generation, consentGeneration)
        }
        private fun elapsed() = SystemClock.elapsedRealtime() - started
    }
}
