package com.justcal.app.ai

import android.app.job.JobScheduler
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.HttpURLConnection
import java.net.URI
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelPlatformReliabilityTest {
    private fun report(message: String) = InstrumentationRegistry.getInstrumentation().sendStatus(
        0, Bundle().apply { putString("stream", "\nPLATFORM $message\n") })

    /** At most 64 KiB from the pinned artifact; a non-range response is closed unread. */
    @Test fun pinnedHttpsRangeProbe() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("modelNetworkProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val descriptor = ModelCatalog(context).models.first()
        var location = URI(descriptor.url)
        val started = android.os.SystemClock.elapsedRealtime()
        for (redirect in 0..5) {
            require(ModelResumePolicy.trusted(location))
            val connection = location.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Range", "bytes=0-65535")
            try {
                val code = connection.responseCode
                report("pinned source hop=$redirect host=${location.host} status=$code")
                if (code in listOf(301, 302, 303, 307, 308)) {
                    location = location.resolve(requireNotNull(connection.getHeaderField("Location")))
                    continue
                }
                assertEquals("Refusing an unbounded model response", 206, code)
                assertEquals("bytes 0-65535/${descriptor.bytes}", connection.getHeaderField("Content-Range"))
                var total = 0
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (total < 65536) {
                        val count = input.read(buffer, 0, minOf(buffer.size, 65536 - total))
                        if (count < 0) break
                        total += count
                        report("range progress=$total/65536")
                    }
                }
                assertEquals(65536, total)
                report("pinned HTTPS 64KiB received in ${android.os.SystemClock.elapsedRealtime() - started}ms; etagPresent=${connection.getHeaderField("ETag") != null}; not a full install/integrity test")
                return
            } finally { connection.disconnect() }
        }
        error("Redirect budget exceeded")
    }

    /** Real UIDT scheduler/manifest boundary; no queued transfer or model bytes. */
    @Test fun userInitiatedJobCanBeScheduledFromForegroundWithoutReplacingWork() {
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        assumeTrue(InstrumentationRegistry.getArguments().getString("uidtProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scheduler = context.getSystemService(JobScheduler::class.java)
        check(scheduler.getPendingJob(421) == null) { "Existing model job retained; probe must not replace it" }
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity { startModelDownload(it, ModelCatalog(context).models.first()) }
            // The production callback may already finish: no transfer was queued.
            val pending = scheduler.getPendingJob(421)
            if (pending != null) assertTrue(pending.isUserInitiated)
            report("API ${Build.VERSION.SDK_INT} production UIDT scheduling accepted from foreground; transfer/OS-stop not asserted")
        } finally {
            scheduler.cancel(421)
            scenario.close()
        }
    }
}
