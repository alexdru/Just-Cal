package com.justcal.app.ai

import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justcal.app.camera.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Opt in explicitly: downloads multi-GB pinned weights and runs real image inference. */
@RunWith(AndroidJUnit4::class)
class ModelRuntimeDeviceTest {
    @Test fun mealNameImageGateAndCancellation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("realAiModels") == "true")
        val context = instrumentation.targetContext
        val catalog = ModelCatalog(context)
        val store = LocalModelStore(context, LiteRtModelInspector(), catalog)
        val model = store.list().first { store.validate(it).catalogId == "gemma4-e2b" }
        val validation = store.validate(model)
        val descriptor = requireNotNull(catalog.matching(validation.sha256))
        val images = ImagePreprocessor(context)
        val runtime = LiteRtLocalAiEngine(context, store, images)
        val report = File(context.getExternalFilesDir(null), "ai-results/meal-name-gate.txt")
        report.parentFile!!.mkdirs()
        report.writeText("runtime=$AI_RUNTIME_VERSION hash=${validation.sha256} profile=${MealRecognition.PROFILE_VERSION}\n")
        val results = mutableListOf<JsonObject>()
        val jsonReport = File(report.parentFile, "meal-name-gate.json")
        var cancellationRetried = false
        fun saveReport() {
            jsonReport.writeText(
                buildJsonObject {
                    put("runtimeVersion", AI_RUNTIME_VERSION)
                    put("artifactHash", validation.sha256)
                    put("revision", descriptor.revision)
                    put("taskVersion", MealRecognition.PROFILE_VERSION)
                    put("prompt", MealRecognition.PROMPT)
                    put("cancellationRetried", cancellationRetried)
                    put("cases", JsonArray(results))
                }.toString(),
            )
        }
        val failures = mutableListOf<String>()
        val scenario = androidx.test.core.app.ActivityScenario.launch(com.justcal.app.MainActivity::class.java)
        try {
            runtime.load(model, AiBackend.AUTO)
            for (name in listOf("apple", "banana", "pizza", "dog", "bus", "blank", "multiple")) {
                val copy = images.newCaptureFile()
                if (name == "multiple") {
                    val bitmap = android.graphics.Bitmap.createBitmap(512, 256, android.graphics.Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = android.graphics.Canvas(bitmap).apply { drawColor(android.graphics.Color.WHITE) }
                        for ((index, food) in listOf("apple", "banana").withIndex()) {
                            val part = android.graphics.BitmapFactory.decodeFile(
                                File(context.getExternalFilesDir(null), "meal-fixtures/$food.jpg").absolutePath)
                            try { canvas.drawBitmap(part, null, android.graphics.Rect(index * 256, 0, (index + 1) * 256, 256), null) }
                            finally { part.recycle() }
                        }
                        copy.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { bitmap.recycle() }
                } else if (name == "blank") {
                    val bitmap = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        copy.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { bitmap.recycle() }
                } else File(context.getExternalFilesDir(null), "meal-fixtures/$name.jpg").copyTo(copy)
                val image = images.prepare(Uri.fromFile(copy), ImageSource.CAMERA, ScanRequest(ScanMode.MEAL, 456))
                try {
                    val raw = StringBuilder()
                    val metrics = withTimeout(90_000) { runtime.generate(image, MealRecognition.PROMPT, false) { raw.append(it) } }
                    val parsed = runCatching { MealRecognition.parse(raw.toString()) }
                    val found = parsed.getOrNull()?.name.orEmpty().lowercase()
                    val correct = when (name) {
                        "apple" -> "apple" in found || "яблок" in found
                        "banana" -> "banana" in found || "банан" in found
                        "pizza" -> "pizza" in found || "пицц" in found
                        "multiple" -> found.isEmpty() // A contradictory multi-food answer may be safely rejected.
                        else -> parsed.isSuccess && found.isEmpty() // Garbage is not a correct non-food classification.
                    }
                    if (!correct) failures += "$name: $raw"
                    results += buildJsonObject {
                        put("fixture", name)
                        put("pipelineCorrect", correct)
                        put("validResponse", parsed.isSuccess)
                        put("acceptedName", parsed.getOrNull()?.name?.let(::JsonPrimitive) ?: JsonNull)
                        put("validationError", parsed.exceptionOrNull()?.javaClass?.simpleName?.let(::JsonPrimitive) ?: JsonNull)
                        put("raw", raw.toString())
                        put("backend", metrics.backend.name)
                        put("initializationMs", metrics.initializationMillis)
                        put("inferenceMs", metrics.inferenceMillis)
                        put("firstResponseMs", metrics.firstResponseMillis?.let(::JsonPrimitive) ?: JsonNull)
                    }
                    saveReport()
                    val line = "\n$name pipelineCorrect=$correct validResponse=${parsed.isSuccess} $metrics\n$raw\n"
                    report.appendText(line)
                    instrumentation.sendStatus(0, Bundle().apply { putString("stream", line) })
                    if (name == "apple") {
                        val cancelled = launch { runtime.generate(image, "Describe this image in great detail.", false) {} }
                        delay(2_000)
                        cancelled.cancelAndJoin()
                        val afterCancel = StringBuilder()
                        runtime.generate(image, MealRecognition.PROMPT, false) { afterCancel.append(it) }
                        check(MealRecognition.parse(afterCancel.toString()).name != null)
                        report.appendText("Cancellation joined; retry completed.\n")
                        cancellationRetried = true
                        saveReport()
                    }
                } finally { images.discard(image.uri) }
            }
        } finally { withContext(NonCancellable) { runtime.unload() }; scenario.close() }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
    @Test fun modelImageSmoke() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("realAiModels") == "true")
        val context = instrumentation.targetContext
        val catalog = ModelCatalog(context)
        val store = LocalModelStore(context, LiteRtModelInspector(), catalog)
        val downloads = ModelDownloadRepository(context, catalog, store)
        val images = ImagePreprocessor(context)
        val modelId = args.getString("modelId") ?: "gemma4-e2b"
        val descriptor = catalog.models.single { it.id == modelId }
        var model = store.list().firstOrNull { runCatching { store.validate(it).sha256 == descriptor.sha256 }.getOrDefault(false) }
        val supplied = File(context.getExternalFilesDir(null), "model-fixtures/${descriptor.filename}")
        if (model == null && supplied.isFile) {
            model = supplied.inputStream().use { ModelFiles(File(context.noBackupFilesDir, "local-ai-models"))
                .import(descriptor.filename, descriptor.bytes, it) {} }
            check(store.validate(model).sha256 == descriptor.sha256)
        }
        if (model == null) {
            downloads.queue(modelId)
            downloads.transfer(modelId)
            check(downloads.state.value[modelId]?.phase == TransferPhase.INSTALLED) { downloads.state.value[modelId].toString() }
            model = store.list().first { store.validate(it).sha256 == descriptor.sha256 }
        }
        val validation = store.validate(model)
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$modelId metadata: $validation\n") })
        val runtime = LiteRtLocalAiEngine(context, store, images)
        val outputDirectory = File(context.getExternalFilesDir(null), "ai-results").apply { mkdirs() }
        val backend = args.getString("aiBackend")?.let(AiBackend::valueOf) ?: AiBackend.AUTO
        val started = System.nanoTime()
        val scenario = androidx.test.core.app.ActivityScenario.launch(com.justcal.app.MainActivity::class.java)
        try {
            val init = runtime.load(model, backend)
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$modelId init: $init ms\n") })
            val selectedCases = args.getString("aiCases")?.split(',')?.map(String::toInt) ?: listOf(1, 2, 3)
            for (index in selectedCases) {
                val fixture = File(context.getExternalFilesDir(null), "ocr-fixtures/case$index.jpg")
                check(fixture.isFile) { "Supplied package fixture unavailable" }
                val copy = images.newCaptureFile()
                fixture.copyTo(copy)
                val image = images.prepare(Uri.fromFile(copy), ImageSource.CAMERA, ScanRequest(ScanMode.PACKAGE, 456))
                var crop: PreparedImage? = null
                try {
                    if (args.getString("aiCrops") == "true") {
                        val ocr = com.justcal.app.ocr.PaddlePackageOcrEngine(context, images)
                        val label = ocr.recognize(images.verify(image))
                        crop = LabelImageCropper(images).crop(image, label)
                        check(crop != null) { "Reliable nutrition block unavailable" }
                    }
                    val raw = StringBuilder()
                    val prompt = if (descriptor.profile == ModelTaskProfile.LABEL_TRANSCRIPTION) "OCR:"
                        else "Read the visible product name, nutrition per 100 g, and net weight. Transcribe exact label text and numbers. Do not estimate missing values."
                    val metrics = withTimeout(180_000) { runtime.generate(crop ?: image, prompt, false) { raw.append(it) } }
                    val memory = android.os.Debug.MemoryInfo().also { android.os.Debug.getMemoryInfo(it) }
                    File(outputDirectory, "$modelId-${backend.name}-case$index.txt").writeText(
                        "hash=${validation.sha256}\nruntime=$AI_RUNTIME_VERSION\n$metrics\nPSS=${memory.totalPss} KiB\n$raw")
                    instrumentation.sendStatus(0, Bundle().apply {
                        putString("stream", "\n$modelId case$index $metrics PSS=${memory.totalPss} KiB\n$raw\n")
                    })
                    if (descriptor.profile == ModelTaskProfile.LABEL_TRANSCRIPTION) {
                        val parsed = com.justcal.app.ocr.NutritionLabelParser.parseUnscoredTranscription(raw.toString())
                        File(outputDirectory, "$modelId-${backend.name}-case$index-transcription.json").writeText(
                            Json.encodeToString(com.justcal.app.ocr.ParsedNutritionLabel.serializer(), parsed))
                    }
                    assertTrue("Empty image response", raw.isNotBlank())
                } finally { crop?.let { images.discard(it.uri) }; images.discard(image.uri) }
            }
        } catch (failure: Throwable) {
            File(outputDirectory, "$modelId-${backend.name}-failure.txt").writeText(
                "runtime=$AI_RUNTIME_VERSION hash=${validation.sha256}\nelapsedMs=${(System.nanoTime() - started) / 1_000_000}\n${failure.javaClass.simpleName}: ${failure.message}")
            throw failure
        } finally {
            withContext(NonCancellable) { runtime.unload() }
            val memory = android.os.Debug.MemoryInfo().also { android.os.Debug.getMemoryInfo(it) }
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$modelId unloaded PSS=${memory.totalPss} KiB\n") })
            scenario.close()
        }
    }
}
