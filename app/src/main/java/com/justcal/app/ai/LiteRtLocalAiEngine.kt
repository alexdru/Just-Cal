package com.justcal.app.ai

import android.content.Context
import androidx.core.content.edit
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
    private var requestedBackend = AiBackend.AUTO
    override val actualBackend get() = selectedBackend.takeIf { engine != null }
    override var fallbackReason: String? = null
        private set
    private var contextBudget = 2048
    private var outputBudget = 512
    private var profile = ModelTaskProfile.CUSTOM
    override val reuseAcrossRuns: Boolean get() = profile != ModelTaskProfile.CUSTOM
    private var initializationMillis = 0L
    private var ownsNativeSlot = false
    private var loadedHash: String? = null
    private var loadedModel: LocalModel? = null
    private var fingerprint: String? = null
    private val attempts = mutableListOf<String>()
    private val recoveryFile get() = File(context.noBackupFilesDir, "local-ai-interrupted.json")

    private fun mark(stage: String) {
        val atomic = android.util.AtomicFile(recoveryFile)
        val output = atomic.startWrite()
        try {
            output.write(kotlinx.serialization.json.buildJsonObject {
                put("sha256", kotlinx.serialization.json.JsonPrimitive(loadedHash))
                put("stage", kotlinx.serialization.json.JsonPrimitive(stage))
                put("runtime", kotlinx.serialization.json.JsonPrimitive(AI_RUNTIME_VERSION))
            }.toString().toByteArray())
            atomic.finishWrite(output)
        } catch (failure: Throwable) { atomic.failWrite(output); throw failure }
    }

    @OptIn(ExperimentalApi::class)
    override suspend fun load(model: LocalModel, backend: AiBackend): Long = try {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                check(engine == null) { "Unload the current model first" }
                check(ModelRuntimeLease.slot.tryAcquire()) { "AI runtime is busy" }
                ownsNativeSlot = true
                try {
                    val memory = android.app.ActivityManager.MemoryInfo().also {
                        context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(it)
                    }
                    check(!memory.lowMemory && memory.availMem > memory.threshold) { "Insufficient available memory" }
                    val validation = models.validate(model)
                    val descriptor = ModelCatalog(context).matching(validation.sha256)
                    profile = descriptor?.profile ?: ModelTaskProfile.CUSTOM
                    contextBudget = if (validation.capabilities.dynamicContext)
                        minOf(descriptor?.contextTokens ?: 2048, validation.capabilities.contextTokens)
                        else validation.capabilities.contextTokens
                    require(contextBudget <= 4096) { "Static context exceeds the supported memory budget" }
                    outputBudget = minOf(descriptor?.outputTokens ?: 512, contextBudget / 2)
                    val candidates = BackendSelector.candidates(validation.capabilities)
                    loadedHash = validation.sha256
                    val identity = "${validation.sha256}/$AI_RUNTIME_VERSION/${android.os.Build.FINGERPRINT.hashCode()}"
                    fingerprint = identity
                    attempts.clear()
                    val preferences = context.getSharedPreferences("local-ai-backends", Context.MODE_PRIVATE)
                    val previous = preferences.getString(identity, null)?.let { name -> AiBackend.entries.firstOrNull { it.name == name } }
                    val requested = if (backend == AiBackend.AUTO) previous?.takeIf { it in candidates } ?: candidates.first() else backend
                    fallbackReason = null
                    requestedBackend = backend
                    require(requested in candidates) { "Requested image backend is unsupported" }
                    val loadStarted = System.nanoTime()
                    try { loadCandidate(model, requested, validation.sha256) }
                    catch (failure: Exception) {
                        val alternative = candidates.firstOrNull { it != requested }
                        if (backend != AiBackend.AUTO || alternative == null ||
                            descriptor?.profile != ModelTaskProfile.GENERAL_VISION ||
                            !BackendSelector.mayFallback(failure)) throw failure
                        currentCoroutineContext().ensureActive()
                        fallbackReason = "Accelerator initialization failed: ${failure.javaClass.simpleName}"
                        loadCandidate(model, alternative, validation.sha256)
                    }
                    initializationMillis = elapsedMillis(loadStarted)
                    recoveryFile.delete()
                    initializationMillis
                } catch (failure: Throwable) {
                    recoveryFile.delete()
                    ownsNativeSlot = false
                    ModelRuntimeLease.slot.release()
                    throw failure
                }
            }
        }
    } catch (cancelled: CancellationException) {
        withContext(NonCancellable) { unload() }
        throw cancelled
    }

    @OptIn(ExperimentalApi::class)
    private suspend fun loadCandidate(model: LocalModel, backend: AiBackend, hash: String): Long {
        attempts += backend.name
        mark("INITIALIZING")
        val nativeBackend = if (backend == AiBackend.CPU) Backend.CPU() else Backend.GPU()
        val cache = File(context.cacheDir, "local-ai-runtime/${model.id}/$hash/$AI_RUNTIME_VERSION/${android.os.Build.FINGERPRINT.hashCode()}/${backend.name}/$contextBudget-$outputBudget-${profile.name}-v1")
        check(cache.mkdirs() || cache.isDirectory)
        ExperimentalFlags.enableBenchmark = true
        val candidate = Engine(
            EngineConfig(
                modelPath = models.path(model).absolutePath,
                backend = nativeBackend,
                visionBackend = if (backend == AiBackend.CPU) Backend.CPU() else Backend.GPU(),
                maxNumTokens = contextBudget,
                maxNumImages = 1,
                cacheDir = cache.absolutePath,
            ),
        )
        val start = System.nanoTime()
        return try {
            candidate.initialize()
            currentCoroutineContext().ensureActive()
            initializationMillis = elapsedMillis(start)
            selectedBackend = backend
            engine = candidate
            loadedModel = model
            initializationMillis
        } catch (failure: Throwable) {
            if (candidate.isInitialized()) candidate.close()
            throw failure
        }
    }

    @OptIn(ExperimentalApi::class)
    override suspend fun generate(
        image: PreparedImage, prompt: String, structured: Boolean, onChunk: suspend (String) -> Unit,
    ): AiMetrics = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loaded = checkNotNull(engine) { "Model is not loaded" }
            require(!structured || profile == ModelTaskProfile.GENERAL_VISION) { "Structured nutrition is unverified for this model; use transcription or a custom prompt" }
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
                maxOutputToken = outputBudget,
                samplerConfig = if (profile == ModelTaskProfile.LABEL_TRANSCRIPTION)
                    SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0) else null,
                thinkingConfig = ThinkingConfig(enableThinking = false),
                extraContext = mapOf("enable_thinking" to false),
                enableResponseFormat = structured,
            ))
            mark("INFERENCE")
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
                loadedModel?.takeIf { firstResponseMillis != null }?.let {
                    try { models.recordImageProbe(it, selectedBackend) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Probe persistence is diagnostic evidence; recognition admission is independent. */ }
                }
                fingerprint?.let { context.getSharedPreferences("local-ai-backends", Context.MODE_PRIVATE)
                    .edit { putString(it, selectedBackend.name) } }
                AiMetrics(
                    backend = selectedBackend,
                    requestedBackend = requestedBackend.name, backendAttempts = attempts.toList(), fallbackReason = fallbackReason,
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
                        recoveryFile.delete()
                    }
                }
            }
        }
    }

    override suspend fun unload() = withContext(Dispatchers.IO) {
        mutex.withLock {
            engine?.close()
            engine = null
            loadedModel = null
            if (ownsNativeSlot) {
                ownsNativeSlot = false
                ModelRuntimeLease.slot.release()
            }
        }
    }

    private fun elapsedMillis(start: Long) = (System.nanoTime() - start) / 1_000_000
}
