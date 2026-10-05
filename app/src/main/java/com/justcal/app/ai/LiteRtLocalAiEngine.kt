package com.justcal.app.ai

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litertlm.*
import com.justcal.app.camera.ImagePreprocessor
import com.justcal.app.camera.PreparedImage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock

/** One loaded engine, one fresh conversation per run. All JNI work and cleanup is off the UI thread. */
class LiteRtLocalAiEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: LocalModelStore,
    private val images: ImagePreprocessor,
) : LocalAiEngine {
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var selectedBackend = AiBackend.CPU
    private var initializationMillis = 0L
    private var ownsNativeSlot = false

    @OptIn(ExperimentalApi::class)
    override suspend fun load(model: LocalModel, backend: AiBackend): Long = try {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                check(engine == null) { "Unload the current model first" }
                nativeSlot.acquire()
                ownsNativeSlot = true
                try {
                    val nativeBackend = if (backend == AiBackend.CPU) Backend.CPU() else Backend.GPU()
                    val cache = File(context.cacheDir, "local-ai-runtime/${model.id}/${backend.name}")
                    check(cache.mkdirs() || cache.isDirectory)
                    ExperimentalFlags.enableBenchmark = true
                    val candidate = Engine(
                        EngineConfig(
                            modelPath = models.path(model).absolutePath,
                            backend = nativeBackend,
                            visionBackend = if (backend == AiBackend.CPU) Backend.CPU() else Backend.GPU(),
                            maxNumTokens = 4096,
                            maxNumImages = 1,
                            cacheDir = cache.absolutePath,
                        ),
                    )
                    val start = System.nanoTime()
                    try {
                        candidate.initialize()
                        currentCoroutineContext().ensureActive()
                        initializationMillis = elapsedMillis(start)
                        selectedBackend = backend
                        engine = candidate
                        initializationMillis
                    } catch (failure: Throwable) {
                        if (candidate.isInitialized()) candidate.close()
                        throw failure
                    }
                } catch (failure: Throwable) {
                    ownsNativeSlot = false
                    nativeSlot.release()
                    throw failure
                }
            }
        }
    } catch (cancelled: CancellationException) {
        // Initialization can finish just as withContext returns to a cancelled caller.
        withContext(NonCancellable) { unload() }
        throw cancelled
    }

    @OptIn(ExperimentalApi::class)
    override suspend fun generate(
        image: PreparedImage, prompt: String, structured: Boolean, onChunk: suspend (String) -> Unit,
    ): AiMetrics = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loaded = checkNotNull(engine) { "Model is not loaded" }
            val start = System.nanoTime()
            images.verify(image)
            // Generic decoder supplies upright sRGB pixels. LiteRT-LM owns tensor preprocessing.
            val bitmap = images.decode(image)
            val imageBytes = try {
                ByteArrayOutputStream().use { bytes ->
                    val format = if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    check(bitmap.compress(format, 95, bytes)) { "Could not encode image" }
                    bytes.toByteArray()
                }
            } finally { bitmap.recycle() }
            currentCoroutineContext().ensureActive()
            val conversation = loaded.createConversation(ConversationConfig(
                maxOutputToken = 1024,
                thinkingConfig = ThinkingConfig(enableThinking = false),
                extraContext = mapOf("enable_thinking" to false),
                enableResponseFormat = structured,
            ))
            val finished = CompletableDeferred<Unit>()
            // The native output budget bounds this channel; preserve every streamed chunk.
            val chunks = Channel<String>(Channel.UNLIMITED)
            var started = false
            var firstResponseMillis: Long? = null
            try {
                conversation.sendMessageAsync(
                    Contents.of(Content.ImageBytes(imageBytes), Content.Text(prompt)),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            val text = message.toString()
                            if (text.isNotEmpty()) chunks.trySend(text)
                        }
                        override fun onDone() { finished.complete(Unit); chunks.close() }
                        override fun onError(throwable: Throwable) {
                            finished.complete(Unit)
                            chunks.close(throwable)
                        }
                    },
                    responseFormat = if (structured) ResponseFormat.json(NutritionExtraction.schema) else null,
                )
                started = true
                for (text in chunks) {
                    if (firstResponseMillis == null) firstResponseMillis = elapsedMillis(start)
                    onChunk(text)
                }
                val benchmark = runCatching { conversation.getBenchmarkInfo() }
                val info = benchmark.getOrNull()
                AiMetrics(
                    backend = selectedBackend,
                    initializationMillis = initializationMillis,
                    inferenceMillis = elapsedMillis(start),
                    firstResponseMillis = firstResponseMillis,
                    nativeFirstTokenSeconds = info?.timeToFirstTokenInSecond?.takeIf { it.isFinite() && (it >= 0) },
                    prefillTokensPerSecond = info?.lastPrefillTokensPerSecond?.takeIf { it.isFinite() && (it > 0) },
                    decodeTokensPerSecond = info?.lastDecodeTokensPerSecond?.takeIf { it.isFinite() && (it > 0) },
                    benchmarkError = benchmark.exceptionOrNull()?.message,
                )
            } finally {
                // Flow cancellation alone does not cancel native inference. Wait for the terminal
                // callback before freeing the conversation, encoded image or engine.
                withContext(NonCancellable) {
                    try {
                        if (started && !finished.isCompleted) {
                            conversation.cancelProcess()
                            finished.await()
                        }
                    } finally {
                        chunks.close()
                        conversation.close()
                    }
                }
            }
        }
    }

    override suspend fun unload() = withContext(Dispatchers.IO) {
        mutex.withLock {
            engine?.close()
            engine = null
            if (ownsNativeSlot) {
                ownsNativeSlot = false
                nativeSlot.release()
            }
        }
    }

    // Re-entering while an old entry is cancelling must not initialize a second multi-GB engine.
    private companion object { val nativeSlot = Semaphore(1) }

    private fun elapsedMillis(start: Long) = (System.nanoTime() - start) / 1_000_000
}
