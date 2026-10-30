package com.justcal.app.diagnostics

import android.content.Context
import android.graphics.Bitmap
import com.justcal.app.camera.ImagePreprocessor
import com.justcal.app.camera.PreparedImage
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Private, backup-excluded normalized photos and bounded native tombstones. No external originals. */
internal class DiagnosticAttachments(context: Context, private val images: ImagePreprocessor) {
    private val root = File(context.noBackupFilesDir, "diagnostic-attachments").apply { mkdirs() }
    private fun file(id: String, suffix: String): File {
        require(UUID.fromString(id).toString() == id)
        return File(root, "$id.$suffix")
    }
    suspend fun capture(id: String, image: PreparedImage): Boolean = withContext(Dispatchers.IO) {
        val pending = file(id, "tmp")
        try {
            val bitmap = images.decode(image)
            try {
                pending.outputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
                    output.fd.sync()
                }
            } finally { bitmap.recycle() }
            require(pending.length() in 1..MAX_PHOTO_BYTES)
            check(pending.renameTo(file(id, "jpg")))
            true
        } finally { pending.delete() }
    }
    fun trace(id: String, input: InputStream): Boolean {
        val pending = file(id, "tmp")
        return try {
            pending.outputStream().use { output ->
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_TRACE_BYTES)
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
            pending.length() > 0 && pending.renameTo(file(id, "pb"))
        } finally { pending.delete() }
    }
    fun open(id: String, suffix: String): InputStream? =
        runCatching { file(id, suffix).inputStream() }.getOrNull()

    fun exists(id: String, suffix: String): Boolean = runCatching { file(id, suffix).isFile }.getOrDefault(false)
    fun clear() { root.listFiles()?.forEach { it.delete() } }

    fun prune(ids: Set<String>, now: Long = System.currentTimeMillis()) {
        root.listFiles()?.forEach {
            if (it.nameWithoutExtension !in ids || now - it.lastModified() > OcrDiagnosticsRepository.RETENTION_MS) it.delete()
        }
        val files = root.listFiles().orEmpty().sortedBy { it.lastModified() }
        var bytes = files.sumOf { it.length() }
        for (file in files) {
            if (bytes <= MAX_BYTES) break
            val size = file.length()
            if (file.delete()) bytes -= size
        }
    }
    companion object {
        const val MAX_BYTES = 64L * 1024 * 1024
        const val MAX_PHOTO_BYTES = 6L * 1024 * 1024
        const val MAX_TRACE_BYTES = 512 * 1024
    }
}
