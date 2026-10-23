package com.justcal.app.diagnostics

import android.content.Context
import android.os.Build
import androidx.room.*
import com.justcal.app.BuildConfig
import com.justcal.app.domain.FoodDraft
import com.justcal.app.domain.FoodField
import com.justcal.app.ocr.PackageScanPrefill
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

@Entity(tableName = "ocr_scans")
data class StoredScan(@PrimaryKey val id: String, val timestampMillis: Long, val payload: String)
@Dao interface DiagnosticDao {
    @Query("SELECT * FROM ocr_scans ORDER BY timestampMillis DESC") fun observe(): Flow<List<StoredScan>>
    @Query("SELECT * FROM ocr_scans WHERE id = :id") suspend fun get(id: String): StoredScan?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(scan: StoredScan)
    @Query("DELETE FROM ocr_scans") suspend fun clear()
    @Query("DELETE FROM ocr_scans WHERE timestampMillis < :cutoff OR id NOT IN (SELECT id FROM ocr_scans ORDER BY timestampMillis DESC LIMIT 500)") suspend fun prune(cutoff: Long)
    @Query("SELECT SUM(length(CAST(payload AS BLOB))) FROM ocr_scans") suspend fun bytes(): Long?
    @Query("DELETE FROM ocr_scans WHERE id = (SELECT id FROM ocr_scans ORDER BY timestampMillis LIMIT 1)") suspend fun removeOldest()
}
@Database(entities = [StoredScan::class], version = 1, exportSchema = true)
abstract class DiagnosticDatabase : RoomDatabase() { abstract fun scans(): DiagnosticDao }

/** Isolated database and supervisor: diagnostic failures never fail recognition or diary writes. */
@Singleton class OcrDiagnosticsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("ocr-diagnostic-consent", Context.MODE_PRIVATE)
    // All future scans capture details, including upgrades with a former disabled preference.
    private val mutableDetailed = MutableStateFlow(true)
    val detailed: StateFlow<Boolean> = mutableDetailed.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = Room.databaseBuilder(context, DiagnosticDatabase::class.java,
        File(context.noBackupFilesDir, "ocr-diagnostics.db").absolutePath).build()
    private val dao = database.scans()
    val scans: Flow<List<DiagnosticScan>> = dao.observe().map { rows ->
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        rows.filter { it.timestampMillis >= cutoff }.mapNotNull { runCatching { Json.decodeFromString<DiagnosticScan>(it.payload) }.getOrNull() }
    }.catch { emit(emptyList()) }

    @Volatile var generation: Long = 0
        private set
    @Volatile var consentGeneration: Long = 0
        private set
    private val operations = kotlinx.coroutines.channels.Channel<suspend () -> Unit>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    init {
        scope.launch { for (operation in operations) runCatching { operation() } }
        operations.trySend {
            database.withTransaction {
                val exits = runCatching {
                    context.getSystemService(android.app.ActivityManager::class.java)
                        .getHistoricalProcessExitReasons(context.packageName, 0, 16)
                }.getOrDefault(emptyList())
                dao.observe().first().forEach { row ->
                    val scan = Json.decodeFromString<DiagnosticScan>(row.payload)
                    if (scan.status == "RUNNING") {
                        val exit = exits.filter { it.timestamp >= scan.timestampMillis }.minByOrNull { it.timestamp }
                        val reason = exit?.let { "PROCESS_EXIT_REASON:${it.reason}" }
                        put(scan.copy(status = "INTERRUPTED",
                            errors = listOfNotNull("PROCESS_INTERRUPTED_TOTAL_UNAVAILABLE", reason)))
                    }
                }
                prune()
            }
        }
        scope.launch { while (isActive) { delay(60 * 60 * 1000L); operations.send { prune() } } }
    }
    private suspend fun execute(block: suspend () -> Unit) {
        val completion = CompletableDeferred<Unit>()
        operations.send { try { block(); completion.complete(Unit) } catch (error: Exception) { completion.completeExceptionally(error) } }
        completion.await()
    }
    // Legacy mode hook for compatibility tests; production always starts with detailed capture.
    @android.annotation.SuppressLint("UseKtx")
    internal suspend fun setDetailed(enabled: Boolean) = execute {
        check(preferences.edit().putBoolean("detailed", enabled).commit()) { "Consent could not be saved" }
        mutableDetailed.value = enabled
        if (!enabled) consentGeneration++
        // Revoking consent removes previously captured content as well.
        if (!enabled) database.withTransaction {
            dao.observe().first().forEach { row ->
                val old = Json.decodeFromString<DiagnosticScan>(row.payload)
                put(old.sanitized().copy(brand = null, detailedConsent = false, corrections = emptyList(), correctedFields = emptyList()))
            }
        }
    }
    suspend fun clear() { generation++; execute { dao.clear() } }
    internal suspend fun awaitIdle() = execute { }
    internal suspend fun close() { awaitIdle(); operations.close(); scope.cancel(); database.close() }
    suspend fun setBrand(id: String, brand: String?) = execute {
        check(detailed.value) { "Detailed diagnostics consent is required" }
        database.withTransaction {
            val old = dao.get(id)?.let { Json.decodeFromString<DiagnosticScan>(it.payload) } ?: return@withTransaction
            check(old.detailedConsent) { "This scan was recorded in basic mode" }
            put(old.copy(brand = brand?.trim()?.take(80)?.takeIf { it.isNotEmpty() }))
        }
    }
    fun record(scan: DiagnosticScan, attemptGeneration: Long, attemptConsentGeneration: Long = consentGeneration) {
        operations.trySend { if (attemptGeneration == generation) database.withTransaction {
            val safe = if (detailed.value && scan.detailedConsent && attemptConsentGeneration == consentGeneration) scan else scan.sanitized().copy(brand = null, detailedConsent = false, corrections = emptyList(), correctedFields = emptyList())
            put(safe); prune()
        } }
    }
    fun corrections(prefill: PackageScanPrefill, draft: FoodDraft) {
        val id = prefill.diagnosticSessionId ?: return
        val original = prefill.recognition?.draft() ?: prefill.label.draft()
        val changes = FoodField.entries.filter { normalize(it, original.value(it)) != normalize(it, draft.value(it)) }
            .map { field -> DiagnosticCorrection(field.name,
                if (field == FoodField.AMOUNT) "USER_PORTION_ADJUSTMENT" else if (original.value(field).isBlank()) "MISSING_FIELD_FILLED" else "EXPLICIT_CORRECTION",
                original.value(field), draft.value(field)) }
        operations.trySend { database.withTransaction {
            val old = dao.get(id)?.let { Json.decodeFromString<DiagnosticScan>(it.payload) } ?: return@withTransaction
            if (old.detailedConsent && detailed.value) put(old.copy(
                correctedFields = (changes.map { it.field } + old.correctedFields.filter { it == "NET_WEIGHT" }).distinct(),
                corrections = changes + old.corrections.filter { it.field == "NET_WEIGHT" }))
        } }
    }
    suspend fun correctNetWeight(id: String, value: String) = execute {
        check(detailed.value) { "Detailed diagnostics consent is required" }
        require(value.length <= 32)
        val normalized = value.trim().replace(',', '.').toBigDecimalOrNull()
        require(normalized != null && normalized.signum() > 0 && normalized.scale() <= 2 && normalized <= java.math.BigDecimal("1000000")) { "Enter positive grams with at most two decimal places" }
        database.withTransaction {
            val old = dao.get(id)?.let { Json.decodeFromString<DiagnosticScan>(it.payload) } ?: return@withTransaction
            check(old.detailedConsent)
            val before = old.parsedFields["NET_WEIGHT"].orEmpty()
            val after = normalized.stripTrailingZeros().toPlainString()
            if (before.replace(',', '.').toBigDecimalOrNull()?.compareTo(normalized) != 0) {
                val change = DiagnosticCorrection("NET_WEIGHT", if (before.isBlank()) "MISSING_FIELD_FILLED" else "EXPLICIT_EXTRACTION_CORRECTION", before, after)
                put(old.copy(correctedFields = (old.correctedFields + "NET_WEIGHT").distinct(),
                    corrections = old.corrections.filter { it.field != "NET_WEIGHT" } + change))
            } else put(old.copy(correctedFields = old.correctedFields - "NET_WEIGHT",
                corrections = old.corrections.filter { it.field != "NET_WEIGHT" }))
        }
    }
    private fun normalize(field: FoodField, value: String): String =
        if (field == FoodField.NAME) value.trim() else value.trim().replace(',', '.').toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: value.trim()
    private suspend fun put(scan: DiagnosticScan) { dao.put(StoredScan(scan.id, scan.timestampMillis, Json.encodeToString(scan))) }
    private suspend fun prune() {
        dao.prune(System.currentTimeMillis() - RETENTION_MS)
        while ((dao.bytes() ?: 0) > MAX_BYTES) dao.removeOldest()
    }
    fun metadata(id: String, timestamp: Long, status: String, elapsed: Long) = DiagnosticScan(id, timestamp,
        appVersion = BuildConfig.VERSION_NAME, androidVersion = Build.VERSION.RELEASE,
        device = "${Build.MANUFACTURER} ${Build.MODEL}", runtime = "ONNX Runtime 1.23.2 (pinned dependency) / OpenCV ${org.opencv.core.Core.VERSION} CPU", status = status, totalMs = elapsed)
    companion object {
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        const val MAX_BYTES = 8L * 1024 * 1024
    }
}
