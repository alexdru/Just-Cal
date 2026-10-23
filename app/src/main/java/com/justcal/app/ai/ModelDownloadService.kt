package com.justcal.app.ai

import kotlin.time.Duration.Companion.seconds

import android.app.*
import android.app.job.*
import android.content.*
import android.os.Build
import android.os.IBinder
import android.os.PersistableBundle
import androidx.core.app.NotificationCompat
import com.justcal.app.MainActivity
import com.justcal.app.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.sample

private const val CHANNEL = "ai-model-downloads"
private const val NOTIFICATION = 420
private const val DOWNLOAD_JOB = 421

/** Called only by an explicit Download/Resume action in a visible Activity. */
fun startModelDownload(context: Context, model: ModelDescriptor) {
    if (Build.VERSION.SDK_INT >= 34) {
        val extras = PersistableBundle().apply { putString("model", model.id) }
        val job = JobInfo.Builder(DOWNLOAD_JOB, ComponentName(context, ModelDownloadJobService::class.java))
            .setUserInitiated(true).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setEstimatedNetworkBytes(model.bytes, 0).setExtras(extras).build()
        check(context.getSystemService(JobScheduler::class.java).schedule(job) == JobScheduler.RESULT_SUCCESS) {
            "Unable to schedule model download"
        }
    } else {
        context.startForegroundService(Intent(context, ModelDownloadService::class.java).putExtra("model", model.id))
    }
}

fun stopModelDownload(context: Context) {
    if (Build.VERSION.SDK_INT >= 34) context.getSystemService(JobScheduler::class.java).cancel(DOWNLOAD_JOB)
    context.stopService(Intent(context, ModelDownloadService::class.java))
}

private fun notification(context: Context, transfer: ModelTransfer?, total: Long): Notification {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.ai_models), NotificationManager.IMPORTANCE_LOW))
    val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val cancel = PendingIntent.getBroadcast(context, 0, Intent(context, ModelDownloadCancelReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    return NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_package)
        .setContentTitle(context.getString(R.string.ai_models)).setContentIntent(open)
        .setContentText(context.getString(if (transfer?.phase == TransferPhase.VERIFYING) R.string.ai_verifying else R.string.ai_downloading))
        .setOnlyAlertOnce(true).setOngoing(true)
        .setProgress(100, if (total > 0) ((transfer?.bytes ?: 0) * 100 / total).toInt() else 0,
            transfer?.phase != TransferPhase.DOWNLOADING)
        .addAction(0, context.getString(R.string.ai_pause), cancel).build()
}

@AndroidEntryPoint
class ModelDownloadCancelReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: ModelDownloadRepository
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.pause()
                if (Build.VERSION.SDK_INT >= 34) context.getSystemService(JobScheduler::class.java).cancel(DOWNLOAD_JOB)
                context.stopService(Intent(context, ModelDownloadService::class.java))
            } finally { result.finish() }
        }
    }
}

@AndroidEntryPoint
class ModelDownloadService : Service() {
    @Inject lateinit var repository: ModelDownloadRepository
    @Inject lateinit var catalog: ModelCatalog
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var work: Job? = null
    override fun onBind(intent: Intent?): IBinder? = null
    @OptIn(FlowPreview::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val model = catalog.models.firstOrNull { it.id == intent?.getStringExtra("model") } ?: run {
            stopSelf(startId); return START_NOT_STICKY
        }
        if (work?.isActive == true) return START_NOT_STICKY
        if (repository.state.value[model.id]?.phase != TransferPhase.QUEUED) { stopSelf(startId); return START_NOT_STICKY }
        startForeground(NOTIFICATION, notification(this, null, model.bytes))
        work = scope.launch {
            val updates = launch {
                repository.state.sample(1.seconds).collect { states ->
                    if (Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(
                            this@ModelDownloadService, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION,
                            notification(this@ModelDownloadService, states[model.id], model.bytes))
                    }
                }
            }
            try { repository.transfer(model.id) }
            finally {
                updates.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

@AndroidEntryPoint
class ModelDownloadJobService : JobService() {
    @Inject lateinit var repository: ModelDownloadRepository
    @Inject lateinit var catalog: ModelCatalog
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var work: Job? = null
    @OptIn(FlowPreview::class)
    override fun onStartJob(params: JobParameters): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        val model = catalog.models.firstOrNull { it.id == params.extras.getString("model") } ?: return false
        if (work?.isActive == true || repository.state.value[model.id]?.phase != TransferPhase.QUEUED) return false
        setNotification(params, NOTIFICATION, notification(this, null, model.bytes), JOB_END_NOTIFICATION_POLICY_REMOVE)
        work = scope.launch {
            val updates = launch {
                repository.state.sample(1.seconds).collect {
                    setNotification(params, NOTIFICATION, notification(this@ModelDownloadJobService, it[model.id], model.bytes),
                        JOB_END_NOTIFICATION_POLICY_REMOVE)
                }
            }
            try { repository.transfer(model.id) }
            finally { updates.cancel(); jobFinished(params, false) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); return false }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
