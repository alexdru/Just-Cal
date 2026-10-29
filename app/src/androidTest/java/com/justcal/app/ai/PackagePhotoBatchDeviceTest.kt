package com.justcal.app.ai

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justcal.app.MainActivity
import com.justcal.app.camera.ImagePreprocessor
import com.justcal.app.camera.ImageSource
import com.justcal.app.camera.PreparedImage
import com.justcal.app.camera.ScanMode
import com.justcal.app.camera.ScanRequest
import com.justcal.app.ocr.NutritionLabelParser
import com.justcal.app.ocr.PackageOcrResult
import com.justcal.app.ocr.PackageScanPrefill
import com.justcal.app.ocr.PaddlePackageOcrEngine
import java.io.File
import java.security.MessageDigest
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit local-photo batch. Uses installed models only; no downloads, preferences or diary writes. */
@RunWith(AndroidJUnit4::class)
class PackagePhotoBatchDeviceTest {
    @Test fun evaluateLocalPackagePhotos() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("packagePhotoBatch") == "true")
        val engine = args.getString("batchEngine") ?: "ocr"
        require(engine in setOf("ocr", "paddleocr-vl16", "gemma4-e2b"))
        val preferCrop = (engine == "paddleocr-vl16") || (args.getString("batchCrop") == "true")
        val variant = if ((engine == "gemma4-e2b") && preferCrop) "$engine-crop" else engine
        val runId = args.getString("batchRun") ?: "2026-10-28"
        require(runId.matches(Regex("[A-Za-z0-9-]+")))
        val context = instrumentation.targetContext
        val fixtures = File(context.getExternalFilesDir(null), "package-batch-fixtures")
        val selected = args.getString("batchCases")?.split(',')?.toSet()
        val files = fixtures.walkTopDown().filter {
            it.isFile && (it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp"))
        }.filter { (selected == null) || (it.name in selected) }.sortedBy { it.relativeTo(fixtures).path }.toList()
        require(files.isNotEmpty()) { "No local package photos supplied" }
        val root = File(context.getExternalFilesDir(null), "package-batch-results/$runId")
        val output = File(root, variant).apply { mkdirs() }
        val images = ImagePreprocessor(context)
        val ocr = PaddlePackageOcrEngine(context, images)
        val catalog = ModelCatalog(context)
        val store = LocalModelStore(context, LiteRtModelInspector(), catalog)
        val runtime = LiteRtLocalAiEngine(context, store, images)
        val model = if (engine == "ocr") null else {
            store.list().firstOrNull { store.validate(it).catalogId == engine }
                ?: error("Requested pinned model is not installed")
        }
        val validation = model?.let { store.validate(it) }
        val descriptor = validation?.sha256?.let(catalog::matching)
        val prompt = if (engine == "gemma4-e2b") GEMMA_PACKAGE_PROMPT else if (engine == "ocr") "" else "OCR:"
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var processed = 0
        try {
            for ((index, file) in files.withIndex()) {
                val relative = file.relativeTo(fixtures).invariantSeparatorsPath
                val inputHash = sha256(file)
                val began = SystemClock.elapsedRealtime()
                var image: PreparedImage? = null
                var crop: PreparedImage? = null
                val copy = images.newCaptureFile()
                var outcome = "ERROR"
                var failure: String? = null
                var initializationMs: Long? = null
                var metrics: AiMetrics? = null
                val raw = StringBuilder()
                var payload: JsonObject = buildJsonObject {}
                try {
                    file.copyTo(copy, overwrite = true)
                    image = images.prepare(Uri.fromFile(copy), ImageSource.CAMERA, ScanRequest(ScanMode.PACKAGE, 456))
                    if (engine == "ocr") {
                        val result = ocr.recognize(images.verify(image))
                        val label = NutritionLabelParser.parse(result)
                        val (name, energy, protein, fat, carbs, amount) = label.draft()
                        payload = buildJsonObject {
                            put("ocrResult", Json.encodeToJsonElement(PackageOcrResult.serializer(), result))
                            put("parsedLabel", Json.encodeToJsonElement(com.justcal.app.ocr.ParsedNutritionLabel.serializer(), label))
                            put(
                                "editor",
                                buildJsonObject {
                                    put("name", name)
                                    put("energy", energy)
                                    put("protein", protein)
                                    put("fat", fat)
                                    put("carbs", carbs)
                                    put("amount", amount)
                                },
                            )
                        }
                        outcome = if (result.lines.isEmpty()) "EMPTY" else "COMPLETE"
                    } else {
                        val baselineFile = File(File(root, "ocr"), "$relative.json")
                        val baselineReport = Json.parseToJsonElement(baselineFile.readText()).jsonObject
                        require(baselineReport["inputSha256"]?.jsonPrimitive?.content == inputHash) { "OCR baseline mismatch" }
                        val baseline = Json.decodeFromJsonElement(PackageOcrResult.serializer(), baselineReport.getValue("ocrResult"))
                        require((baseline.width == image.width) && (baseline.height == image.height)) { "OCR image geometry mismatch" }
                        if (preferCrop) {
                            crop = LabelImageCropper(images).crop(image, baseline)
                        }
                        val cropUri = crop?.uri
                        if ((cropUri != null) && (args.getString("batchSaveCrops") == "true")) {
                            val savedCrop = File(output, "crops/$relative")
                            savedCrop.parentFile!!.mkdirs()
                            requireNotNull(context.contentResolver.openInputStream(Uri.parse(cropUri))).use { input ->
                                savedCrop.outputStream().use { input.copyTo(it) }
                            }
                        }
                        // Benchmark every supplied photo. Full frame when no reliable crop exists is
                        // exploratory evidence only; production currently skips VLM in that situation.
                        initializationMs = runtime.load(requireNotNull(model), AiBackend.AUTO)
                        metrics = withTimeout(90.seconds) {
                            runtime.generate(crop ?: image, prompt, engine == "gemma4-e2b") {
                                require((raw.length + it.length) <= 16_000) { "Response budget exceeded" }
                                raw.append(it)
                            }
                        }
                        if (engine == "paddleocr-vl16") {
                            val parsed = NutritionLabelParser.parseUnscoredTranscription(raw.toString())
                            val hybrid = TranscriptionNutrition.merge(PackageScanPrefill.from(baseline).label, raw.toString())
                            payload = buildJsonObject {
                                put("parsedLabel", Json.encodeToJsonElement(com.justcal.app.ocr.ParsedNutritionLabel.serializer(), parsed))
                                put("hybridDraft", Json.encodeToJsonElement(com.justcal.app.domain.RecognitionDraft.serializer(), hybrid))
                            }
                            outcome = if (listOf(
                                parsed.energyKcal,
                                parsed.proteinGrams,
                                parsed.fatGrams,
                                parsed.carbsGrams,
                                parsed.netGrams,
                            ).all { it == null }) "EMPTY" else "COMPLETE"
                        } else {
                            val parsed = NutritionExtraction.parse(raw.toString())
                            payload = buildJsonObject { put("nutritionJson", Json.parseToJsonElement(parsed.formattedJson)) }
                            outcome = if (listOf(
                                parsed.caloriesKcal,
                                parsed.proteinGrams,
                                parsed.fatGrams,
                                parsed.carbohydrateGrams,
                                parsed.packageWeightGrams,
                            ).all { it == null }) "EMPTY" else "COMPLETE"
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    outcome = "DEADLINE"
                    failure = "LOCAL_INFERENCE_DEADLINE"
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failure = error.javaClass.simpleName
                } catch (error: LinkageError) {
                    failure = error.javaClass.simpleName
                } finally {
                    withContext(NonCancellable) {
                        if (engine != "ocr") runtime.unload()
                        crop?.run { images.discard(uri) }
                        image?.run { images.discard(uri) }
                        copy.delete()
                    }
                }
                val report = buildJsonObject {
                    put("schemaVersion", 1)
                    put("runId", runId)
                    put("appVersion", com.justcal.app.BuildConfig.VERSION_NAME)
                    put("engine", engine)
                    put("variant", variant)
                    put("preferNutritionCrop", preferCrop)
                    put("cropProfileVersion", if (preferCrop) JsonPrimitive(LabelImageCropper.PROFILE_VERSION) else JsonNull)
                    put("fixture", relative)
                    put("inputSha256", inputHash)
                    put("inputBytes", file.length())
                    put("device", Build.MANUFACTURER + "/" + Build.MODEL)
                    put("api", Build.VERSION.SDK_INT)
                    put("runtimeVersion", if (engine == "ocr") "bundled-PP-OCRv5" else AI_RUNTIME_VERSION)
                    put("artifactHash", validation?.sha256?.let(::JsonPrimitive) ?: JsonNull)
                    put("revision", descriptor?.revision?.let(::JsonPrimitive) ?: JsonNull)
                    put(
                        "taskVersion",
                        when (engine) {
                            "gemma4-e2b" -> if (preferCrop) "package-photo-crop-json-v2" else "package-photo-json-v1"
                            "ocr" -> "bundled-package-ocr-v1"
                            else -> TranscriptionNutrition.PROFILE_VERSION
                        },
                    )
                    put("prompt", prompt)
                    put("structured", engine == "gemma4-e2b")
                    put("freshEnginePerPhoto", engine != "ocr")
                    put("imageWidth", image?.width?.let(::JsonPrimitive) ?: JsonNull)
                    put("imageHeight", image?.height?.let(::JsonPrimitive) ?: JsonNull)
                    put("inputMode", if (crop == null) "FULL_FRAME" else "OCR_NUTRITION_CROP")
                    put("cropWidth", crop?.width?.let(::JsonPrimitive) ?: JsonNull)
                    put("cropHeight", crop?.height?.let(::JsonPrimitive) ?: JsonNull)
                    put("outcome", outcome)
                    put("failure", failure?.let(::JsonPrimitive) ?: JsonNull)
                    put("initializationMs", initializationMs?.let(::JsonPrimitive) ?: JsonNull)
                    put("elapsedMs", SystemClock.elapsedRealtime() - began)
                    put("raw", raw.toString())
                    put(
                        "metrics",
                        metrics?.run {
                            buildJsonObject {
                                put("backend", backend.name)
                                put("inferenceMs", inferenceMillis)
                                put("firstResponseMs", firstResponseMillis?.let(::JsonPrimitive) ?: JsonNull)
                                put("requestedBackend", requestedBackend?.let(::JsonPrimitive) ?: JsonNull)
                                put("backendAttempts", JsonArray(backendAttempts.map(::JsonPrimitive)))
                                put("fallbackReason", fallbackReason?.let(::JsonPrimitive) ?: JsonNull)
                                put("nativeFirstTokenSeconds", nativeFirstTokenSeconds?.let(::JsonPrimitive) ?: JsonNull)
                                put("decodeTokensPerSecond", decodeTokensPerSecond?.let(::JsonPrimitive) ?: JsonNull)
                            }
                        } ?: JsonNull,
                    )
                    payload.forEach { (key, value) -> put(key, value) }
                }
                val destination = File(output, "$relative.json")
                destination.parentFile!!.mkdirs()
                val pending = File(destination.parentFile, destination.name + ".pending")
                pending.writeText(report.toString())
                check(pending.renameTo(destination)) { "Could not commit batch report" }
                processed++
                val numericProgress = (payload["nutritionJson"] as? JsonObject)
                    ?.filterKeys { it != "productName" }?.let(::JsonObject)?.toString().orEmpty()
                instrumentation.sendStatus(
                    0,
                    Bundle().apply {
                        putString("stream", "\n$variant ${index + 1}/${files.size} $relative $outcome ${SystemClock.elapsedRealtime() - began} ms $numericProgress\n")
                    },
                )
            }
        } finally {
            withContext(NonCancellable) { runtime.unload() }
            scenario.close()
        }
        // Coverage only. A completed native call or valid JSON is never an accuracy assertion.
        assertEquals(files.size, processed)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val GEMMA_PACKAGE_PROMPT = PackageMacroAssistance.PROMPT
    }
}
