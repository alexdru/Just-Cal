package com.justcal.app.data.backup

import com.justcal.app.data.DiaryEntryEntity
import com.justcal.app.domain.EntrySource
import com.justcal.app.domain.Product
import com.justcal.app.domain.ProductRevision
import com.justcal.app.domain.EntryProductLink
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
    val schemaVersion: Int = 2,
    val exportedAtMillis: Long,
    val appVersion: String,
    val entries: List<BackupEntry>,
    val products: List<Product> = emptyList(),
    val revisions: List<ProductRevision> = emptyList(),
    val links: List<EntryProductLink> = emptyList(),
    val catalogUnits: String = "hundredths-per100g",
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
        require(backup.schemaVersion == 2) { "Unsupported diary backup schema version; only version 2 is supported" }
        require(backup.exportedAtMillis >= 0 && backup.appVersion.length in 1..128) { "Invalid backup metadata" }
        require(backup.entries.size <= MAX_ENTRIES) { "Too many diary entries" }
        require(backup.products.size <= 10_000 && backup.revisions.size <= 50_000 && backup.links.size <= MAX_ENTRIES) { "Catalog limit exceeded" }
        require(backup.catalogUnits == "hundredths-per100g") { "Unsupported catalog units" }
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
        fun uuid(id: String) = require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) { "Invalid catalog UUID" }
        val products = backup.products.associateBy { it.id }
        val revisions = backup.revisions.associateBy { it.id }
        require(products.size == backup.products.size && revisions.size == backup.revisions.size) { "Duplicate catalog UUID" }
        backup.products.forEach { uuid(it.id); require(it.createdAtMillis >= 0) }
        backup.revisions.forEach { r ->
            uuid(r.id); uuid(r.productId)
            require(r.productId in products && r.savedAtMillis >= 0) { "Missing product or invalid timestamp" }
            require(r.name.isNotBlank() && r.name.length <= 1000 && '\u0000' !in r.name)
            require(listOf(r.energy, r.protein, r.fat, r.carbs).all { it >= 0 })
            require(r.packageMassHundredths == null || r.packageMassHundredths > 0)
            require(listOf(r.brand, r.manufacturer, r.retailer, r.deliveryService).all {
                it == null || (it.isNotBlank() && it.length <= 200 && '\u0000' !in it)
            })
            require(r.origins.length in 1..1000 && '\u0000' !in r.origins)
            r.previousRevisionId?.let { parent ->
                uuid(parent)
                require(revisions[parent]?.productId == r.productId) { "Invalid revision parent" }
            }
        }
        val visited = HashSet<String>()
        backup.revisions.forEach { r ->
            val path = HashSet<String>()
            var id: String? = r.id
            while (id != null && id !in visited) {
                require(path.add(id)) { "Cyclic revision ancestry" }
                id = revisions.getValue(id).previousRevisionId
            }
            visited.addAll(path)
        }
        val linked = HashSet<String>()
        backup.links.forEach { link ->
            uuid(link.entryId); uuid(link.revisionId)
            require(linked.add(link.entryId) && link.entryId in ids && link.revisionId in revisions) { "Invalid entry link" }
            require(link.provenance in setOf("USER_SELECTED", "HISTORY_PROMOTION", "SCAN_ACCEPTED", "USER_CORRECTION")) { "Invalid selection provenance" }
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
        require(tree["schemaVersion"].toString() == "2") {
            "Unsupported diary backup schema version; only version 2 is supported"
        }
        require(tree.keys.containsAll(listOf("products", "revisions", "links", "catalogUnits"))) { "Missing catalog graph" }
        require(tree.getValue("products").jsonArray.size <= 10_000 && tree.getValue("revisions").jsonArray.size <= 50_000 &&
            tree.getValue("links").jsonArray.size <= DiaryImportValidator.MAX_ENTRIES) { "Catalog limit exceeded" }
        val entries = requireNotNull(tree["entries"]).jsonArray
        require(entries.size <= DiaryImportValidator.MAX_ENTRIES) { "Too many diary entries" }
        entries.forEach {
            require(it.jsonObject.keys.containsAll(listOf("consumedUnit", "nutritionBasis"))) { "Missing entry units" }
        }
        return DiaryImportValidator.validate(json.decodeFromString<DiaryBackup>(text))
    }
    fun write(backup: DiaryBackup, output: OutputStream) {
        DiaryImportValidator.validate(backup)
        val encoded = json.encodeToString(DiaryBackup.serializer(), backup)
        val bytes = encoded.toByteArray(Charsets.UTF_8)
        require(bytes.size <= DiaryImportValidator.MAX_BYTES) { "Backup exceeds self-import limit of 32 MiB" }
        output.write(bytes)
        output.flush()
    }
}
