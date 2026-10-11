package com.justcal.app.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.justcal.app.BuildConfig
import com.justcal.app.data.DiaryDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DiaryImportResult(val imported: Int, val skipped: Int, val conflicting: Int, val recoveryFile: String? = null)

@Singleton
class DiaryBackupRepository @Inject constructor(
    private val database: DiaryDatabase,
    @ApplicationContext private val context: Context,
) {
    private val recovery get() = File(context.noBackupFilesDir, "diary-recovery.json")
    private suspend fun snapshot() = DiaryBackup(exportedAtMillis = System.currentTimeMillis(),
        appVersion = BuildConfig.VERSION_NAME, entries = database.diaryDao().getAll().map(BackupEntry::from))

    suspend fun export(output: OutputStream) = withContext(Dispatchers.IO) {
        DiaryBackupSerializer.write(database.withTransaction { snapshot() }, output)
    }

    suspend fun preview(input: InputStream): DiaryBackup = withContext(Dispatchers.IO) {
        DiaryBackupSerializer.read(input)
    }

    suspend fun recoveryAvailable(): Boolean = withContext(Dispatchers.IO) { recovery.isFile }
    suspend fun exportRecovery(output: OutputStream) = withContext(Dispatchers.IO) {
        check(recovery.isFile) { "No recovery backup is available" }
        recovery.inputStream().use { it.copyTo(output) }
        output.flush()
    }

    suspend fun importBackup(backup: DiaryBackup, replaceAll: Boolean = false): DiaryImportResult =
        withContext(Dispatchers.IO) {
            DiaryImportValidator.validate(backup)
            database.withTransaction {
                val dao = database.diaryDao()
                val existing = dao.getAll().associateBy { it.portableId }
                if (replaceAll) {
                    // Durable recovery is committed before deleting anything. A failed write aborts the transaction.
                    val partial = File(context.noBackupFilesDir, "diary-recovery.partial")
                    try {
                        java.io.FileOutputStream(partial).use { stream ->
                            DiaryBackupSerializer.write(snapshot(), stream)
                            stream.fd.sync()
                        }
                        java.nio.file.Files.move(partial.toPath(), recovery.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                    } finally { partial.delete() }
                    dao.deleteAll()
                }
                var imported = 0
                var skipped = 0
                var conflicting = 0
                backup.entries.forEach { entry ->
                    val current = if (replaceAll) null else existing[entry.id]
                    when {
                        current == null -> { dao.insert(entry.toEntity()); imported++ }
                        current == entry.toEntity().copy(id = current.id) -> skipped++
                        else -> conflicting++
                    }
                }
                DiaryImportResult(imported, skipped, conflicting, if (replaceAll) recovery.name else null)
            }
        }
}
