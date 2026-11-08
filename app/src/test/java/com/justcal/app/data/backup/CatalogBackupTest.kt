package com.justcal.app.data.backup

import com.justcal.app.domain.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class CatalogBackupTest {
    private fun uuid() = UUID.randomUUID().toString()
    private val product = Product(uuid(), 1)
    private val revision = ProductRevision(uuid(), product.id, name = "Йогурт almond", energy = 10000,
        protein = 100, fat = 200, carbs = 300, savedAtMillis = 1)
    private fun backup() = DiaryBackup(schemaVersion = 2, exportedAtMillis = 1, appVersion = "test",
        entries = emptyList(), products = listOf(product), revisions = listOf(revision))
    @Test fun graphRoundTripAndLegacyHasNoNewFields() {
        val out = ByteArrayOutputStream()
        DiaryBackupSerializer.write(backup(), out)
        assertEquals(backup(), DiaryBackupSerializer.read(ByteArrayInputStream(out.toByteArray())))
        val legacy = ByteArrayOutputStream()
        DiaryBackupSerializer.write(backup().copy(schemaVersion = 1, products = emptyList(), revisions = emptyList()), legacy)
        assertFalse(legacy.toString("UTF-8").contains("catalogUnits"))
        assertFalse(legacy.toString("UTF-8").contains("products"))
    }
    @Test fun missingReferencesCyclesAndCrossProductParentsAreRejected() {
        val original = backup()
        listOf(original.copy(products = emptyList()),
            original.copy(revisions = listOf(revision.copy(previousRevisionId = revision.id))),
            original.copy(revisions = listOf(revision, revision)),
            original.copy(revisions = listOf(revision.copy(energy = -1))),
            original.copy(catalogUnits = "grams"),
            original.copy(links = listOf(EntryProductLink(uuid(), revision.id, "USER_SELECTED"))))
            .forEach { b -> assertThrows(IllegalArgumentException::class.java) { DiaryImportValidator.validate(b) } }
    }
    @Test fun longRevisionChainValidationIsIterative() {
        val rows = (0..49999).map { revision.copy(id = UUID(0, it.toLong()).toString(),
            previousRevisionId = if (it == 0) null else UUID(0, it.toLong() - 1).toString()) }
        DiaryImportValidator.validate(backup().copy(revisions = rows))
    }
    @Test fun maximumGraphExportEitherRoundTripsOrFailsBeforeWriting() {
        val products = (0..9999).map { product.copy(id = UUID(1, it.toLong()).toString()) }
        val revisions = (0..49999).map { revision.copy(id = UUID(2, it.toLong()).toString(),
            productId = products[it % products.size].id) }
        val b = backup().copy(products = products, revisions = revisions)
        val output = ByteArrayOutputStream()
        try {
            DiaryBackupSerializer.write(b, output)
            assertEquals(b, DiaryBackupSerializer.read(ByteArrayInputStream(output.toByteArray())))
        } catch (error: IllegalArgumentException) {
            assertEquals(0, output.size())
            assertTrue(error.message.orEmpty().contains("32 MiB"))
        }
    }
}
