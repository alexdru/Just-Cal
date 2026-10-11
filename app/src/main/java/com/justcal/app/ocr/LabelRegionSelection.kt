package com.justcal.app.ocr

import java.util.Locale

/** Prefixes locate regions only; truncated text is never passed to the nutrition parser. */
internal object LabelRegionSelection {
    private val nutrition = Regex("пищев[а-яё]*\\s+ц|nutrition|per\\s*100|на\\s*100", RegexOption.IGNORE_CASE)
    private val boundary = Regex("состав|ingredients|изготовител|manufacturer|рекомендац|cooking|хранить|storage|условия", RegexOption.IGNORE_CASE)
    private val important = Regex("масс|нетт|net\\s*(weight|wt)|macca|срок\\s*годности|best\\s*before|кулинар|изделие|охлажд", RegexOption.IGNORE_CASE)

    fun select(probes: Map<Int, OcrLine>): Set<Int> {
        val sorted = probes.entries.sortedBy { it.value.top }
        val selected = mutableSetOf<Int>()
        val medianHeight = sorted.map { it.value.height }.sorted().let { it.getOrNull(it.size / 2) ?: 1f }
        sorted.take(6).filter { !boundary.containsMatchIn(it.value.text) }.forEach { selected += it.key }
        for ((index, line) in sorted) {
            if (line.height >= medianHeight * 1.4f || important.containsMatchIn(line.text)) selected += index
            if (!nutrition.containsMatchIn(line.text.lowercase(Locale.ROOT))) continue
            // Include split heading and adjacent nutrient rows, ending at the next unrelated section.
            val end = sorted.firstOrNull {
                it.value.top > line.bottom && boundary.containsMatchIn(it.value.text)
            }?.value?.top ?: (line.bottom + line.height * 8)
            sorted.filter {
                it.value.bottom >= line.top - line.height &&
                    it.value.top < minOf(end, line.bottom + line.height * 8)
            }.forEach { selected += it.key }
        }
        return selected
    }

    fun complete(result: PackageOcrResult): Boolean {
        val parsed = NutritionLabelParser.parse(result)
        return parsed.basis == LabelBasis.PER_100_G &&
            listOf(parsed.name, parsed.netGrams, parsed.energyKcal, parsed.proteinGrams,
                parsed.fatGrams, parsed.carbsGrams).all { it?.confident == true }
    }
}
