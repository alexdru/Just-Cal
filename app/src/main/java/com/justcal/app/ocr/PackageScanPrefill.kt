package com.justcal.app.ocr

import kotlinx.serialization.Serializable

/** Bounded textual review snapshot; never includes image pixels or native handles. */
@Serializable data class PackageScanPrefill(
    val label: ParsedNutritionLabel,
    val rawText: String,
    val confidence: Float,
    val metrics: OcrMetrics,
) {
    companion object {
        fun from(result: PackageOcrResult) = PackageScanPrefill(
            NutritionLabelParser.parse(result),
            result.lines.joinToString("\n") { (listOf(it.text) + it.alternatives).joinToString("\n") }.take(16_000),
            result.lines.map { it.confidence }.average().takeIf { it.isFinite() }?.toFloat() ?: 0f,
            result.metrics,
        )
    }
}
