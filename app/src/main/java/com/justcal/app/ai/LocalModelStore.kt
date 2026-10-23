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

    fun validation(model: LocalModel): ModelValidation? = runCatching {
        Json.decodeFromString<ModelValidation>(File(path(model).parentFile, "validation.json").readText())
    }.getOrNull()

    fun saveValidation(model: LocalModel, value: ModelValidation) = writeValidation(path(model).parentFile!!, value)

    internal fun writeValidation(folder: File, value: ModelValidation) {
        val pending = File(folder, ".validation")
        pending.outputStream().use { output ->
            output.write(Json.encodeToString(value).toByteArray())
            output.fd.sync()
        }
        check(pending.renameTo(File(folder, "validation.json"))) { "Could not save model validation" }
    }

    fun removePartialImports() {
        directory.listFiles().orEmpty().filter { it.name.startsWith(".import-") }.forEach { it.deleteRecursively() }
    }

    suspend fun import(name: String, expectedSize: Long?, stream: InputStream,
        validate: suspend (File) -> ModelValidation? = { null }, onProgress: (Long) -> Unit): LocalModel {
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
                    require(temporary.usableSpace > MODEL_STORAGE_RESERVE) { "Insufficient private storage" }
                    target.write(buffer, 0, count)
                    onProgress(total)
                }
                target.fd.sync()
            }
            require(total > 0 && (expectedSize == null || expectedSize < 0 || total == expectedSize)) {
                "Empty or incomplete model file"
            }
            val validation = validate(File(temporary, "model.litertlm"))
            File(temporary, "metadata.json").outputStream().use { output ->
                output.write(buildJsonObject { put("name", name) }.toString().toByteArray())
                output.fd.sync()
            }
            validation?.let { writeValidation(temporary, it) }
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
class LocalModelStore @Inject constructor(@ApplicationContext private val context: Context,
    private val inspector: LiteRtModelInspector, private val catalog: ModelCatalog) {
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
                    require(name?.endsWith(".litertlm", ignoreCase = true) == true && name.length <= 255) {
                        "Select a .litertlm file"
                    }
                    size?.takeIf { it > 0 }?.let { expected ->
                        require(expected <= 16L * 1024 * 1024 * 1024) { "Model exceeds 16 GiB import limit" }
                        val storage = context.getSystemService(StorageManager::class.java)
                        val volume = storage.getUuidForPath(context.noBackupFilesDir)
                        val required = expected + MODEL_STORAGE_RESERVE
                        require(storage.getAllocatableBytes(volume) >= required) { "Insufficient private storage" }
                        storage.allocateBytes(volume, required)
                    }
                    context.contentResolver.openInputStream(uri).use { stream ->
                        files.import(requireNotNull(name) { "Model filename unavailable" }, size,
                            requireNotNull(stream) { "Model file unavailable" }, validate = { inspect(it) }, onProgress = progress).also { produced.set(it) }
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
        check(ModelRuntimeLease.slot.tryAcquire()) { "Model is in use; unload it first" }
        try { lock.withLock {
            files.path(model) // Validate the private reference before deriving its cache directory.
            val cache = File(context.cacheDir, "local-ai-runtime/${model.id}")
            check(!cache.exists() || cache.deleteRecursively()) { "Could not remove model cache" }
            files.remove(model)
        } } finally { ModelRuntimeLease.slot.release() }
    }

    suspend fun validate(model: LocalModel): ModelValidation = withContext(Dispatchers.IO) {
        lock.withLock {
            // Reinspect legacy files once; never delete an unsupported legacy artifact.
            files.validation(model)?.takeIf { it.runtime == AI_RUNTIME_VERSION }
                ?: inspect(files.path(model)).also { files.saveValidation(model, it) }
        }
    }

    suspend fun recordImageProbe(model: LocalModel, backend: AiBackend) = withContext(Dispatchers.IO) {
        lock.withLock {
            val validation = requireNotNull(files.validation(model))
            files.saveValidation(model, validation.copy(imageProbe = probeFingerprint(backend)))
        }
    }
    fun probeFingerprint(backend: AiBackend) = "$AI_RUNTIME_VERSION/${android.os.Build.FINGERPRINT}/${backend.name}"

    private suspend fun inspect(file: File, expectedHash: String? = null): ModelValidation {
        val result = inspector.inspect(file, expectedHash)
        return result.copy(catalogId = catalog.matching(result.sha256)?.id)
    }

    suspend fun installDownload(descriptor: ModelDescriptor, file: File): LocalModel = withContext(Dispatchers.IO) {
        lock.withLock {
            require(file.canonicalFile == File(context.noBackupFilesDir,
                "local-ai-models/.download-${descriptor.id}/model.litertlm").canonicalFile)
            require(file.length() == descriptor.bytes)
            val validation = inspect(file, descriptor.sha256)
            require(validation.sha256 == descriptor.sha256) { "Model integrity check failed" }
            val folder = file.parentFile!!
            File(folder, "metadata.json").outputStream().use {
                it.write(buildJsonObject { put("name", descriptor.filename) }.toString().toByteArray())
                it.fd.sync()
            }
            files.writeValidation(folder, validation)
            val id = UUID.randomUUID().toString()
            val committed = File(folder.parentFile, id)
            currentCoroutineContext().ensureActive()
            check(folder.renameTo(committed)) { "Could not commit downloaded model" }
            LocalModel(id, descriptor.filename, descriptor.bytes)
        }
    }
    fun path(model: LocalModel): File = files.path(model)
}
