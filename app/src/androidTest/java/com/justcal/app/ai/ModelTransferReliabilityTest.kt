package com.justcal.app.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real repository/checkpoints/files, simulated HTTP/storage; never uses installed models. */
@RunWith(AndroidJUnit4::class)
class ModelTransferReliabilityTest {
    private class Response(val code: Int = 200, val tag: String? = "\"v1\"",
        val range: String? = null, val body: ByteArray = bytes,
        val length: Long = body.size.toLong(), val type: String = "application/octet-stream",
        val stream: (() -> InputStream)? = null,
        val onDisconnect: () -> Unit = {}) : HttpURLConnection(URL("https://huggingface.co/test")) {
        var disconnected = false
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() { disconnected = true; onDisconnect() }
        override fun getResponseCode() = code
        override fun getHeaderField(name: String?) = when (name) {
            "ETag" -> tag
            "Content-Range" -> range
            else -> null
        }
        override fun getContentType() = type
        override fun getContentLengthLong() = length
        override fun getInputStream() = stream?.invoke() ?: ByteArrayInputStream(body)
    }
    private class Fixture(directory: File? = null) : AutoCloseable {
        val context: Context = ApplicationProvider.getApplicationContext()
        val root = (directory ?: File(context.cacheDir, "transfer-fixture-${UUID.randomUUID()}/local-ai-models")).apply { mkdirs() }
        val descriptor = ModelCatalog(context).models.first().copy(id = "fixture", bytes = bytes.size.toLong(),
            sha256 = hash(bytes), packageAdmission = null, mealNameAdmission = null)
        val responses = ArrayDeque<Response>()
        val requests = CopyOnWriteArrayList<Pair<String?, String?>>()
        var available = Long.MAX_VALUE
        var preflightFailure = false
        var installations = 0
        val partial get() = File(root, ".download-fixture/model.litertlm")
        fun repository() = ModelDownloadRepository(listOf(descriptor), root,
            { if (preflightFailure) throw IOException("ENOSPC") },
            { available },
            { _: URI ->
                responses.removeFirst().also { response ->
                    // Capture headers when responseCode is requested, after connect configures them.
                    lastResponse = response
                }
            },
            { model, file ->
                require(model.bytes == file.length())
                require(model.sha256 == hash(file.readBytes())) { "Fixture integrity check failed" }
                installations++
                val folder = file.parentFile!!
                check(folder.renameTo(File(root, "installed-$installations")))
            })
        var lastResponse: Response? = null
        suspend fun run(repository: ModelDownloadRepository) {
            repository.queue("fixture")
            repository.transfer("fixture")
            lastResponse?.let { requests += it.getRequestProperty("Range") to it.getRequestProperty("If-Range") }
        }
        fun seed(count: Int, etag: String? = "\"v1\"") {
            partial.parentFile!!.mkdirs()
            partial.writeBytes(bytes.copyOf(count))
            File(partial.parentFile, "checkpoint.json").writeText(
                kotlinx.serialization.json.Json.encodeToString(ModelTransfer.serializer(),
                    ModelTransfer("fixture", TransferPhase.DOWNLOADING, count.toLong(), etag))
            )
        }
        override fun close() { root.parentFile!!.deleteRecursively() }
    }
    @Test fun lateCallbackMustNotStartAnotherDownloadAfterInstallation() = runBlocking {
        Fixture().use { f ->
            f.responses.add(Response())
            f.responses.add(Response())
            val repository = f.repository()
            f.run(repository)
            assertEquals(TransferPhase.INSTALLED, repository.state.value["fixture"]?.phase)
            repository.transfer("fixture")
            assertEquals("Late callback created another installed copy", 1, f.installations)
        }
    }
    @Test fun newValidatorMustNeverDescribeOldPartialBytes() = runBlocking {
        Fixture().use { f ->
            f.seed(8)
            f.responses.add(Response(tag = "\"v2\""))
            val repository = f.repository()
            val observed = AtomicLong(-1)
            val watcher = launch(Dispatchers.Unconfined) {
                repository.state.collect { states ->
                    if (states["fixture"]?.run { phase == TransferPhase.DOWNLOADING && etag == "\"v2\"" } == true) {
                        observed.compareAndSet(-1, f.partial.length())
                    }
                }
            }
            try {
                f.run(repository)
                assertEquals("New ETag was checkpointed before truncating old bytes", 0L, observed.get())
            } finally { watcher.cancelAndJoin() }
        }
    }
    @Test fun resume206PreservesPrefixAndReportsProgress() = runBlocking {
        Fixture().use { f ->
            f.seed(8)
            f.responses.add(Response(206, range = "bytes 8-31/32", body = bytes.copyOfRange(8, 32)))
            val repository = f.repository()
            val progress = CopyOnWriteArrayList<ModelTransfer>()
            val watcher = launch(Dispatchers.Unconfined) {
                repository.state.collect { it["fixture"]?.let(progress::add) }
            }
            try {
                f.run(repository)
                assertEquals(1, f.installations)
                assertEquals("bytes=8-", f.requests.single().first)
                assertEquals("\"v1\"", f.requests.single().second)
                assertTrue(progress.map { it.phase }.containsAll(listOf(TransferPhase.QUEUED, TransferPhase.DOWNLOADING, TransferPhase.VERIFYING, TransferPhase.INSTALLED)))
                val counts = progress.filter { it.phase == TransferPhase.DOWNLOADING }.map { it.bytes }
                assertEquals(8L, counts.first())
                assertEquals(32L, counts.last())
                assertTrue(counts.zipWithNext().all { (before, after) -> after >= before })
            } finally { watcher.cancelAndJoin() }
        }
    }
    @Test fun changedEtagAnd416RestartInsteadOfCombiningBytes() = runBlocking {
        for (response in listOf(Response(206, tag = "\"v2\"", range = "bytes 8-31/32", body = bytes.copyOfRange(8, 32)), Response(416))) {
            Fixture().use { f ->
                f.seed(8)
                f.responses.add(response)
                f.responses.add(Response(tag = "\"v2\""))
                val repository = f.repository()
                f.run(repository)
                assertTrue(response.disconnected)
                assertEquals(1, f.installations)
                assertNull(f.requests.last().first)
            }
        }
    }
    @Test fun networkFailureCanResumeAfterRepositoryRecreation() = runBlocking {
        Fixture().use { f ->
            f.responses.add(Response(stream = { object : InputStream() {
                var offset = 0
                override fun read(): Int = error("Use bulk read")
                override fun read(buffer: ByteArray, start: Int, length: Int): Int {
                    if (offset == 8) throw IOException("Disconnected")
                    bytes.copyInto(buffer, start, 0, 8)
                    offset = 8
                    return 8
                }
            } }))
            val repository = f.repository()
            f.run(repository)
            assertEquals(TransferPhase.FAILED, repository.state.value["fixture"]?.phase)
            assertEquals(8L, f.partial.length())
            f.responses.add(Response(206, range = "bytes 8-31/32", body = bytes.copyOfRange(8, 32)))
            val restored = f.repository()
            assertEquals(TransferPhase.PAUSED, restored.state.value["fixture"]?.phase)
            f.run(restored)
            assertEquals(1, f.installations)
        }
    }
    @Test fun insufficientStorageAndInvalidResponsesNeverInstall() = runBlocking {
        for (response in listOf(
            Response(type = "text/html"), Response(length = 12),
            Response(body = ByteArray(32)), Response(206, range = "bytes 1-31/32"),
        )) Fixture().use { f ->
            f.responses.add(response)
            val repository = f.repository()
            f.run(repository)
            assertEquals(TransferPhase.FAILED, repository.state.value["fixture"]?.phase)
            assertEquals(0, f.installations)
            repository.cancel("fixture")
            assertFalse(f.partial.parentFile!!.exists())
        }
        for (preflight in listOf(true, false)) Fixture().use { f ->
            f.preflightFailure = preflight
            f.available = 0
            f.responses.add(Response())
            val repository = f.repository()
            f.run(repository)
            assertEquals(TransferPhase.FAILED, repository.state.value["fixture"]?.phase)
            assertEquals(0, f.installations)
            repository.cancel("fixture")
        }
    }
    @Test fun pauseUnblocksNetworkAndSerializesCancelWithWriter() = runBlocking {
        Fixture().use { f ->
            val reading = CountDownLatch(1)
            val release = CountDownLatch(1)
            val response = Response(stream = { object : InputStream() {
                override fun read(): Int {
                    reading.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    throw IOException("Socket closed")
                }
            } }, onDisconnect = { release.countDown() })
            f.responses.add(response)
            val repository = f.repository()
            repository.queue("fixture")
            val worker = launch(Dispatchers.IO) { repository.transfer("fixture") }
            try {
                assertTrue(reading.await(5, TimeUnit.SECONDS))
                assertTrue(runCatching { repository.queue("fixture") }.isFailure)
                withTimeout(2.seconds) { repository.pause() }
                worker.join()
                assertEquals(TransferPhase.PAUSED, repository.state.value["fixture"]?.phase)
                repository.cancel("fixture")
                assertFalse(f.partial.parentFile!!.exists())
                repository.transfer("fixture")
                assertEquals(0, f.installations)
            } finally { release.countDown(); worker.cancelAndJoin() }
        }
    }
    @Test fun pausedActiveTransferResumesItsPrefix() = runBlocking {
        Fixture().use { f ->
            val reading = CountDownLatch(1)
            val release = CountDownLatch(1)
            f.responses.add(Response(stream = { object : InputStream() {
                var sent = false
                override fun read(): Int = error("Use bulk read")
                override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
                    if (!sent) { sent = true; bytes.copyInto(buffer, offset, 0, 8); return 8 }
                    reading.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    throw IOException("Socket closed")
                }
            } }, onDisconnect = { release.countDown() }))
            val repository = f.repository()
            repository.queue("fixture")
            val worker = launch(Dispatchers.IO) { repository.transfer("fixture") }
            try {
                assertTrue(reading.await(5, TimeUnit.SECONDS))
                withTimeout(2.seconds) { repository.pause() }
                worker.join()
                assertEquals(TransferPhase.PAUSED, repository.state.value["fixture"]?.phase)
                assertEquals(8L, f.partial.length())
                f.responses.add(Response(206, range = "bytes 8-31/32", body = bytes.copyOfRange(8, 32)))
                f.run(repository)
                assertEquals("bytes=8-", f.requests.single().first)
                assertEquals(1, f.installations)
            } finally { release.countDown(); worker.cancelAndJoin() }
        }
    }
    @Test fun completePartialVerifiesWithoutNetworkAndWeakValidatorsRestart() = runBlocking {
        Fixture().use { f ->
            f.seed(32)
            f.run(f.repository())
            assertEquals(1, f.installations)
            assertNull(f.lastResponse)
        }
        for (etag in listOf(null, "W/\"v1\"")) Fixture().use { f ->
            f.seed(8, etag)
            f.responses.add(Response())
            f.run(f.repository())
            assertEquals(1, f.installations)
            assertNull(f.requests.single().first)
        }
    }
    @Test fun disposableModelDeletionDuringTransferKeepsWriterAndRuntimeLeaseSafe() = runBlocking {
        Fixture().use { f ->
            val isolated = object : android.content.ContextWrapper(f.context) {
                override fun getNoBackupFilesDir() = f.root.parentFile!!
                override fun getCacheDir() = File(f.root.parentFile, "cache").apply { mkdirs() }
            }
            val store = LocalModelStore(isolated, LiteRtModelInspector(), ModelCatalog(isolated))
            val model = ModelFiles(f.root).import("owned.litertlm", 32,
                ByteArrayInputStream(bytes)) {}
            val ownedPath = ModelFiles(f.root).path(model)
            val reading = CountDownLatch(1)
            val release = CountDownLatch(1)
            f.responses.add(Response(stream = { object : ByteArrayInputStream(bytes) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    reading.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return super.read(buffer, offset, length)
                }
            } }))
            val repository = f.repository()
            repository.queue("fixture")
            val worker = launch(Dispatchers.IO) { repository.transfer("fixture") }
            try {
                assertTrue(reading.await(5, TimeUnit.SECONDS))
                assertTrue(ModelRuntimeLease.slot.tryAcquire())
                try {
                    assertTrue(runCatching { store.remove(model) }.isFailure)
                    assertTrue(ownedPath.isFile)
                } finally { ModelRuntimeLease.slot.release() }
                store.remove(model)
                assertFalse(ownedPath.exists())
                release.countDown()
                worker.join()
                assertEquals(1, f.installations)
                assertEquals(TransferPhase.INSTALLED, repository.state.value["fixture"]?.phase)
            } finally { release.countDown(); worker.cancelAndJoin() }
        }
    }
    private fun processDirectory(): File {
        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        val id = args.getString("processTransferFixture")
        org.junit.Assume.assumeTrue(id != null)
        require(Regex("[a-f0-9-]{36}").matches(requireNotNull(id)))
        return File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "process-transfer-$id/local-ai-models")
    }
    /** Run alone, wait for PROCESS_TRANSFER_READY, then kill only the emulator test process. */
    @Test fun seedForProcessDeath() = runBlocking {
        Fixture(processDirectory()).use { f ->
            f.responses.add(Response(stream = { object : InputStream() {
                var sent = false
                override fun read(): Int = error("Use bulk read")
                override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
                    if (!sent) { sent = true; bytes.copyInto(buffer, offset, 0, 8); return 8 }
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(
                        0, android.os.Bundle().apply { putString("stream", "\nPROCESS_TRANSFER_READY\n") })
                    Thread.sleep(90_000)
                    error("Expected external process stop")
                }
            } }))
            f.run(f.repository())
        }
    }
    @Test fun resumeAfterProcessDeath() = runBlocking {
        Fixture(processDirectory()).use { f ->
            val restored = f.repository()
            assertEquals(TransferPhase.PAUSED, restored.state.value["fixture"]?.phase)
            assertEquals(8L, f.partial.length())
            f.responses.add(Response(206, range = "bytes 8-31/32", body = bytes.copyOfRange(8, 32)))
            f.run(restored)
            assertEquals(1, f.installations)
        }
    }
    companion object {
        private val bytes = ByteArray(32) { it.toByte() }
        private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
