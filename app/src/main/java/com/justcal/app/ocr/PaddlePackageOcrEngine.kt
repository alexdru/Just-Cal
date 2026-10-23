package com.justcal.app.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.os.SystemClock
import android.os.Trace
import com.justcal.app.camera.ImagePreprocessor
import com.justcal.app.camera.ScanMode
import com.justcal.app.camera.VerifiedImageInput
import com.paddle.ocr.model.OCRBox
import com.paddle.ocr.postprocess.DBPostProcessor
import com.paddle.ocr.postprocess.QuadTextCrop
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** CPU inference, with all native ownership and experiment settings confined to this adapter. */
@Singleton
class PaddlePackageOcrEngine internal constructor(
    private val context: Context,
    private val images: ImagePreprocessor,
    private val config: OcrRuntimeConfig,
) : PackageOcrEngine {
    @Inject constructor(@ApplicationContext context: Context, images: ImagePreprocessor) :
        this(context, images, OcrRuntimeConfig())

    private val permit = Mutex()
    private var cached: Models? = null

    private class Models(val detector: OrtSession, val recognizer: OrtSession, val dictionary: List<String>) : AutoCloseable {
        override fun close() { try { recognizer.close() } finally { detector.close() } }
    }

    /** Serialized with inference: cancellation cannot free an in-flight native session. */
    internal suspend fun release() = withContext(Dispatchers.Default) {
        permit.withLock { cached?.close(); cached = null }
    }

    override suspend fun recognize(input: VerifiedImageInput): PackageOcrResult = withContext(Dispatchers.Default) {
        require(input.image.request.mode == ScanMode.PACKAGE)
        permit.withLock {
            val started = SystemClock.elapsedRealtime()
            check(OpenCVLoader.initLocal()) { "OpenCV initialization failed" }
            val environment = OrtEnvironment.getEnvironment()
            val initStart = SystemClock.elapsedRealtime()
            val models = cached ?: loadModels(environment).also { if (config.reuseSessions) cached = it }
            val initialization = SystemClock.elapsedRealtime() - initStart
            try {
                currentCoroutineContext().ensureActive()
                val bitmap = images.decode(input.image)
                var source = Mat()
                val rgba = Mat()
                try {
                    Utils.bitmapToMat(bitmap, rgba)
                    Imgproc.cvtColor(rgba, source, Imgproc.COLOR_RGBA2BGR)
                } catch (error: Exception) {
                    source.release()
                    throw error
                } finally {
                    rgba.release()
                    bitmap.recycle()
                }
                try {
                    val detStart = SystemClock.elapsedRealtime()
                    var coordinateRotation = 0
                    var boxes = detect(source, models.detector, environment)
                    if (boxes.isNotEmpty() && boxes.count { box ->
                        val w = box.points.maxOf { it.x } - box.points.minOf { it.x }
                        val h = box.points.maxOf { it.y } - box.points.minOf { it.y }
                        h > w * 2
                    } > boxes.size * 0.6) {
                        val rotated = Mat()
                        Core.rotate(source, rotated, Core.ROTATE_90_COUNTERCLOCKWISE)
                        coordinateRotation = 270
                        source.release()
                        source = rotated
                        boxes = detect(source, models.detector, environment)
                    }
                    val detection = SystemClock.elapsedRealtime() - detStart
                    require(boxes.size <= 384) { "Too many text regions; photograph the label more closely" }
                    currentCoroutineContext().ensureActive()
                    val recStart = SystemClock.elapsedRealtime()
                    val lines = linkedMapOf<Int, OcrLine>()
                    val probes = linkedMapOf<Int, OcrLine>()
                    val profiles = ArrayList<OcrRegionProfile>()
                    var invertedLines = 0
                    suspend fun recognizeRegion(index: Int, scout: Boolean, stage: String): OcrLine {
                        currentCoroutineContext().ensureActive()
                        val box = boxes[index]
                        val cropStart = System.nanoTime()
                        val crop = QuadTextCrop.crop(source, box)
                        val cropUs = (System.nanoTime() - cropStart) / 1000
                        try {
                            // Preserve glyph scale. A bounded left prefix identifies a section without
                            // running the expensive recognizer over its entire ingredient/address row.
                            val prefixColumns = ceil(crop.rows() * (320.0 / 48)).toInt()
                            val partial = scout && crop.cols() > prefixColumns
                            val prefix = if (partial) crop.colRange(0, prefixColumns) else null
                            var result = try {
                                recognizeLine(prefix ?: crop, models, environment, index, stage, cropUs, profiles)
                            } finally { prefix?.release() }
                            if (!scout && result.second < 0.85f) {
                                val rotated = Mat()
                                try {
                                    Core.rotate(crop, rotated, Core.ROTATE_180)
                                    val reversed = recognizeLine(rotated, models, environment, index, "rotation", 0, profiles)
                                    if (reversed.second > result.second + 0.05f) {
                                        result = reversed
                                        invertedLines++
                                    }
                                } finally { rotated.release() }
                            }
                            var alternatives = emptyList<String>()
                            if (!scout && config.refineEnergy &&
                                EnergyLineRefinement.needed(result.first)) {
                                // Retry the pixels with less detector margin, rather than repairing units
                                // or selecting a plausible number in the parser.
                                val margin = (crop.rows() * 0.12).toInt()
                                if (margin > 0 && crop.rows() - margin * 2 >= 8) {
                                    val tight = crop.rowRange(margin, crop.rows() - margin)
                                    try {
                                        val refined = recognizeLine(tight, models, environment, index, "energy_retry", 0, profiles)
                                        if (EnergyLineRefinement.accept(result.first, refined.first, refined.second)) {
                                            alternatives = listOf(result.first)
                                            result = refined
                                        }
                                    } finally { tight.release() }
                                }
                            }
                            val line = OcrLine(result.first, result.second,
                                box.points.map { OcrPoint(it.x, it.y) }, alternatives)
                            if (!partial && (!scout || result.second >= 0.85f)) lines[index] = line
                            return line
                        } finally { crop.release() }
                    }
                    var fallback = false
                    if (config.progressive) {
                        val heights = boxes.map { box ->
                            box.points.maxOf { it.y } - box.points.minOf { it.y }
                        }.sorted()
                        val median = heights.getOrNull(heights.size / 2) ?: 1f
                        for (index in boxes.indices) {
                            val box = boxes[index]
                            val large = box.points.maxOf { it.y } - box.points.minOf { it.y } >= median * 1.4f
                            probes[index] = recognizeRegion(index, scout = !large, stage = if (large) "title" else "scout")
                        }
                        for (index in LabelRegionSelection.select(probes)) {
                            if (index !in lines) lines[index] = recognizeRegion(index, scout = false, stage = "selected")
                        }
                        val partialResult = PackageOcrResult(lines.values.filter { it.text.isNotBlank() },
                            source.cols(), source.rows(), OcrMetrics(initialization, detection, 0, 0))
                        fallback = invertedLines > partialResult.lines.size * 0.6 || !LabelRegionSelection.complete(partialResult)
                    }
                    if (!config.progressive || fallback) {
                        for (index in boxes.indices) {
                            if (index !in lines) lines[index] = recognizeRegion(index, scout = false,
                                stage = if (fallback) "fallback" else "full")
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    val recognition = SystemClock.elapsedRealtime() - recStart
                    val recognized = lines.values.filter { it.text.isNotBlank() }
                    val inverted = invertedLines > recognized.size * 0.6
                    if (inverted) coordinateRotation = (coordinateRotation + 180) % 360
                    val upright = if (inverted) recognized.map { line ->
                        line.copy(points = line.points.map { OcrPoint(source.cols() - it.x, source.rows() - it.y) })
                    } else recognized
                    PackageOcrResult(LabelReadingOrder.sort(upright), source.cols(), source.rows(),
                        OcrMetrics(initialization, detection, recognition, SystemClock.elapsedRealtime() - started),
                        regions = profiles, detectedRegions = boxes.size, fullFallback = fallback,
                        coordinateRotationDegrees = coordinateRotation)
                } finally { source.release() }
            } finally { if (!config.reuseSessions) models.close() }
        }
    }

    private fun loadModels(environment: OrtEnvironment): Models {
        val dictionary = context.assets.open("ocr/eslav/characters.json").bufferedReader().use {
            Json.decodeFromString<List<String>>(it.readText())
        }
        return OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(config.threads)
            options.setInterOpNumThreads(1)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            options.setCPUArenaAllocator(false)
            val detector = model(environment, options, "ocr/det/inference.onnx")
            try {
                val recognizer = model(environment, options, "ocr/eslav/inference.onnx")
                try {
                    checkShape(detector, recognition = false, dictionary.size)
                    checkShape(recognizer, recognition = true, dictionary.size)
                    Models(detector, recognizer, dictionary)
                } catch (error: Throwable) { recognizer.close(); throw error }
            } catch (error: Throwable) { detector.close(); throw error }
        }
    }

    private fun model(env: OrtEnvironment, options: OrtSession.SessionOptions, path: String): OrtSession =
        context.assets.open(path).use { env.createSession(it.readBytes(), options) }

    private fun checkShape(session: OrtSession, recognition: Boolean, dictionarySize: Int) {
        val input = session.inputInfo.values.single().info as TensorInfo
        require(input.shape.size == 4 && input.shape[1] == 3L) { "Unsupported OCR input" }
        val output = session.outputInfo.values.single().info as TensorInfo
        if (recognition) {
            require(input.shape[2] == 48L && output.shape.size == 3)
            require(output.shape[2] == dictionarySize + 1L) { "CTC dictionary mismatch" }
        } else require(output.shape.size == 4)
    }

    private fun detect(src: Mat, session: OrtSession, env: OrtEnvironment): List<OCRBox> {
        val scale = minOf(1.0, 1600.0 / maxOf(src.rows(), src.cols()))
        val height = ((src.rows() * scale / 32).roundToInt() * 32).coerceAtLeast(32)
        val width = ((src.cols() * scale / 32).roundToInt() * 32).coerceAtLeast(32)
        val resized = Mat()
        try {
            Imgproc.resize(src, resized, Size(width.toDouble(), height.toDouble()))
            val tensor = tensor(resized, doubleArrayOf(0.485, 0.456, 0.406), doubleArrayOf(0.229, 0.224, 0.225))
            val (probabilities, shape) = run(session, env, tensor, longArrayOf(1, 3, height.toLong(), width.toLong()))
            return DBPostProcessor.process(probabilities, shape, 0.3f, 0.55f, 1.5f,
                1000, false, "fast", "quad", src.rows(), src.cols())
        } finally { resized.release() }
    }

    private fun recognizeLine(
        src: Mat, models: Models, env: OrtEnvironment, index: Int, stage: String,
        cropUs: Long, profiles: MutableList<OcrRegionProfile>,
    ): Pair<String, Float> {
        val prepStart = System.nanoTime()
        val resizedWidth = ceil(48.0 * src.cols() / src.rows()).toInt().coerceIn(1, 3200)
        val width = maxOf(320, resizedWidth)
        val resized = Mat()
        try {
            Trace.beginSection("OCR.rec.$index.$stage")
            Imgproc.resize(src, resized, Size(resizedWidth.toDouble(), 48.0))
            // eslav configuration is BGR, pixel /127.5 -1; normalized padding remains zero.
            val data = tensor(resized, doubleArrayOf(0.5, 0.5, 0.5), doubleArrayOf(0.5, 0.5, 0.5), width)
            val prepUs = (System.nanoTime() - prepStart) / 1000
            val inferStart = System.nanoTime()
            val (probabilities, shape) = run(models.recognizer, env, data, longArrayOf(1, 3, 48, width.toLong()))
            val inferUs = (System.nanoTime() - inferStart) / 1000
            require(shape.size == 3 && shape[0] == 1L)
            val decodeStart = System.nanoTime()
            val result = CtcDecoder.decode(probabilities, shape[1].toInt(), shape[2].toInt(), models.dictionary)
            if (config.profile) profiles += OcrRegionProfile(index, stage, width, cropUs, prepUs, inferUs,
                (System.nanoTime() - decodeStart) / 1000, result.first, result.second)
            return result
        } finally { try { resized.release() } finally { Trace.endSection() } }
    }

    private fun tensor(mat: Mat, mean: DoubleArray, std: DoubleArray, paddedWidth: Int = mat.cols()): FloatArray {
        val plane = mat.rows() * paddedWidth
        val data = FloatArray(3 * plane)
        val row = ByteArray(mat.cols() * 3)
        repeat(mat.rows()) { y ->
            mat.get(y, 0, row)
            repeat(mat.cols()) { x ->
                repeat(3) { channel ->
                    data[channel * plane + y * paddedWidth + x] =
                        (((row[x * 3 + channel].toInt() and 255) / 255.0 - mean[channel]) / std[channel]).toFloat()
                }
            }
        }
        return data
    }

    private fun run(session: OrtSession, env: OrtEnvironment, data: FloatArray, shape: LongArray): Pair<FloatArray, LongArray> =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape).use { input ->
            session.run(mapOf(session.inputNames.single() to input)).use { result ->
                val output = result[0] as OnnxTensor
                val buffer = output.floatBuffer
                FloatArray(buffer.remaining()).also { buffer.get(it) } to output.info.shape
            }
        }
}
