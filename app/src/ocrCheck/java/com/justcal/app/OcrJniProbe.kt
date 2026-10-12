package com.justcal.app

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import com.justcal.app.camera.ImagePreprocessor
import com.justcal.app.camera.ImageSource
import com.justcal.app.camera.ScanMode
import com.justcal.app.camera.ScanRequest
import com.justcal.app.ocr.NutritionLabelParser
import com.justcal.app.ocr.PaddlePackageOcrEngine
import kotlinx.coroutines.runBlocking

/**
 * Local verification entry point compiled into the minified app itself.
 * This avoids instrumentation's shared, unminified Kotlin/AndroidX classpath.
 * Only the debug-signed ocrCheck variant includes this class/manifest entry.
 */
class OcrJniProbe : Instrumentation() {
    private var large = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        large = arguments?.getString("large") == "true"
        start()
    }

    override fun onStart() {
        val outcome = Bundle()
        var result = Activity.RESULT_CANCELED
        try {
            runBlocking {
                val context = targetContext
                val images = ImagePreprocessor(context)
                val bitmap = Bitmap.createBitmap(if (large) 3000 else 1200,
                    if (large) 4000 else 800, Bitmap.Config.ARGB_8888)
                val file = images.newCaptureFile()
                try {
                    val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
                    val scale = if (large) 2.5f else 1f
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 44f * scale }
                    listOf("РИС С КУРИЦЕЙ", "Масса нетто: 240 г", "Пищевая ценность на 100 г",
                        "Белки 11 г", "Жиры 6,4 г", "Углеводы 20,4 г",
                        "Энергетическая ценность: 183,2 ккал").forEachIndexed { index, text ->
                        canvas.drawText(text, 40f * scale, (80f + index * 95) * scale, paint)
                    }
                    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally { bitmap.recycle() }
                val image = images.prepare(Uri.fromFile(file), ImageSource.CAMERA, ScanRequest(ScanMode.PACKAGE, 123))
                try {
                    val recognized = PaddlePackageOcrEngine(context, images).recognize(images.verify(image))
                    val label = NutritionLabelParser.parse(recognized)
                    check(recognized.lines.any { it.text.contains("РИС") })
                    check(label.netGrams?.value == "240" && label.energyKcal?.value == "183.2" && label.proteinGrams?.value == "11")
                    outcome.putString("stream", "MINIFIED_OCR_OK ${image.width}x${image.height} regions=${recognized.detectedRegions} metrics=${recognized.metrics}\n")
                    result = Activity.RESULT_OK
                } finally { images.discard(image.uri) }
            }
        } catch (error: Throwable) {
            outcome.putString("stream", "MINIFIED_OCR_FAILED ${error.stackTraceToString()}\n")
        }
        finish(result, outcome)
    }
}
