package com.justcal.app.ai

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.system.Os
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.camera.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

/** Opt-in existing-weight lifecycle probe; private metadata/cache and no model downloads. */
@RunWith(AndroidJUnit4::class)
class ModelRuntimeReliabilityTest {
    private fun report(message: String) = InstrumentationRegistry.getInstrumentation().sendStatus(
        0, Bundle().apply { putString("stream", "\nRELIABILITY $message\n") })
    private fun memory(stage: String) {
        val info = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        report("$stage pssKiB=${info.totalPss} nativeAllocatedBytes=${Debug.getNativeHeapAllocatedSize()} " +
            "graphicsPssKiB=${info.memoryStats["summary.graphics"] ?: "unavailable"}")
    }
    private fun digest(file: File): String? = if (!file.isFile) null else
        file.inputStream().use { stream ->
            val hash = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(1024 * 1024)
            while (true) { val count = stream.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
            hash.digest().joinToString("") { "%02x".format(it) }
        }

    @Test fun nativeInspectionAndInvalidContainerRecovery() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "invalid-runtime-${UUID.randomUUID()}.litertlm")
        try {
            file.writeBytes(ByteArray(45))
            val failure = runCatching { LiteRtModelInspector().inspect(file) }.exceptionOrNull()
            assertNotNull(failure)
            assertFalse("JNI could not load: $failure", failure is LinkageError)
            assertTrue(failure is IllegalArgumentException)
            assertTrue(failure?.cause is Exception)
            assertEquals(1, ModelRuntimeLease.slot.availablePermits)
            report("native inspector loaded; malformed container rejected; pageBytes=${Os.sysconf(android.system.OsConstants._SC_PAGESIZE)} sdk=${android.os.Build.VERSION.SDK_INT}")
        } finally { file.delete() }
    }

    @Test fun existingModelsRepeatCancelReloadSwitchAndExitWithoutChangingUserData() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runtimeReliability") == "true")
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        val originalRoot = File(real.noBackupFilesDir, "local-ai-models")
        val sourceFiles = ModelFiles(originalRoot)
        val originals = sourceFiles.list()
        val catalog = ModelCatalog(real)
        // Require both already installed pinned artifacts; this test never downloads or imports.
        val selected = catalog.models.map { descriptor ->
            val original = originals.singleOrNull { sourceFiles.validation(it)?.sha256 == descriptor.sha256 }
                ?: error("Install the pinned ${descriptor.id} before opting in; no download performed")
            descriptor to original
        }
        val protected = buildList {
            add(File(real.filesDir, "datastore/nutrition_preferences.preferences_pb"))
            add(File(real.applicationInfo.dataDir, "shared_prefs/local-ai-backends.xml"))
            for (suffix in listOf("", "-wal", "-shm")) add(File(real.getDatabasePath("just-cal.db").path + suffix))
            for ((_, model) in selected) {
                val folder = sourceFiles.path(model).parentFile!!
                add(File(folder, "metadata.json")); add(File(folder, "validation.json"))
            }
        }
        val before = protected.associate { it.path to digest(it) }
        val root = File(real.cacheDir, "runtime-reliability-${UUID.randomUUID()}").apply { mkdirs() }
        val preferenceNames = mutableSetOf<String>()
        val isolated = object : ContextWrapper(real) {
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val ownedName = "${root.name}-$name"
                preferenceNames += ownedName
                return real.getSharedPreferences(ownedName, mode)
            }
        }
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val images = ImagePreprocessor(isolated)
        val store = LocalModelStore(isolated, LiteRtModelInspector(), catalog)
        val runtime = LiteRtLocalAiEngine(isolated, store, images)
        val ownedImages = mutableListOf<PreparedImage>()
        try {
            require(root.usableSpace > selected.sumOf { it.first.bytes } + 2L * 1024 * 1024 * 1024) {
                "Insufficient space for disposable model copies and runtime caches"
            }
            val models = selected.map { (descriptor, source) ->
                val id = UUID.randomUUID().toString()
                val folder = File(isolated.noBackupFilesDir, "local-ai-models/$id").apply { mkdirs() }
                val target = File(folder, "model.litertlm")
                sourceFiles.path(source).copyTo(target)
                File(sourceFiles.path(source).parentFile, "metadata.json").copyTo(File(folder, "metadata.json"))
                val model = LocalModel(id, descriptor.filename, descriptor.bytes)
                val validation = store.validate(model) // Hashes and inspects actual artifact bytes.
                assertEquals(descriptor.sha256, validation.sha256)
                report("inspected ${descriptor.id} hash=${validation.sha256} backends=${BackendSelector.candidates(validation.capabilities)}")
                model
            }
            for (word in listOf("ALPHA", "BETA")) {
                val capture = images.newCaptureFile()
                val bitmap = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(if (word == "ALPHA") Color.RED else Color.BLUE)
                    canvas.drawText(word, 30f, 155f, Paint().apply { color = Color.WHITE; textSize = 100f; isAntiAlias = true })
                    capture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally { bitmap.recycle() }
                ownedImages += images.prepare(Uri.fromFile(capture), ImageSource.CAMERA, ScanRequest(ScanMode.MEAL, 456))
            }
            // Unsupported backend rejection must release the lease, then Auto can load.
            assertTrue(runCatching { runtime.load(models.first(), AiBackend.GPU) }.isFailure)
            assertEquals(1, ModelRuntimeLease.slot.availablePermits)
            for ((modelIndex, model) in models.withIndex()) {
                val prompt = if (modelIndex == 0) "Read the single large word. Reply with that word only." else "OCR:"
                memory("before-$modelIndex")
                repeat(2) { cycle ->
                    val load = runtime.load(model, AiBackend.AUTO)
                    report("model=$modelIndex cycle=$cycle initializationMs=$load backend=${runtime.actualBackend}")
                    memory("loaded-$modelIndex-$cycle")
                    repeat(3) { run ->
                        val raw = StringBuilder()
                        val metrics = withTimeout(90.seconds) {
                            runtime.generate(ownedImages[run % 2], prompt, structured = false) { raw.append(it) }
                        }
                        assertTrue("Empty native response", raw.isNotBlank())
                        report("model=$modelIndex cycle=$cycle run=$run inferenceMs=${metrics.inferenceMillis} firstMs=${metrics.firstResponseMillis} backend=${metrics.backend} output=${raw.take(120)}")
                        memory("after-$modelIndex-$cycle-$run")
                    }
                    if (cycle == 0) {
                        val work = launch { runtime.generate(ownedImages.first(), prompt, false) {} }
                        delay(250.milliseconds)
                        val cancelStart = SystemClock.elapsedRealtime()
                        withTimeout(90.seconds) { work.cancelAndJoin() }
                        report("model=$modelIndex cancelJoinedMs=${SystemClock.elapsedRealtime() - cancelStart}")
                        val retry = StringBuilder()
                        withTimeout(90.seconds) { runtime.generate(ownedImages.last(), prompt, false) { retry.append(it) } }
                        assertTrue(retry.isNotBlank())
                    }
                    runtime.unload()
                    assertEquals(1, ModelRuntimeLease.slot.availablePermits)
                    memory("unloaded-$modelIndex-$cycle")
                    delay(1.seconds)
                    memory("settled-$modelIndex-$cycle")
                }
                // A real Activity stop drives session close while native inference is running.
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                val session = LocalAiSession(runtime, scope)
                val closed = CompletableDeferred<Unit>()
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) scope.launch(Dispatchers.IO) {
                        try { session.close(); closed.complete(Unit) } catch (failure: Throwable) { closed.completeExceptionally(failure) }
                    }
                }
                scenario.onActivity { it.lifecycle.addObserver(observer) }
                try {
                    session.refresh(listOf(model)); session.load()
                    withTimeout(90.seconds) { session.state.first { it.loaded || it.error != null } }
                    assertTrue(session.state.value.loaded)
                    session.run(ownedImages.first(), prompt, false)
                    withTimeout(5.seconds) { session.state.first { it.phase == AiLabPhase.RUNNING } }
                    delay(250.milliseconds)
                    scenario.moveToState(Lifecycle.State.CREATED)
                    withTimeout(90.seconds) { closed.await() }
                    assertEquals(1, ModelRuntimeLease.slot.availablePermits)
                    report("model=$modelIndex activity-stop joined inference and unloaded")
                } finally {
                    scenario.onActivity { it.lifecycle.removeObserver(observer) }
                    withContext(NonCancellable) { session.close() }
                    scope.cancel()
                    scenario.moveToState(Lifecycle.State.RESUMED)
                }
            }
        } finally {
            withContext(NonCancellable) { runtime.unload() }
            ownedImages.forEach { images.discard(it.uri) }
            scenario.close()
            preferenceNames.forEach(real::deleteSharedPreferences)
            root.deleteRecursively() // Only owned copies/caches; original weights never removed.
            val after = protected.associate { it.path to digest(it) }
            assertEquals("User diary/preferences/model metadata changed", before, after)
            for ((descriptor, model) in selected) assertEquals(descriptor.bytes, sourceFiles.path(model).length())
            report("protected diary/preferences/model metadata unchanged; original model files retained")
        }
    }
}
