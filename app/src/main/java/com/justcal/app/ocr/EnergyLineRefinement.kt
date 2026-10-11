package com.justcal.app.ocr

/** Retry selection never synthesizes text and rejects changes to any numerical token. */
internal object EnergyLineRefinement {
    private val kilojoules = Regex("(?:кдж|kj)(?![a-zа-я])", RegexOption.IGNORE_CASE)
    private val ambiguous = Regex("[0-9]\\s*(?:кал|cal)(?![a-zа-я])", RegexOption.IGNORE_CASE)
    private val kcal = Regex("[0-9]\\s*(?:ккал|kcal)(?![a-zа-я])", RegexOption.IGNORE_CASE)
    private val numbers = Regex("[0-9]+(?:[.,]\\s*[0-9]+)?")

    fun needed(text: String): Boolean = kilojoules.containsMatchIn(text) && ambiguous.containsMatchIn(text)

    fun accept(original: String, candidate: String, confidence: Float): Boolean =
        needed(original) && confidence >= .8f && kcal.containsMatchIn(candidate) &&
            numericTokens(original) == numericTokens(candidate)

    private fun numericTokens(text: String): List<String> = numbers.findAll(text).map {
        it.value.replace(" ", "").replace(',', '.').toBigDecimal().stripTrailingZeros().toPlainString()
    }.toList()
}
