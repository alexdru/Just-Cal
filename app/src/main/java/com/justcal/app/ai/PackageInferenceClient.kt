package com.justcal.app.ai

import android.content.*
import android.os.*
import com.justcal.app.camera.PreparedImage
import com.justcal.app.diagnostics.DiagnosticAiAttempt
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

internal class PackageWorkerDied(val pid: Int) : IllegalStateException("Package model process exited")

/** Holds the parent-process runtime lease until remote cleanup or confirmed Binder death. */
class PackageInferenceClient internal constructor(
    private val context: Context,
    private val component: ComponentName,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        context, ComponentName(context, PackageInferenceService::class.java))

    suspend fun generate(model: LocalModel, image: PreparedImage,
        onStage: suspend (DiagnosticAiAttempt) -> Unit): DiagnosticAiAttempt = withContext(Dispatchers.IO) {
        check(ModelRuntimeLease.slot.tryAcquire()) { "AI runtime is busy" }
        try { withTimeout(120.seconds) { request(model, image, onStage) } }
        finally { ModelRuntimeLease.slot.release() }
    }

    private suspend fun request(model: LocalModel, image: PreparedImage,
        onStage: suspend (DiagnosticAiAttempt) -> Unit): DiagnosticAiAttempt {
        val ready = CompletableDeferred<Messenger>()
        val ended = CompletableDeferred<Unit>()
        val dead = CompletableDeferred<Unit>()
        val events = Channel<Message>(Channel.UNLIMITED)
        var pid = 0
        var remote: Messenger? = null
        var bound = false
        val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.what == PackageInferenceProtocol.READY) pid = message.data.getInt("pid")
            if (message.what in listOf(PackageInferenceProtocol.DONE, PackageInferenceProtocol.FAILED)) ended.complete(Unit)
            events.trySend(Message.obtain().apply { copyFrom(message) })
            true
        })
        val death = IBinder.DeathRecipient {
            dead.complete(Unit)
            ended.complete(Unit)
            events.close(PackageWorkerDied(pid))
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                try {
                    binder.linkToDeath(death, 0)
                    ready.complete(Messenger(binder))
                } catch (_: RemoteException) {
                    ready.completeExceptionally(PackageWorkerDied(pid))
                    death.binderDied()
                }
            }
            override fun onServiceDisconnected(name: ComponentName) { death.binderDied() }
            override fun onBindingDied(name: ComponentName) { death.binderDied() }
            override fun onNullBinding(name: ComponentName) {
                ready.completeExceptionally(IllegalStateException("Package model unavailable"))
                ended.complete(Unit)
            }
        }
        try {
            withContext(Dispatchers.Main.immediate) {
                bound = context.bindService(Intent().setComponent(component), connection, Context.BIND_AUTO_CREATE)
            }
            check(bound) { "Package model unavailable" }
            remote = withTimeout(15.seconds) { ready.await() }
            remote.send(Message.obtain(null, PackageInferenceProtocol.RUN).apply {
                replyTo = reply
                data = Bundle().apply {
                    putString("model", model.id)
                    putString("image", Json.encodeToString(image))
                }
            })
            for (event in events) when (event.what) {
                PackageInferenceProtocol.STAGE -> {
                    onStage(Json.decodeFromString(requireNotNull(event.data.getString("attempt"))))
                    remote.send(Message.obtain(null, PackageInferenceProtocol.ACK))
                }
                PackageInferenceProtocol.DONE ->
                    return Json.decodeFromString(requireNotNull(event.data.getString("attempt")))
                PackageInferenceProtocol.FAILED ->
                    error(event.data.getString("error") ?: "Package model failed")
            }
            throw PackageWorkerDied(pid)
        } finally {
            withContext(NonCancellable) {
                if (remote != null && !ended.isCompleted) {
                    runCatching { remote.send(Message.obtain(null, PackageInferenceProtocol.CANCEL)) }
                    // Give normal cancellation its native join; a hung worker is disposed by unbind.
                    withTimeoutOrNull(15.seconds) { ended.await() }
                }
                if (bound) withContext(Dispatchers.Main.immediate) { context.unbindService(connection) }
                if (remote != null) {
                    // onUnbind kills the single-use process; never release images/lease ahead of it.
                    dead.await()
                    runCatching { remote.binder.unlinkToDeath(death, 0) }
                }
                events.cancel()
            }
        }
    }
}
