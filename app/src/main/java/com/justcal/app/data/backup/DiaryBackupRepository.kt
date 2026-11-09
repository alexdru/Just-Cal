package com.justcal.app.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.justcal.app.BuildConfig
import com.justcal.app.data.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ImportTypeCounts(val imported: Int = 0, val skipped: Int = 0, val conflicting: Int = 0)
data class DiaryImportResult(
    val imported: Int, val skipped: Int, val conflicting: Int, val recoveryFile: String? = null,
    val products: ImportTypeCounts = ImportTypeCounts(), val revisions: ImportTypeCounts = ImportTypeCounts(),
    val links: ImportTypeCounts = ImportTypeCounts(),
)

@Singleton
class DiaryBackupRepository @Inject constructor(
    private val database: DiaryDatabase, @ApplicationContext private val context: Context,
) {
    private val recovery get() = File(context.noBackupFilesDir, "diary-recovery.json")
    private suspend fun snapshot(): DiaryBackup {
        val catalog = database.foodCatalogDao()
        return DiaryBackup(schemaVersion = 2, exportedAtMillis = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME, entries = database.diaryDao().getAll().map(BackupEntry::from),
            products = catalog.products().map { it.domain() },
            revisions = catalog.allRevisions().map { it.domain() },
            links = catalog.links().map { it.domain() })
    }
    suspend fun export(output: OutputStream) = withContext(Dispatchers.IO) {
        DiaryBackupSerializer.write(database.withTransaction { snapshot() }, output)
    }
    suspend fun preview(input: InputStream): DiaryBackup = withContext(Dispatchers.IO) { DiaryBackupSerializer.read(input) }
    suspend fun recoveryAvailable(): Boolean = withContext(Dispatchers.IO) { recovery.isFile }
    suspend fun exportRecovery(output: OutputStream) = withContext(Dispatchers.IO) {
        check(recovery.isFile) { "No recovery backup is available" }
        recovery.inputStream().use { it.copyTo(output) }; output.flush()
    }
    @Suppress("BlockingMethodInNonBlockingContext") // Called only inside the IO transaction path.
    private suspend fun writeRecovery() {
        val partial = File(context.noBackupFilesDir, "diary-recovery.partial")
        try {
            java.io.FileOutputStream(partial).use { stream ->
                DiaryBackupSerializer.write(snapshot(), stream); stream.fd.sync()
            }
            java.nio.file.Files.move(partial.toPath(), recovery.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } finally { partial.delete() }
    }
    suspend fun previewCounts(backup: DiaryBackup): DiaryImportResult = applyBackup(backup, false, true)
    suspend fun importBackup(backup: DiaryBackup, replaceAll: Boolean = false): DiaryImportResult = applyBackup(backup, replaceAll, false)
    private suspend fun applyBackup(backup: DiaryBackup, replaceAll: Boolean, previewOnly: Boolean): DiaryImportResult = withContext(Dispatchers.IO) {
        DiaryImportValidator.validate(backup)
        database.withTransaction {
            val diary = database.diaryDao(); val catalog = database.foodCatalogDao()
            if (replaceAll) {
                writeRecovery()
                catalog.deleteLinks()
                diary.deleteAll()
                catalog.deleteProducts()
            }
            val existing = diary.getAll().associateBy { it.portableId }
            val existingProducts = catalog.products().associateBy { it.id }
            val existingRevisions = catalog.allRevisions().associateBy { it.id }
            val existingLinks = catalog.links().associateBy { it.entryId }
            val badEntries = HashSet<String>(); val badProducts = HashSet<String>(); val badRevisions = HashSet<String>()
            var imported = 0; var skipped = 0; var conflicting = 0
            var pi = 0; var ps = 0; var pc = 0
            var ri = 0; var rs = 0; var rc = 0
            var li = 0; var ls = 0; var lc = 0
            backup.entries.forEach { entry ->
                val current = existing[entry.id]
                when {
                    current == null -> { if (!previewOnly) diary.insert(entry.toEntity()); imported++ }
                    current == entry.toEntity().copy(id = current.id) -> skipped++
                    else -> { conflicting++; badEntries += entry.id }
                }
            }
            backup.products.forEach { p ->
                val current = existingProducts[p.id]
                when {
                    current == null -> { if (!previewOnly) catalog.insertProduct(ProductEntity(p.id, p.createdAtMillis, p.archived)); pi++ }
                    current.domain() == p -> ps++
                    else -> { pc++; badProducts += p.id }
                }
            }
            val incoming = backup.revisions.associateBy { it.id }
            // Decide all conflicts before inserting dependents, independent of file order.
            backup.revisions.forEach { r ->
                if (r.productId in badProducts || existingRevisions[r.id]?.domain()?.let { it != r } == true) badRevisions += r.id
            }
            val resolved = HashSet<String>()
            backup.revisions.forEach { r ->
                val path = mutableListOf<String>()
                var id: String? = r.id
                while (id != null && id !in resolved) { path += id; id = incoming[id]?.previousRevisionId }
                var blocked = id in badRevisions
                path.asReversed().forEach { key ->
                    blocked = blocked || key in badRevisions
                    if (blocked) badRevisions += key
                    resolved += key
                }
            }
            backup.revisions.forEach { r ->
                when {
                    r.id in badRevisions -> rc++
                    existingRevisions[r.id] != null -> rs++
                    else -> { if (!previewOnly) catalog.insertRevision(ProductRevisionEntity.from(r)); ri++ }
                }
            }
            backup.links.forEach { link ->
                val current = existingLinks[link.entryId]
                when {
                    link.entryId in badEntries || link.revisionId in badRevisions -> lc++
                    current == null -> { if (!previewOnly) catalog.link(EntryProductLinkEntity(link.entryId, link.revisionId, link.provenance)); li++ }
                    current.domain() == link -> ls++
                    else -> lc++
                }
            }
            if (!previewOnly) catalog.invalidate()
            DiaryImportResult(imported, skipped, conflicting, if (replaceAll) recovery.name else null,
                ImportTypeCounts(pi, ps, pc), ImportTypeCounts(ri, rs, rc), ImportTypeCounts(li, ls, lc))
        }
    }
}
