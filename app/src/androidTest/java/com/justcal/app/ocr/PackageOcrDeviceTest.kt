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
        val libraries = setOf("libonnxruntime.so", "libonnxruntime4j_jni.so", "libopencv_java4.so", "libc++_shared.so")
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
