package com.justcal.app.ai

import android.app.Service
import android.content.Intent
import android.os.*
import com.justcal.app.camera.ImagePreprocessor
import com.justcal.app.camera.PreparedImage
import com.justcal.app.diagnostics.DiagnosticAiAttempt
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/** Small metadata-only IPC. Images and weights remain private files, never Binder payloads. */
internal object PackageInferenceProtocol {
    const val RUN = 1
    const val CANCEL = 2
    const val ACK = 3
    const val STAGE = 4
    const val DONE = 5
    const val FAILED = 6
    const val READY = 7
}

/** Dedicated, single-use process: a JNI abort cannot destroy the diary/OCR process. */
@AndroidEntryPoint
class PackageInferenceService : Service() {
    @Inject lateinit var runtime: LiteRtLocalAiEngine
    @Inject lateinit var models: LocalModelStore
    @Inject lateinit var images: ImagePreprocessor
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null
    @Volatile private var acknowledgment: CompletableDeferred<Unit>? = null
    private var client: Messenger? = null
    private val clientDeath = IBinder.DeathRecipient { Process.killProcess(Process.myPid()) }
    private val incoming by lazy {
        Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.sendingUid != applicationInfo.uid) return@Handler true
            when (message.what) {
                PackageInferenceProtocol.RUN -> if (client == null) {
                    client = message.replyTo
                    client?.binder?.linkToDeath(clientDeath, 0)
                    reply(PackageInferenceProtocol.READY, Bundle().apply { putInt("pid", Process.myPid()) })
                    val request = Bundle(message.data)
                    work = scope.launch { run(request) }
                }
                PackageInferenceProtocol.CANCEL -> work?.cancel()
                PackageInferenceProtocol.ACK -> acknowledgment?.complete(Unit)
            }
            true
        })
    }
    override fun onBind(intent: Intent): IBinder = incoming.binder

    private fun reply(what: Int, data: Bundle = Bundle()) {
        client?.send(Message.obtain(null, what).apply { this.data = data })
    }
    private suspend fun stage(value: DiagnosticAiAttempt) {
        val saved = CompletableDeferred<Unit>()
        acknowledgment = saved
        reply(PackageInferenceProtocol.STAGE, Bundle().apply { putString("attempt", Json.encodeToString(value)) })
        // Persist the checkpoint in the parent before entering JNI.
        withTimeout(15.seconds) { saved.await() }
    }
    private suspend fun run(request: Bundle) {
        var result: DiagnosticAiAttempt? = null
        var failed: String? = null
        try {
            val id = requireNotNull(request.getString("model"))
            val files = ModelFiles(File(noBackupFilesDir, "local-ai-models"))
            // Read only: do not run partial-import cleanup in a second process.
            val model = files.list().single { it.id == id }
            val validation = models.validate(model)
            val descriptor = requireNotNull(ModelCatalog(this).matching(validation.sha256))
            require(descriptor.supportsRecognition("package", validation))
            val image = Json.decodeFromString<PreparedImage>(requireNotNull(request.getString("image")))
            images.verify(image)
            val macro = descriptor.profile == ModelTaskProfile.GENERAL_VISION
            var attempt = DiagnosticAiAttempt(
                stage = "PACKAGE_MODEL_INITIALIZING", artifactHash = validation.sha256,
                revision = descriptor.revision, runtimeVersion = AI_RUNTIME_VERSION,
                profile = descriptor.profile.name + "/" + if (macro) PackageMacroAssistance.PROFILE_VERSION else LabelImageCropper.PROFILE_VERSION,
                requestedBackend = "AUTO", processId = Process.myPid(),
                prompt = if (macro) PackageMacroAssistance.PROMPT else "OCR:",
            )
            stage(attempt)
            val initialization = runtime.load(model, AiBackend.AUTO)
            attempt = attempt.copy(stage = "PACKAGE_MODEL_INFERENCE", actualBackend = runtime.actualBackend?.name,
                initializationMs = initialization)
            stage(attempt)
            val raw = StringBuilder()
            val metrics = withTimeout(90.seconds) {
                runtime.generate(image, if (macro) PackageMacroAssistance.PROMPT else "OCR:", macro) {
                    require(raw.length + it.length <= 16_000)
                    raw.append(it)
                }
            }
            result = attempt.copy(stage = "PACKAGE_MODEL_COMPLETED", rawOutput = raw.toString(),
                actualBackend = metrics.backend.name, backendAttempts = metrics.backendAttempts,
                fallbackReason = metrics.fallbackReason, inferenceMs = metrics.inferenceMillis,
                firstResponseMs = metrics.firstResponseMillis)
        } catch (_: CancellationException) {
            failed = "PACKAGE_MODEL_CANCELLED"
        } catch (failure: Exception) {
            failed = "PACKAGE_MODEL_FAILED:${failure.javaClass.simpleName}"
        } catch (failure: LinkageError) {
            failed = "PACKAGE_MODEL_LINKAGE:${failure.javaClass.simpleName}"
        } finally {
            // Do not announce completion until native processing and disposal have finished.
            withContext(NonCancellable) { runtime.unload() }
        }
        reply(if (failed == null) PackageInferenceProtocol.DONE else PackageInferenceProtocol.FAILED,
            Bundle().apply {
                result?.let { putString("attempt", Json.encodeToString(it)) }
                putString("error", failed)
            })
    }
    override fun onUnbind(intent: Intent?): Boolean {
        // Also contains a JNI call that never finishes cancellation. Only this dedicated process dies.
        Process.killProcess(Process.myPid())
        return false
    }
    override fun onDestroy() {
        client?.binder?.unlinkToDeath(clientDeath, 0)
        scope.cancel()
        super.onDestroy()
    }
}
