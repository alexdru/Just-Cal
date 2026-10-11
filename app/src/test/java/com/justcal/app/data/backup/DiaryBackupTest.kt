package com.justcal.app.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class DiaryBackupTest {
    private fun entry() = BackupEntry(
        id = "12345678-1234-4234-a234-123456789012", date = "2026-10-12",
        createdAtMillis = 1791800000000, name = "Йогурт / Yogurt",
        consumedAmount = "37.51", caloriesKcal = "205.50", proteinGrams = "12.34",
        fatGrams = "5.67", carbohydratesGrams = "20.01", source = "PACKAGE_SCAN",
    )
    private fun backup(entries: List<BackupEntry> = listOf(entry())) =
        DiaryBackup(exportedAtMillis = 1, appVersion = "1.0.0-debug", entries = entries)
    private fun reject(value: DiaryBackup) {
        assertThrows(IllegalArgumentException::class.java) { DiaryImportValidator.validate(value) }
    }

    @Test fun unicodeDecimalDatesAndMetadataRoundTrip() {
        val original = backup(listOf(entry(), entry().copy(
            id = "12345678-1234-4234-a234-123456789013", date = "2025-01-01",
            consumedAmount = "0.01", caloriesKcal = "92233720368547758.07",
        )))
        val output = ByteArrayOutputStream()
        DiaryBackupSerializer.write(original, output)
        val restored = DiaryBackupSerializer.read(ByteArrayInputStream(output.toByteArray()))
        assertEquals(original, restored)
        assertEquals(3751L, restored.entries.first().toEntity().eatenGramsHundredths)
        assertEquals(Long.MAX_VALUE, restored.entries.last().toEntity().energyKcalHundredths)
    }
    @Test fun emptyDiaryRoundTrips() {
        val output = ByteArrayOutputStream()
        DiaryBackupSerializer.write(backup(emptyList()), output)
        assertTrue(DiaryBackupSerializer.read(ByteArrayInputStream(output.toByteArray())).entries.isEmpty())
    }
    @Test fun rejectsUnsupportedVersionsUnitsDuplicateIdsAndInvalidNumbers() {
        reject(backup().copy(schemaVersion = 2))
        reject(backup().copy(format = "other"))
        reject(backup(listOf(entry(), entry())))
        listOf("-1", "1.001", "NaN", "1e2", "92233720368547758.08").forEach {
            reject(backup(listOf(entry().copy(caloriesKcal = it))))
        }
        reject(backup(listOf(entry().copy(consumedAmount = "0"))))
        reject(backup(listOf(entry().copy(consumedUnit = "oz"))))
        reject(backup(listOf(entry().copy(name = " "))))
        reject(backup(listOf(entry().copy(date = "2026-02-30"))))
        reject(backup(listOf(entry().copy(id = "1"))))
        reject(backup(listOf(entry().copy(source = "UNKNOWN"))))
    }
    @Test fun requiresExplicitFormatVersionAndUnits() {
        val output = ByteArrayOutputStream()
        DiaryBackupSerializer.write(backup(), output)
        val text = output.toString("UTF-8")
        listOf(""""schemaVersion": 1,""", """"format": "just-cal-diary",""",
            """"consumedUnit": "g",""", """"nutritionBasis": "per100g",""").forEach { field ->
            assertThrows(IllegalArgumentException::class.java) {
                DiaryBackupSerializer.read(ByteArrayInputStream(text.replace(field, "").toByteArray()))
            }
        }
    }

    @Test fun rejectsMalformedAndInvalidUtf8Files() {
        assertThrows(Exception::class.java) { DiaryBackupSerializer.read(ByteArrayInputStream("{".toByteArray())) }
        assertThrows(Exception::class.java) { DiaryBackupSerializer.read(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28))) }
    }
}
