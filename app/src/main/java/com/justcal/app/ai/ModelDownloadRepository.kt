package com.justcal.app.ai

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class TransferPhase { QUEUED, PAUSED, DOWNLOADING, VERIFYING, INSTALLED, FAILED }

@Serializable
data class ModelTransfer(
    val catalogId: String, val phase: TransferPhase = TransferPhase.PAUSED,
    val bytes: Long = 0, val etag: String? = null, val error: String? = null,
)

/** Strict resume decisions, shared with JVM protocol tests. */
object ModelResumePolicy {
    fun append(code: Int, offset: Long, range: String?, expected: Long, etag: String?, previous: String?): Boolean {
        if (code == 200) return false
        require(code == 206 && offset > 0) { "Download server rejected the request ($code)" }
        val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(range.orEmpty())
            ?: error("Invalid download range")
        require(match.groupValues[1].toLong() == offset &&
            match.groupValues[2].toLong() == expected - 1 &&
            match.groupValues[3].toLong() == expected) { "Download range does not match the pinned model" }
        require(previous != null && previous == etag) { "Download validator changed; retry from the beginning" }
        return true
    }

    fun trusted(url: URI): Boolean = url.scheme == "https" && url.userInfo == null &&
        (url.host == "huggingface.co" || url.host == "cdn-lfs.huggingface.co" ||
            url.host == "cdn-lfs-us-1.hf.co" || url.host == "cdn-lfs-eu-1.hf.co" ||
            url.host == "cas-bridge.xethub.hf.co" || url.host == "us.aws.cdn.hf.co" ||
            url.host == "eu.aws.cdn.hf.co") && (url.port == -1 || url.port == 443)
}

@Singleton
class ModelDownloadRepository internal constructor(
    private val catalogModels: List<ModelDescriptor>,
    private val root: File,
    private val prepareStorage: (Long) -> Unit,
    private val availableBytes: () -> Long,
    private val openConnection: (URI) -> HttpURLConnection,
    private val install: suspend (ModelDescriptor, File) -> Unit,
) {
    @Inject constructor(
        @ApplicationContext context: Context, catalog: ModelCatalog, models: LocalModelStore,
    ) : this(
        catalog.models, File(context.noBackupFilesDir, "local-ai-models"),
        { required ->
            val storage = context.getSystemService(android.os.storage.StorageManager::class.java)
            val volume = storage.getUuidForPath(context.noBackupFilesDir)
            require(storage.getAllocatableBytes(volume) >= required) { "Insufficient private storage" }
            storage.allocateBytes(volume, required)
        },
        { context.noBackupFilesDir.usableSpace },
        { it.toURL().openConnection() as HttpURLConnection },
        { descriptor, file -> models.installDownload(descriptor, file); Unit },
    )
    private val gate = Mutex()
    private val mutable = MutableStateFlow<Map<String, ModelTransfer>>(restore())
    val state = mutable.asStateFlow()
    @Volatile private var active: Job? = null
    @Volatile private var activeId: String? = null
    @Volatile private var activeConnection: HttpURLConnection? = null
    private val stopped = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun folder(id: String): File {
        require(catalogModels.any { it.id == id })
        return File(root, ".download-$id").also { check(it.mkdirs() || it.isDirectory) }
    }

    private fun restore(): Map<String, ModelTransfer> = catalogModels.mapNotNull { model ->
        val directory = File(root, ".download-${model.id}")
        val checkpoint = File(directory, "checkpoint.json")
        if (!directory.exists()) null else {
            val previous = runCatching { Json.decodeFromString<ModelTransfer>(checkpoint.readText()) }.getOrNull()
            model.id to ModelTransfer(model.id, bytes = File(directory, "model.litertlm").length(),
                etag = previous?.etag, error = previous?.error)
        }
    }.toMap()

    private fun publish(value: ModelTransfer) {
        val checkpoint = AtomicFile(File(folder(value.catalogId), "checkpoint.json"))
        val output = checkpoint.startWrite()
        try {
            output.write(Json.encodeToString(value).toByteArray())
            checkpoint.finishWrite(output)
        } catch (failure: Throwable) { checkpoint.failWrite(output); throw failure }
        mutable.value = mutable.value + (value.catalogId to value)
    }

    suspend fun queue(id: String) = withContext(Dispatchers.IO) {
        check(gate.tryLock()) { "Another model transfer is active" }
        try {
            check(state.value.values.none { it.phase in setOf(TransferPhase.QUEUED, TransferPhase.DOWNLOADING, TransferPhase.VERIFYING) })
            val previous = state.value[id]
            stopped.remove(id)
            publish(ModelTransfer(id, TransferPhase.QUEUED, File(folder(id), "model.litertlm").length(), previous?.etag, previous?.error))
        } finally { gate.unlock() }
    }

    suspend fun transfer(id: String) = withContext(Dispatchers.IO) {
        if (!gate.tryLock()) return@withContext
        if (id in stopped || state.value[id]?.phase != TransferPhase.QUEUED) {
            gate.unlock()
            return@withContext
        }
        active = currentCoroutineContext().job
        activeId = id
        try {
            ensureTransferActive(id)
            val descriptor = catalogModels.single { it.id == id }
            val file = File(folder(id), "model.litertlm")
            var offset = file.length()
            if (offset == descriptor.bytes && state.value[id]?.error != null) {
                check(file.delete()) { "Could not restart damaged download" }
                offset = 0
            }
            require(offset <= descriptor.bytes) { "Partial model exceeds expected size; cancel and retry" }
            prepareStorage(descriptor.bytes - offset + MODEL_STORAGE_RESERVE)
            var etag = state.value[id]?.etag
            if (offset == descriptor.bytes) {
                publish(ModelTransfer(id, TransferPhase.VERIFYING, offset, etag))
            } else {
                // A partial file without a strong validator is restarted rather than combined.
                if (etag == null || etag.startsWith("W/")) { offset = 0; etag = null }
                var connection = connect(descriptor.url, offset, etag)
                if (connection.responseCode == 416 || (offset > 0 && connection.responseCode == 206 &&
                    connection.getHeaderField("ETag") != etag)) {
                    connection.disconnect()
                    offset = 0
                    etag = null
                    connection = connect(descriptor.url, 0, null)
                }
                try {
                    val code = connection.responseCode
                    if (code == 401 || code == 403) error("Download unavailable; import the model with the document picker")
                    val responseTag = connection.getHeaderField("ETag")?.takeUnless { it.startsWith("W/") }
                    val append = if (code == 416) {
                        error("Resume range rejected; cancel the partial download and retry")
                    } else ModelResumePolicy.append(code, offset, connection.getHeaderField("Content-Range"),
                        descriptor.bytes, responseTag, etag)
                    if (!append) offset = 0
                    val contentType = connection.contentType.orEmpty().lowercase()
                    require("text/" !in contentType && "html" !in contentType && "json" !in contentType) {
                        "Server returned a page instead of model data"
                    }
                    val length = connection.contentLengthLong
                    require(length < 0 || length == descriptor.bytes - offset) { "Unexpected model download length" }
                    etag = responseTag
                    java.io.FileOutputStream(file, append).use { target ->
                        // Persist truncation before associating a new validator with this file.
                        // Otherwise process death could resume old bytes using the new ETag.
                        if (!append) target.fd.sync()
                        publish(ModelTransfer(id, TransferPhase.DOWNLOADING, offset, etag))
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(1024 * 1024)
                            var lastCheckpoint = offset
                            while (true) {
                                ensureTransferActive(id)
                                val count = input.read(buffer)
                                ensureTransferActive(id)
                                if (count < 0) break
                                require(offset + count <= descriptor.bytes) { "Download exceeds pinned size" }
                                require(availableBytes() > MODEL_STORAGE_RESERVE) { "Insufficient private storage" }
                                target.write(buffer, 0, count)
                                offset += count
                                mutable.value = mutable.value + (id to ModelTransfer(id, TransferPhase.DOWNLOADING, offset, etag))
                                if (offset - lastCheckpoint >= 16 * 1024 * 1024) {
                                    target.fd.sync()
                                    publish(ModelTransfer(id, TransferPhase.DOWNLOADING, offset, etag))
                                    lastCheckpoint = offset
                                }
                            }
                        }
                        target.fd.sync()
                    }
                } finally { connection.disconnect() }
                require(offset == descriptor.bytes) { "Download interrupted; resume to finish" }
                publish(ModelTransfer(id, TransferPhase.VERIFYING, offset, etag))
            }
            install(descriptor, file)
            mutable.value = mutable.value + (id to ModelTransfer(id, TransferPhase.INSTALLED, descriptor.bytes))
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { checkpointStopped(id, TransferPhase.PAUSED, null) }
            throw cancelled
        } catch (failure: Exception) {
            if (!currentCoroutineContext().isActive || id in stopped) {
                withContext(NonCancellable) { checkpointStopped(id, TransferPhase.PAUSED, null) }
                throw CancellationException("Model transfer stopped", failure)
            }
            checkpointStopped(id, TransferPhase.FAILED, failure.message?.takeUnless { "/" in it } ?: "Model download or validation failed")
        } catch (_: LinkageError) {
            checkpointStopped(id, TransferPhase.FAILED, "Model runtime unavailable")
        } finally {
            activeConnection?.disconnect()
            activeConnection = null
            active = null
            activeId = null
            gate.unlock()
        }
    }

    private fun checkpointStopped(id: String, phase: TransferPhase, error: String?) {
        val previous = state.value[id]
        val value = ModelTransfer(id, phase, File(root, ".download-$id/model.litertlm").length(),
            if (previous?.phase == TransferPhase.VERIFYING) null else previous?.etag, error)
        try { publish(value) }
        catch (_: Exception) { mutable.value = mutable.value + (id to value.copy(error = "Insufficient storage for download checkpoint")) }
    }

    suspend fun pause() {
        stopped.addAll(state.value.values.filter { it.phase == TransferPhase.QUEUED }.map { it.catalogId })
        activeId?.let(stopped::add)
        val work = active
        work?.cancel()
        // Coroutine cancellation alone does not unblock a synchronous socket read.
        withContext(Dispatchers.IO) { activeConnection?.disconnect() }
        work?.join()
        withContext(Dispatchers.IO) {
            state.value.values.filter { it.phase == TransferPhase.QUEUED }.forEach {
                publish(it.copy(phase = TransferPhase.PAUSED))
            }
        }
    }

    suspend fun cancel(id: String) = withContext(Dispatchers.IO) {
        stopped.add(id)
        if (activeId == id) pause()
        gate.lock()
        try {
            check(folder(id).deleteRecursively()) { "Could not remove partial download" }
            mutable.value = mutable.value - id
        } finally { gate.unlock() }
    }

    private suspend fun ensureTransferActive(id: String) {
        currentCoroutineContext().ensureActive()
        if (id in stopped) throw CancellationException("Model transfer stopped")
    }

    private fun connect(url: String, offset: Long, etag: String?): HttpURLConnection {
        var location = URI(url)
        repeat(6) {
            require(ModelResumePolicy.trusted(location)) { "Untrusted model download source" }
            val connection = openConnection(location)
            activeConnection = connection
            if (active?.isActive == false || activeId?.let { it in stopped } == true) {
                connection.disconnect()
                throw CancellationException("Model transfer stopped")
            }
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0 && etag != null) {
                connection.setRequestProperty("Range", "bytes=$offset-")
                connection.setRequestProperty("If-Range", etag)
            }
            val code = try { connection.responseCode }
                catch (failure: Exception) { connection.disconnect(); throw failure }
            if (code !in listOf(301, 302, 303, 307, 308)) return connection
            val next = connection.getHeaderField("Location")
            connection.disconnect()
            require(next != null) { "Missing download redirect" }
            location = location.resolve(next)
        }
        error("Too many model download redirects")
    }
}
