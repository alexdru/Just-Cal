package com.justcal.app.data.backup

import com.justcal.app.data.DiaryEntryEntity
import com.justcal.app.domain.EntrySource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Serializable
data class DiaryBackup(
    val format: String = "just-cal-diary",
    val schemaVersion: Int = 1,
    val exportedAtMillis: Long,
    val appVersion: String,
    val entries: List<BackupEntry>,
)

@Serializable
data class BackupEntry(
    val id: String,
    val date: String,
    val createdAtMillis: Long,
    val name: String,
    val consumedAmount: String,
    val consumedUnit: String = "g",
    val nutritionBasis: String = "per100g",
    val caloriesKcal: String,
    val proteinGrams: String,
    val fatGrams: String,
    val carbohydratesGrams: String,
    val source: String,
) {
    internal fun toEntity() = DiaryEntryEntity(
        id = 0, name = name, energyKcalHundredths = exact(caloriesKcal),
        proteinGramsHundredths = exact(proteinGrams), fatGramsHundredths = exact(fatGrams),
        carbsGramsHundredths = exact(carbohydratesGrams), eatenGramsHundredths = exact(consumedAmount),
        dayEpoch = LocalDate.parse(date).toEpochDay(), createdAtMillis = createdAtMillis,
        source = source, portableId = id,
    )

    companion object {
        internal fun from(row: DiaryEntryEntity) = BackupEntry(
            id = row.portableId, date = LocalDate.ofEpochDay(row.dayEpoch).toString(),
            createdAtMillis = row.createdAtMillis, name = row.name,
            consumedAmount = decimal(row.eatenGramsHundredths),
            caloriesKcal = decimal(row.energyKcalHundredths), proteinGrams = decimal(row.proteinGramsHundredths),
            fatGrams = decimal(row.fatGramsHundredths), carbohydratesGrams = decimal(row.carbsGramsHundredths),
            source = row.source,
        )
        private fun decimal(value: Long) = BigDecimal.valueOf(value, 2).toPlainString()
        private fun exact(value: String) = BigDecimal(value).movePointRight(2).longValueExact()
    }
}

/** Strict bounded validation occurs before the user is offered any database operation. */
object DiaryImportValidator {
    const val MAX_BYTES = 32 * 1024 * 1024
    const val MAX_ENTRIES = 50_000
    private val decimal = Regex("[0-9]+(\\.[0-9]{1,2})?")
    fun validate(backup: DiaryBackup): DiaryBackup {
        require(backup.format == "just-cal-diary") { "Unsupported diary backup format" }
        require(backup.schemaVersion == 1) { "Unsupported diary backup schema version" }
        require(backup.exportedAtMillis >= 0 && backup.appVersion.length in 1..128) { "Invalid backup metadata" }
        val ids = HashSet<String>()
        backup.entries.forEachIndexed { index, entry ->
            fun valid(condition: Boolean, field: String) = require(condition) { "Invalid entry ${index + 1}: $field" }
            valid(runCatching { UUID.fromString(entry.id).toString() == entry.id }.getOrDefault(false), "id")
            valid(ids.add(entry.id), "duplicate id")
            valid(entry.name.isNotBlank() && entry.name.length <= 1000 && !entry.name.contains('\u0000'), "name")
            valid(entry.date.length <= 16 && runCatching { LocalDate.parse(entry.date) }.isSuccess, "date")
            valid(entry.createdAtMillis >= 0, "createdAtMillis")
            valid(entry.consumedUnit == "g" && entry.nutritionBasis == "per100g", "units")
            valid(EntrySource.entries.any { it.name == entry.source }, "source")
            listOf("consumedAmount" to entry.consumedAmount, "caloriesKcal" to entry.caloriesKcal,
                "proteinGrams" to entry.proteinGrams, "fatGrams" to entry.fatGrams,
                "carbohydratesGrams" to entry.carbohydratesGrams).forEach { (field, value) ->
                valid(value.length <= 22 && decimal.matches(value), field)
                val number = runCatching { BigDecimal(value).movePointRight(2).longValueExact() }.getOrNull()
                valid(number != null && number >= 0 && (field != "consumedAmount" || number > 0), field)
            }
        }
        return backup
    }
}

object DiaryBackupSerializer {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
    fun read(input: InputStream): DiaryBackup {
        val buffer = ByteArray(8192)
        val collected = java.io.ByteArrayOutputStream()
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(collected.size().toLong() + count <= DiaryImportValidator.MAX_BYTES) { "Diary backup exceeds 32 MiB" }
            collected.write(buffer, 0, count)
        }
        val bytes = collected.toByteArray()
        val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        val tree = json.parseToJsonElement(text).jsonObject
        require(tree.keys.containsAll(listOf("format", "schemaVersion", "exportedAtMillis", "appVersion", "entries"))) {
            "Missing required backup metadata"
        }
        val entries = requireNotNull(tree["entries"]).jsonArray
        require(entries.size <= DiaryImportValidator.MAX_ENTRIES) { "Too many diary entries" }
        entries.forEach {
            require(it.jsonObject.keys.containsAll(listOf("consumedUnit", "nutritionBasis"))) { "Missing entry units" }
        }
        return DiaryImportValidator.validate(json.decodeFromString<DiaryBackup>(text))
    }
    fun write(backup: DiaryBackup, output: OutputStream) {
        DiaryImportValidator.validate(backup)
        output.write(json.encodeToString(DiaryBackup.serializer(), backup).toByteArray(Charsets.UTF_8))
        output.flush()
    }
}
