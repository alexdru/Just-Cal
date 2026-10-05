package com.justcal.app.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Streaming, transactional private model storage. No provider URI is treated as a path. */
class ModelFiles(private val directory: File) {
    init { check(directory.mkdirs() || directory.isDirectory) }

    fun list(): List<LocalModel> = directory.listFiles().orEmpty().asSequence().filter { it.isDirectory && !it.name.startsWith(".") }
        .mapNotNull { folder ->
            runCatching {
                val metadata = Json.parseToJsonElement(File(folder, "metadata.json").readText()).jsonObject
                val model = LocalModel(
                    folder.name,
                    metadata.getValue("name").jsonPrimitive.content,
                    File(folder, "model.litertlm").length(),
                )
                path(model)
                model
            }.getOrNull()
        }.sortedBy { (_, name) -> name }.toList()

    fun path(model: LocalModel): File {
        require(UUID.fromString(model.id).toString() == model.id) { "Invalid private model reference" }
        val file = File(File(directory, model.id), "model.litertlm").canonicalFile
        require((file.parentFile?.parentFile == directory.canonicalFile) && file.isFile && (file.length() > 0)) {
            "Private model unavailable"
        }
        return file
    }

    fun remove(model: LocalModel) {
        val folder = path(model).parentFile!!
        check(folder.deleteRecursively()) { "Could not remove model" }
    }

    fun removePartialImports() {
        directory.listFiles().orEmpty().filter { it.name.startsWith(".import-") }.forEach { it.deleteRecursively() }
    }

    suspend fun import(name: String, expectedSize: Long?, stream: InputStream, onProgress: (Long) -> Unit): LocalModel {
        require(name.endsWith(".litertlm", ignoreCase = true) && name.length <= 255) { "Select a .litertlm file" }
        val id = UUID.randomUUID().toString()
        val temporary = File(directory, ".import-$id")
        val committed = File(directory, id)
        check(temporary.mkdir())
        var retained = false
        try {
            var total = 0L
            File(temporary, "model.litertlm").outputStream().use { target ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 16L * 1024 * 1024 * 1024) { "Model exceeds 16 GiB import limit" }
                    target.write(buffer, 0, count)
                    onProgress(total)
                }
                target.fd.sync()
            }
            require(total > 0 && (expectedSize == null || expectedSize < 0 || total == expectedSize)) {
                "Empty or incomplete model file"
            }
            File(temporary, "metadata.json").writeText(buildJsonObject { put("name", name) }.toString())
            currentCoroutineContext().ensureActive()
            check(temporary.renameTo(committed)) { "Could not commit model import" }
            retained = true
            return LocalModel(id, name, total)
        } finally {
            if (!retained) temporary.deleteRecursively()
        }
    }
}

@Singleton
class LocalModelStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val lock = Mutex()
    // noBackupFilesDir: durable private files, excluded from backup and APK packaging.
    private val files by lazy { ModelFiles(File(context.noBackupFilesDir, "local-ai-models")) }

    suspend fun list(): List<LocalModel> = withContext(Dispatchers.IO) {
        lock.withLock { files.removePartialImports(); files.list() }
    }

    suspend fun import(uri: Uri, progress: (Long) -> Unit): LocalModel {
        val produced = AtomicReference<LocalModel?>()
        try {
            return withContext(Dispatchers.IO) {
                lock.withLock {
                    require(uri.scheme == "content") { "Expected a document provider URI" }
                    var name: String? = null
                    var size: Long? = null
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                        null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            name = cursor.getString(0)
                            if (!cursor.isNull(1)) size = cursor.getLong(1)
                        }
                    }
                    size?.takeIf { it > 0 }?.let { expected ->
                        require(expected <= 16L * 1024 * 1024 * 1024) { "Model exceeds 16 GiB import limit" }
                        val storage = context.getSystemService(StorageManager::class.java)
                        val volume = storage.getUuidForPath(context.noBackupFilesDir)
                        val required = expected + 64L * 1024 * 1024
                        require(storage.getAllocatableBytes(volume) >= required) { "Insufficient private storage" }
                        storage.allocateBytes(volume, required)
                    }
                    context.contentResolver.openInputStream(uri).use { stream ->
                        files.import(requireNotNull(name) { "Model filename unavailable" }, size,
                            requireNotNull(stream) { "Model file unavailable" }, progress).also { produced.set(it) }
                    }
                }
            }
        } catch (failure: Throwable) {
            // Covers cancellation at dispatcher handoff and provider close errors after rename.
            withContext(NonCancellable + Dispatchers.IO) {
                lock.withLock { produced.get()?.let(files::remove) }
            }
            throw failure
        }
    }

    suspend fun remove(model: LocalModel) = withContext(Dispatchers.IO) {
        lock.withLock {
            files.path(model) // Validate the private reference before deriving its cache directory.
            val cache = File(context.cacheDir, "local-ai-runtime/${model.id}")
            check(!cache.exists() || cache.deleteRecursively()) { "Could not remove model cache" }
            files.remove(model)
        }
    }
    fun path(model: LocalModel): File = files.path(model)
}
