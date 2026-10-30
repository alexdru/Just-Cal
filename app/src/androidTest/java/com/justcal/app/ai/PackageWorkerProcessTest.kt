package com.justcal.app.ai

import android.content.ComponentName
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.camera.*
import com.justcal.app.ocr.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageWorkerProcessTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun client() = PackageInferenceClient(context, ComponentName(context, PackageWorkerProbeService::class.java))
    private val image = PreparedImage("file:///not-used-by-probe", ImageSource.CAMERA,
        ScanRequest(ScanMode.PACKAGE, 123), 1, 1, "image/jpeg")
    private fun model(suffix: String) = LocalModel("00000000-0000-0000-0000-000000000$suffix", "fixture", 1)

    @Test fun workerDeathPreservesOcrAndParentThenNextRequestSucceeds() = runBlocking {
        val pid = Process.myPid()
        val baseline = PackageScanPrefill(ParsedNutritionLabel(
            energyKcal = LabelValue("120", .99f, "120 kcal"), basis = LabelBasis.PER_100_G),
            "original OCR", .99f, OcrMetrics(0, 0, 0, 0))
        var checkpointed = false
        var workerPid = 0
        val result = withTimeout(20_000) { packageEnrichmentOrBaseline(baseline) {
            client().generate(model("001"), image) {
                assertNotEquals(pid, it.processId)
                checkpointed = true
                workerPid = requireNotNull(it.processId)
            }
            error("Worker should have died")
        } }
        assertSame(baseline, result)
        assertTrue(checkpointed)
        val manager = context.getSystemService(android.app.ActivityManager::class.java)
        val exit = withTimeout(5000) {
            var found: android.app.ApplicationExitInfo? = null
            while (found == null) {
                found = manager.getHistoricalProcessExitReasons(context.packageName, workerPid, 1).firstOrNull()
                if (found == null) delay(100)
            }
            found
        }
        assertEquals(android.app.ApplicationExitInfo.REASON_CRASH_NATIVE, exit.reason)
        assertEquals(pid, Process.myPid())
        assertEquals(1, ModelRuntimeLease.slot.availablePermits)
        val retried = withTimeout(20_000) { client().generate(model("003"), image) {} }
        assertEquals("fixture", retried.rawOutput)
        assertEquals(1, ModelRuntimeLease.slot.availablePermits)
    }
    @Test fun unresponsiveWorkerIsTerminatedBeforeTheLeaseIsReleased() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val job = launch { client().generate(model("004"), image) { started.complete(Unit) } }
        withTimeout(20_000) { started.await() }
        job.cancel()
        delay(200)
        assertEquals(0, ModelRuntimeLease.slot.availablePermits)
        withTimeout(20_000) { job.join() }
        assertEquals(1, ModelRuntimeLease.slot.availablePermits)
        assertEquals("fixture", withTimeout(20_000) { client().generate(model("003"), image) {} }.rawOutput)
    }
    @Test fun cancellationJoinsWorkerBeforeReleasingLeaseAndAllowsRetry() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val job = launch { client().generate(model("002"), image) { started.complete(Unit) } }
        withTimeout(20_000) { started.await() }
        assertEquals(0, ModelRuntimeLease.slot.availablePermits)
        assertTrue(runCatching { client().generate(model("003"), image) {} }.isFailure)
        withTimeout(20_000) { job.cancelAndJoin() }
        assertEquals(1, ModelRuntimeLease.slot.availablePermits)
        assertEquals("fixture", withTimeout(20_000) { client().generate(model("003"), image) {} }.rawOutput)
    }
}
