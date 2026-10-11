package com.justcal.app.ocr

/** PP-OCR CTC probabilities: blank at 0, dictionary characters at 1..N (including appended space). */
internal object CtcDecoder {
    fun decode(probabilities: FloatArray, steps: Int, classes: Int, dictionary: List<String>): Pair<String, Float> {
        require(classes == dictionary.size + 1) { "Recognition model and dictionary do not match" }
        require(steps > 0 && probabilities.size == steps * classes)
        val text = StringBuilder()
        var previous = -1
        var confidence = 0.0
        var count = 0
        repeat(steps) { step ->
            val start = step * classes
            var best = 0
            for (index in 1 until classes) if (probabilities[start + index] > probabilities[start + best]) best = index
            if (best != 0 && best != previous) {
                text.append(dictionary[best - 1])
                confidence += probabilities[start + best].coerceIn(0f, 1f)
                count++
            }
            previous = best
        }
        return text.toString() to if (count == 0) 0f else (confidence / count).toFloat()
    }
}
