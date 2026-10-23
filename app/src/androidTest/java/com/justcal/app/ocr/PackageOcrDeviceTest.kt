package com.justcal.app.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justcal.app.camera.*
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real CPU model execution, including the shipped Cyrillic dictionary; no fake engine. */
@RunWith(AndroidJUnit4::class)
class PackageOcrDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val images = ImagePreprocessor(context)
    private val engine = PaddlePackageOcrEngine(context, images)

    private fun report(name: String, result: PackageOcrResult) {
        val directory = File(context.getExternalFilesDir(null), "ocr-results").apply { mkdirs() }
        File(directory, "$name.json").writeText(Json.encodeToString(result))
        File(directory, "$name-parsed.json").writeText(Json.encodeToString(NutritionLabelParser.parse(result)))
        instrumentation.sendStatus(0, Bundle().apply {
            val memory = android.os.Debug.MemoryInfo().also { android.os.Debug.getMemoryInfo(it) }
            val highWater = runCatching { File("/proc/self/status").readLines().firstOrNull { it.startsWith("VmHWM:") } }.getOrNull()
            putString("stream", "\n$name memory: PSS=${memory.totalPss} KiB, process peak RSS=$highWater\n$name metrics: ${result.metrics}\n" +
                "parsed: ${Json.encodeToString(NutritionLabelParser.parse(result))}\n" +
                result.lines.joinToString("\n") { "${it.confidence}: ${it.text}" } + "\n")
        })
    }

    @Test fun packagedOcrNativeLibrariesHave16KiBElfAlignment() {
        val libraries = setOf("libonnxruntime.so", "libonnxruntime4j_jni.so", "libopencv_java4.so", "libc++_shared.so", "liblitertlm_jni.so")
        java.util.zip.ZipFile(context.applicationInfo.sourceDir).use { apk ->
            for (library in libraries) {
                val entry = requireNotNull(apk.getEntry("lib/arm64-v8a/$library"))
                apk.getInputStream(entry).use { input ->
                    val reader = java.io.DataInputStream(input)
                    val header = java.nio.ByteBuffer.wrap(ByteArray(64).also(reader::readFully)).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    assertEquals(2, header.get(4).toInt()) // ELF64
                    val tableOffset = header.getLong(32)
                    val entrySize = header.getShort(54).toInt() and 65535
                    val entryCount = header.getShort(56).toInt() and 65535
                    check(tableOffset >= 64 && tableOffset < 65536)
                    check(reader.skipBytes((tableOffset - 64).toInt()) == (tableOffset - 64).toInt())
                    val table = java.nio.ByteBuffer.wrap(ByteArray(entrySize * entryCount).also(reader::readFully))
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    for (index in 0 until entryCount) {
                        val position = index * entrySize
                        if (table.getInt(position) == 1) { // PT_LOAD
                            assertTrue("$library has insufficient ELF alignment", table.getLong(position + 48) >= 16384)
                        }
                    }
                }
            }
        }
    }

    @Test fun nativeBuildUsesNoProprietaryIpp() {
        check(org.opencv.android.OpenCVLoader.initLocal())
        val info = org.opencv.core.Core.getBuildInformation()
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nOpenCV native build:\n$info\n") })
        assertFalse("Intel IPP must not be linked into this GPL application",
            info.lineSequence().any { it.contains("Intel IPP:") && !it.substringAfter("Intel IPP:").trim().startsWith("NO") })
    }

    @Test fun bundledModelsRecognizeRussianAndEnglishPixels() = runBlocking {
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 44f }
            listOf("РИС С КУРИЦЕЙ", "Масса нетто: 240 г", "Пищевая ценность на 100 г",
                "Белки 11 г  Жиры 6,4 г", "Углеводы 20,4 г", "Энергетическая ценность: 183,2 ккал",
                "Net weight 240 g").forEachIndexed { index, text -> canvas.drawText(text, 40f, 80f + index * 95, paint) }
            val file = images.newCaptureFile()
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val image = images.prepare(Uri.fromFile(file), ImageSource.CAMERA, ScanRequest(ScanMode.PACKAGE, 123))
            try {
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                val active = java.util.concurrent.atomic.AtomicBoolean(true)
                val ticks = java.util.concurrent.atomic.AtomicInteger()
                val maxDelay = java.util.concurrent.atomic.AtomicLong()
                val pulse = object : Runnable {
                    var expected = android.os.SystemClock.uptimeMillis()
                    override fun run() {
                        if (!active.get()) return
                        val now = android.os.SystemClock.uptimeMillis()
                        maxDelay.updateAndGet { maxOf(it, now - expected) }
                        ticks.incrementAndGet()
                        expected = now + 40
                        handler.postDelayed(this, 40)
                    }
                }
                handler.post(pulse)
                val result = try { engine.recognize(images.verify(image)) }
                    finally { active.set(false); handler.removeCallbacks(pulse) }
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("stream", "\nMain looper during real OCR: ${ticks.get()} pulses, max scheduling delay ${maxDelay.get()} ms\n")
                })
                assertTrue("Main looper did not remain responsive", ticks.get() >= 5 && maxDelay.get() < 500)
                report("smoke", result)
                assertTrue(result.lines.any { it.text.contains("РИС") })
                assertTrue(result.lines.any { it.text.contains("Net", ignoreCase = true) })
                val parsed = NutritionLabelParser.parse(result)
                assertEquals("240", parsed.netGrams?.value)
                assertEquals("183.2", parsed.energyKcal?.value)
                assertEquals("11", parsed.proteinGrams?.value)
            } finally { images.discard(image.uri) }
        } finally { bitmap.recycle() }
    }

    @Test fun sidewaysAndUpsideDownLabelsRestoreParagraphOrder() = runBlocking {
        val original = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(original).apply { drawColor(Color.WHITE) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 44f }
            listOf("РИС С КУРИЦЕЙ", "Масса нетто: 240 г", "Пищевая ценность на 100 г",
                "Белки 11 г", "Жиры 6,4 г", "Углеводы 20,4 г",
                "Энергетическая ценность 183,2 ккал").forEachIndexed { index, text ->
                canvas.drawText(text, 40f, 80f + index * 95, paint)
            }
            for (angle in listOf(90f, 180f)) {
                val bitmap = Bitmap.createBitmap(original, 0, 0, original.width, original.height,
                    android.graphics.Matrix().apply { postRotate(angle) }, true)
                try {
                    val file = images.newCaptureFile()
                    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    val image = images.prepare(Uri.fromFile(file), ImageSource.CAMERA, ScanRequest(ScanMode.PACKAGE, 123))
                    try {
                        val result = engine.recognize(images.verify(image))
                        report("rotation-${angle.toInt()}", result)
                        val label = NutritionLabelParser.parse(result)
                        assertEquals("183.2", label.energyKcal?.value)
                        assertEquals("11", label.proteinGrams?.value)
                        assertEquals("6.4", label.fatGrams?.value)
                        assertEquals("20.4", label.carbsGrams?.value)
                    } finally { images.discard(image.uri) }
                } finally { bitmap.recycle() }
            }
        } finally { original.recycle() }
    }

    @Test fun servingOnlyLabelUsesFullFallbackAndKeepsNutritionEmpty() = runBlocking {
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 44f }
            listOf("CHICKEN RICE", "Net weight 205 g", "Nutrition per serving",
                "Protein 12 g", "Fat 8 g", "Carbohydrates 21 g", "Energy 202 kcal")
                .forEachIndexed { index, text -> canvas.drawText(text, 40f, 80f + index * 95, paint) }
            val file = images.newCaptureFile()
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val image = images.prepare(Uri.fromFile(file), ImageSource.CAMERA, ScanRequest(ScanMode.PACKAGE, 123))
            try {
                val result = engine.recognize(images.verify(image))
                assertTrue("Incomplete per-100-g data must invoke the full pass", result.fullFallback)
                val label = NutritionLabelParser.parse(result)
                assertEquals(LabelBasis.PER_SERVING, label.basis)
                assertEquals("", label.draft().energy)
                assertEquals("", label.draft().protein)
                report("serving-fallback", result)
            } finally { images.discard(image.uri) }
        } finally { bitmap.recycle() }
    }

    /** Opt-in diagnostic: benchmark completion is distinct from field accuracy. */
    @Test fun profileRealLabelsBenchmark() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("realOcrFixtures") == "true")
        val threads = args.getString("ocrThreads")?.toInt() ?: 2
        val reuse = args.getString("ocrReuse") == "true"
        val progressive = args.getString("ocrProgressive") == "true"
        val refine = args.getString("ocrRefineEnergy") == "true"
        val repeats = args.getString("ocrRepeats")?.toInt() ?: 1
        val selected = args.getString("ocrCases")?.split(',')?.map(String::toInt) ?: listOf(1, 2, 3)
        val prefix = args.getString("ocrRun") ?: "profile-t$threads"
        require(prefix.matches(Regex("[a-zA-Z0-9_-]+")) && repeats in 1..3 && selected.all { it in 1..3 })
        val candidate = PaddlePackageOcrEngine(context, images,
            OcrRuntimeConfig(threads, reuse, progressive, profile = true, refineEnergy = refine))
        try {
            repeat(repeats) { cycle ->
                for (index in selected) {
                    val fixture = File(context.getExternalFilesDir(null), "ocr-fixtures/case$index.jpg")
                    check(fixture.isFile) { "Missing real fixture: $fixture" }
                    val copy = images.newCaptureFile()
                    fixture.copyTo(copy)
                    val image = images.prepare(Uri.fromFile(copy), ImageSource.CAMERA,
                        ScanRequest(ScanMode.PACKAGE, 456))
                    try {
                        val result = candidate.recognize(images.verify(image))
                        report("$prefix-r$cycle-case$index", result)
                        val sum = result.regions
                        instrumentation.sendStatus(0, Bundle().apply {
                            putString("stream", "\nProfile aggregate: cropUs=${sum.sumOf { it.cropUs }}, " +
                                "preparationUs=${sum.sumOf { it.preparationUs }}, " +
                                "inferenceUs=${sum.sumOf { it.inferenceUs }}, decodingUs=${sum.sumOf { it.decodingUs }}\n")
                        })
                    } finally { images.discard(image.uri) }
                }
            }
        } finally { candidate.release() }
    }

    /** Real supplied photos: kcal, protein, fat, carbs per 100 g, visible net grams. */
    @Test fun suppliedPackageReviewFixtures() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("packageReviewFixtures") == "true")
        val expected = linkedMapOf(
            "1792661540668" to listOf("98.5", "5.9", "3.7", "10.4", "205"),
            "1792661541124" to listOf("329.1", "15.8", "15.5", "31.6", "140"),
            "1792661541536" to listOf("196.8", "9.7", "8.8", "19.7", "200"),
            "1792612058945" to listOf("138", "5", "4.4", "19.5", null),
            "1792612059984" to listOf("162", "5.1", "8.1", "17.1", null),
            "1792612058338" to listOf("150", "4.5", "3.6", "24.8", null),
            "1792612059644" to listOf("155", "5", "11", "9", "250"),
            "1792612058637" to listOf("121", "5", "6", "13", null),
            "1792612056936" to listOf("320", "8.5", "21", "24", "170"),
            "1792612057628" to listOf("293", "55", "7.6", "1.2", null),
            "1792612057327" to listOf("159", "5", "7", "19", "260"),
            "1792612058012" to listOf("189", "11", "8.5", "17", null),
        )
        val failures = mutableListOf<String>()
        val cases = mutableListOf<kotlinx.serialization.json.JsonObject>()
        var correctNumbers = 0
        var correctPrefills = 0
        var wrongPrefills = 0
        fun fields(values: List<String?>) = kotlinx.serialization.json.JsonArray(values.map {
            it?.let { value -> kotlinx.serialization.json.JsonPrimitive(value) } ?: kotlinx.serialization.json.JsonNull
        })
        val scenario = androidx.test.core.app.ActivityScenario.launch(com.justcal.app.MainActivity::class.java)
        try {
            for ((id, values) in expected) {
                val fixture = File(context.getExternalFilesDir(null), "package-review-fixtures/$id.jpg")
                check(fixture.isFile) { "Supplied package fixture unavailable: $id" }
                val copy = images.newCaptureFile()
                fixture.copyTo(copy)
                val image = images.prepare(Uri.fromFile(copy), ImageSource.CAMERA, ScanRequest(ScanMode.PACKAGE, 456))
                try {
                    val result = engine.recognize(images.verify(image))
                    report("review-$id", result)
                    val parsed = NutritionLabelParser.parse(result)
                    val draft = parsed.draft()
                    val actual = listOf(parsed.energyKcal?.value, parsed.proteinGrams?.value,
                        parsed.fatGrams?.value, parsed.carbsGrams?.value, parsed.netGrams?.value)
                    val editor = listOf(draft.energy, draft.protein, draft.fat, draft.carbs, draft.amount)
                    actual.forEachIndexed { field, value ->
                        if (value != values[field]) failures += "$id field=$field expected=${values[field]} actual=$value"
                    }
                    if (parsed.basis != LabelBasis.PER_100_G) failures += "$id basis=${parsed.basis}"
                    editor.forEachIndexed { field, value ->
                        if (value.isNotEmpty() && value != values[field]) failures += "$id wrong prefill field=$field value=$value"
                    }
                    correctNumbers += actual.indices.count { values[it] != null && actual[it] == values[it] }
                    correctPrefills += editor.indices.count { editor[it].isNotEmpty() && editor[it] == values[it] }
                    wrongPrefills += editor.indices.count { editor[it].isNotEmpty() && editor[it] != values[it] }
                    cases += kotlinx.serialization.json.buildJsonObject {
                        put("fixture", kotlinx.serialization.json.JsonPrimitive(id))
                        put("expected", fields(values))
                        put("actual", fields(actual))
                        put("editor", fields(editor))
                        put("basis", kotlinx.serialization.json.JsonPrimitive(parsed.basis.name))
                        put("metrics", Json.encodeToJsonElement(OcrMetrics.serializer(), result.metrics))
                    }
                    instrumentation.sendStatus(0, Bundle().apply {
                        putString("stream", "\nREVIEW $id expected=$values actual=$actual editor=$editor name=${draft.name}\n")
                    })
                } finally { images.discard(image.uri) }
            }
            File(context.getExternalFilesDir(null), "ocr-results/package-quality-gate.json").writeText(
                kotlinx.serialization.json.buildJsonObject {
                    put("appVersion", kotlinx.serialization.json.JsonPrimitive(com.justcal.app.BuildConfig.VERSION_NAME))
                    put("engine", kotlinx.serialization.json.JsonPrimitive("bundled PP-OCRv5"))
                    put("visibleNumbers", kotlinx.serialization.json.JsonPrimitive(expected.values.sumOf { row -> row.count { it != null } }))
                    put("correctNumbers", kotlinx.serialization.json.JsonPrimitive(correctNumbers))
                    put("correctPrefills", kotlinx.serialization.json.JsonPrimitive(correctPrefills))
                    put("wrongPrefills", kotlinx.serialization.json.JsonPrimitive(wrongPrefills))
                    put("strictQualityPassed", kotlinx.serialization.json.JsonPrimitive(failures.isEmpty()))
                    put("cases", kotlinx.serialization.json.JsonArray(cases))
                }.toString())
            assertTrue(failures.joinToString("\n"), failures.isEmpty())
        } finally { scenario.close() }
    }

    /** Push original photographs to externalFilesDir/ocr-fixtures/case1.jpg .. case3.jpg. */
    @Test fun realLabelFixtures() = runBlocking {
        val requested = InstrumentationRegistry.getArguments().getString("realOcrFixtures")
        org.junit.Assume.assumeTrue("Real photo fixtures explicitly required for this run", requested == "true")
        val expected = listOf(
            listOf("200", "196.8", "9.7", "8.8", "19.7"),
            listOf("240", "183.2", "11", "6.4", "20.4"),
            listOf("205", "98.5", "5.9", "3.7", "10.4"),
        )
        val failures = mutableListOf<String>()
        for (index in 1..3) {
            val fixture = File(context.getExternalFilesDir(null), "ocr-fixtures/case$index.jpg")
            check(fixture.isFile) { "Missing real fixture: $fixture" }
            val privateCopy = images.newCaptureFile()
            fixture.copyTo(privateCopy)
            val image = images.prepare(Uri.fromFile(privateCopy), ImageSource.CAMERA, ScanRequest(ScanMode.PACKAGE, 456))
            try {
                val result = engine.recognize(images.verify(image))
                report("case$index", result)
                val label = NutritionLabelParser.parse(result)
                val actual = listOf(label.netGrams?.value, label.energyKcal?.value,
                    label.proteinGrams?.value, label.fatGrams?.value, label.carbsGrams?.value)
                actual.forEachIndexed { field, value ->
                    if (value != expected[index - 1][field]) failures += "Case $index field $field expected ${expected[index - 1][field]}, actual $value"
                }
            } finally { images.discard(image.uri) }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
