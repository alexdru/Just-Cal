package com.justcal.app.ai

import android.app.Service
import android.content.Intent
import android.os.*
import com.justcal.app.diagnostics.DiagnosticAiAttempt
import kotlinx.serialization.json.Json

/** Disposable IPC/death fixture. Never loads a model or accesses diary/preferences. */
class PackageWorkerProbeService : Service() {
    private var client: Messenger? = null
    private var mode = ""
    private val messenger by lazy { Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            PackageInferenceProtocol.RUN -> {
                client = message.replyTo
                mode = message.data.getString("model").orEmpty()
                send(PackageInferenceProtocol.READY, Bundle().apply { putInt("pid", Process.myPid()) })
                send(PackageInferenceProtocol.STAGE, Bundle().apply {
                    putString("attempt", Json.encodeToString(attempt("PROBE")))
                })
            }
            PackageInferenceProtocol.ACK -> when {
                mode.endsWith("001") -> android.system.Os.kill(Process.myPid(), android.system.OsConstants.SIGABRT)
                mode.endsWith("002") || mode.endsWith("004") -> Unit // Wait for explicit cancellation.
                else -> send(PackageInferenceProtocol.DONE, Bundle().apply {
                    putString("attempt", Json.encodeToString(attempt("COMPLETED").copy(rawOutput = "fixture")))
                })
            }
            PackageInferenceProtocol.CANCEL -> if (!mode.endsWith("004")) send(PackageInferenceProtocol.FAILED,
                Bundle().apply { putString("error", "CANCELLED") })
        }
        true
    }) }
    private fun attempt(stage: String) = DiagnosticAiAttempt(stage, "fixture", runtimeVersion = "fixture",
        profile = "fixture", requestedBackend = "AUTO", processId = Process.myPid())
    private fun send(what: Int, data: Bundle) {
        client?.send(Message.obtain(null, what).apply { this.data = data })
    }
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onUnbind(intent: Intent?): Boolean { Process.killProcess(Process.myPid()); return false }
}
