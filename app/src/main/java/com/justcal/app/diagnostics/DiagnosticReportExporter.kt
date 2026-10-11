package com.justcal.app.diagnostics

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class DiagnosticReportSummary(
    val schemaVersion: Int = 1,
    val createdAtMillis: Long,
    val scanCount: Int,
    val timingSampleCount: Int,
    val eligibleCorrectionScans: Int,
    val successful: Int,
    val failed: Int,
    val cancelled: Int,
    val interrupted: Int,
    val correctionsByField: Map<String, Int>,
    val failureCategories: Map<String, Int>,
    val brandStatistics: List<BrandStatistics>,
    val averageMs: Double?,
    val medianMs: Double?,
    val p95Ms: Long?,
    val models: List<String>,
    val runtimes: List<String>,
    val rawTextIncluded: Boolean,
    val selectedPhotoCount: Int,
)
@Serializable data class ScanCorrections(val scanId: String, val corrections: List<DiagnosticCorrection>)
@javax.inject.Singleton class DiagnosticReportExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: OcrDiagnosticsRepository,
) {
    private val reportJson = Json { encodeDefaults = true }
    suspend fun export(ids: Set<String>? = null, includeRawText: Boolean = false, photos: List<Uri> = emptyList()): File = withContext(Dispatchers.IO) {
        require(photos.size <= 5) { "At most five explicitly selected photos can be attached" }
        val scans = repository.scans.first().filter { ids == null || it.id in ids }.map { it.sanitized(includeRawText) }
        val stats = DiagnosticStatistics.from(scans)
        val directory = File(context.cacheDir, "diagnostic-reports").apply { mkdirs() }
        directory.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 24L * 60 * 60 * 1000 }?.forEach { it.delete() }
        val file = File(directory, "just-cal-diagnostics-${UUID.randomUUID()}.zip")
        try {
            ZipOutputStream(file.outputStream().buffered()).use { zip ->
                fun entry(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
                }
                entry("summary.json", reportJson.encodeToString(DiagnosticReportSummary(createdAtMillis = System.currentTimeMillis(),
                    scanCount = scans.size, timingSampleCount = scans.count { it.status !in listOf("RUNNING", "INTERRUPTED") }, eligibleCorrectionScans = scans.count { it.detailedConsent && it.status in listOf("SUCCESS", "PARTIAL") }, successful = stats.successful, failed = stats.failed, cancelled = scans.count { it.status == "CANCELLED" }, interrupted = scans.count { it.status == "INTERRUPTED" },
                    correctionsByField = stats.correctionsByField, failureCategories = stats.failures, brandStatistics = stats.brands, averageMs = stats.averageMs,
                    medianMs = stats.medianMs, p95Ms = stats.p95Ms, models = scans.map { it.model }.distinct(),
                    runtimes = scans.map { it.runtime }.distinct(), rawTextIncluded = scans.any { it.rawText != null }, selectedPhotoCount = photos.size)))
                entry("scans.jsonl", scans.joinToString("\n", postfix = if (scans.isEmpty()) "" else "\n") { reportJson.encodeToString(it.copy(corrections = emptyList())) })
                entry("corrections.json", reportJson.encodeToString(scans.map { ScanCorrections(it.id, it.corrections) }))
                entry("README.txt", "Just Cal OCR diagnostics format 1. Offline report; no automatic uploads.\nTimes are measured milliseconds; null means unavailable. Average, median and p95 summarize finished attempts including cancelled and failed attempts; timingSampleCount is their sample count. RUNNING/INTERRUPTED elapsed is only the last recorded observation, not full processing duration; these scans are excluded from time aggregates. EXIF orientation describes normalized OCR pixels.\nDefault exports exclude OCR text, product names, parsed values, correction values, original paths and device identifiers.\nCorrection counts are user edits, not verified truth or model accuracy. Frequency denominators must include only eligibleCorrectionScans (detailed-consent sessions); basic-mode scans have no correction observation. Unchanged fields are never verified ground truth. Amount changes are portion adjustments. Small brand groups are descriptive only.\nPhotos, if present, were explicitly selected by the user for this report; association with scans is not inferred. Images may contain personal content and EXIF metadata.\n")
                photos.forEachIndexed { index, uri ->
                    zip.putNextEntry(ZipEntry("photos/selected-${index + 1}.image"))
                    requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                        val buffer = ByteArray(16 * 1024); var total = 0L
                        while (true) {
                            val count = input.read(buffer); if (count < 0) break
                            total += count; require(total <= 16L * 1024 * 1024) { "Selected photo exceeds 16 MiB" }
                            zip.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
            }
            file
        } catch (error: Exception) { file.delete(); throw error }
    }
}
