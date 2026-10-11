package com.justcal.app.diagnostics

import kotlinx.serialization.Serializable
import kotlin.math.ceil

@Serializable data class DiagnosticCorrection(val field: String, val category: String, val before: String? = null, val after: String? = null)
@Serializable data class DiagnosticScan(
    val id: String,
    val timestampMillis: Long,
    val appVersion: String = "",
    val androidVersion: String = "",
    val device: String = "",
    val model: String = "PP-OCRv5 mobile det + eslav mobile rec",
    val runtime: String = "ONNX Runtime 1.23.2 / OpenCV 4.12.0 CPU",
    val modelRevisions: Map<String, String> = mapOf(
        "detection" to "e6f4fa85f00e168c862bc462aebca69eef9b3d3d",
        "recognition" to "9a32171fc5718746875e1a261818884517975013"),
    val modelHashes: Map<String, String> = mapOf(
        "detectionSha256" to "a431985659dc921974177a95adcfbb90fd9e51989a5e04d70d0b75f597b6e61d",
        "recognitionSha256" to "b3018ef2b09a0250b6e0c8e871c927098363e5fd4df890cc68e8358eb0aaf1bd"),
    val orientation: String = "EXIF normalized upright",
    val status: String,
    val totalMs: Long,
    val initializationMs: Long? = null,
    val detectionMs: Long? = null,
    val recognitionMs: Long? = null,
    val parsingMs: Long? = null,
    val width: Int = 0,
    val height: Int = 0,
    val detectedRegions: Int? = null,
    val confidenceMean: Float? = null,
    val confidenceMin: Float? = null,
    val confidenceMax: Float? = null,
    val missingFields: List<String> = emptyList(),
    val ambiguousFields: List<String> = emptyList(),
    val correctedFields: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val productName: String? = null,
    val brand: String? = null,
    val rawText: String? = null,
    val parsedFields: Map<String, String> = emptyMap(),
    val corrections: List<DiagnosticCorrection> = emptyList(),
    val detailedConsent: Boolean = false,
) {
    fun sanitized(includeRawText: Boolean = false): DiagnosticScan =
        if (includeRawText && detailedConsent) this else copy(
            productName = null, rawText = null, parsedFields = emptyMap(),
            corrections = corrections.map { it.copy(before = null, after = null) },
        )
}

@Serializable data class BrandStatistics(val brand: String, val scans: Int, val correctedScans: Int, val eligibleCorrectionScans: Int)
data class DiagnosticStatistics(
    val total: Int, val successful: Int, val failed: Int, val averageMs: Double?,
    val medianMs: Double?, val p95Ms: Long?, val slowest: List<DiagnosticScan>,
    val correctionsByField: Map<String, Int>, val failures: Map<String, Int>,
    val brands: List<BrandStatistics>,
) {
    companion object {
        fun from(scans: List<DiagnosticScan>): DiagnosticStatistics {
            val times = scans.filter { it.status !in listOf("RUNNING", "INTERRUPTED") }.map { it.totalMs }.sorted()
            return DiagnosticStatistics(scans.size,
                scans.count { it.status == "SUCCESS" || it.status == "PARTIAL" },
                scans.count { it.status == "FAILURE" },
                times.takeIf { it.isNotEmpty() }?.average(),
                times.takeIf { it.isNotEmpty() }?.let { it[(it.size - 1) / 2] / 2.0 + it[it.size / 2] / 2.0 },
                times.takeIf { it.isNotEmpty() }?.get((ceil(times.size * .95).toInt() - 1).coerceAtLeast(0)),
                scans.filter { it.status !in listOf("RUNNING", "INTERRUPTED") }.sortedByDescending { it.totalMs }.take(10),
                scans.filter { it.detailedConsent && it.status in listOf("SUCCESS", "PARTIAL") }.flatMap { it.correctedFields }.groupingBy { it }.eachCount(),
                scans.flatMap { it.errors }.groupingBy { it }.eachCount(),
                scans.groupBy { it.brand ?: "Unknown" }.map { (brand, rows) ->
                    BrandStatistics(brand, rows.size, rows.count { it.detailedConsent && it.status in listOf("SUCCESS", "PARTIAL") && it.correctedFields.isNotEmpty() }, rows.count { it.detailedConsent && it.status in listOf("SUCCESS", "PARTIAL") })
                }.sortedByDescending { it.scans })
        }
    }
}

/** Only explicit packaging attribution is accepted; arbitrary food titles are never brands. */
object PackagingBrand {
    private val attribution = Regex("(?:изготовитель|производитель|торговая марка|бренд|manufacturer|produced by|brand|retailer)\\s*[:：]\\s*([^\\n]{2,80})", RegexOption.IGNORE_CASE)
    fun extract(rawText: String): String? = attribution.find(rawText)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
}
