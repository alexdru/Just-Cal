package com.justcal.app.ocr

import com.justcal.app.domain.FoodDraft
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable enum class LabelBasis { PER_100_G, PER_SERVING, UNKNOWN, AMBIGUOUS }
@Serializable data class LabelValue(val value: String, val confidence: Float, val evidence: String) {
    val confident: Boolean get() = confidence >= 0.8f
}
@Serializable data class ParsedNutritionLabel(
    val name: LabelValue? = null,
    val netGrams: LabelValue? = null,
    val energyKcal: LabelValue? = null,
    val proteinGrams: LabelValue? = null,
    val fatGrams: LabelValue? = null,
    val carbsGrams: LabelValue? = null,
    val basis: LabelBasis = LabelBasis.UNKNOWN,
) {
    /** Missing / low-confidence / unsupported-basis values remain empty, never implicit zeroes. */
    fun draft() = FoodDraft(
        name = formatRecognizedName(name?.takeIf { it.confident }?.value.orEmpty()),
        energy = energyKcal?.takeIf { it.confident && basis == LabelBasis.PER_100_G }?.value.orEmpty(),
        protein = proteinGrams?.takeIf { it.confident && basis == LabelBasis.PER_100_G }?.value.orEmpty(),
        fat = fatGrams?.takeIf { it.confident && basis == LabelBasis.PER_100_G }?.value.orEmpty(),
        carbs = carbsGrams?.takeIf { it.confident && basis == LabelBasis.PER_100_G }?.value.orEmpty(),
        amount = netGrams?.takeIf { it.confident }?.value.orEmpty(),
    )
}

private val nameWord = Regex("""[\p{L}\p{M}\p{N}]+(?:[-’'][\p{L}\p{M}\p{N}]+)*""")

/** Format only the editable OCR prefill; keep extracted values and evidence intact. */
private fun formatRecognizedName(value: String): String {
    val text = value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
    val deduplicated = buildString {
        var previousWord: String? = null
        var end = 0
        for (word in nameWord.findAll(text)) {
            val separator = text.substring(end, word.range.first)
            val repeated = word.value == previousWord && word.value.any(Char::isLetter) &&
                separator.isNotEmpty() && separator.all(Char::isWhitespace)
            if (!repeated) {
                append(separator)
                append(word.value)
            }
            previousWord = word.value
            end = word.range.last + 1
        }
        append(text.substring(end))
    }
    val firstLetter = deduplicated.indexOfFirst(Char::isLetter)
    return if (firstLetter < 0) deduplicated else deduplicated.replaceRange(
        firstLetter, firstLetter + 1, deduplicated[firstLetter].titlecase(Locale.ROOT),
    )
}

/** Context and units establish meaning. Never scans arbitrary label numbers for plausible nutrition. */
object NutritionLabelParser {
    private const val NUMBER = "([0-9]+(?:[.,]\\s*[0-9]{1,2})?)"
    private val per100 = Regex("(?:per\\s*|на\\s*)?100\\s*(?:g(?:rams?)?(?![a-zа-я])|г(?:рамм(?:а|ов)?)?(?:(?![a-zа-я])|(?=продукта(?![a-zа-яё]))))", RegexOption.IGNORE_CASE)
    private val serving = Regex("(?:per\\s+(?:serving|portion)|на\\s+(?:порци[юяи]|упаковку)|в\\s+одной\\s+порции|на\\s+вс[её]\\s+блюдо)", RegexOption.IGNORE_CASE)
    private val nutritionHeading = Regex("пищев[а-я]+\\s*(?:и\\s*энергетическ[а-я]+\\s*)?ценност[а-я]*|nutrition(?:al)?(?:\\s+(?:information|values?|facts))?", RegexOption.IGNORE_CASE)
    private val stop = Regex("состав[: ]|ingredients?[: ]|изготовител[ья]|manufacturer|рекомендации|приготовлени[ея]|cooking|хранить|условия\\s+хранения|storage|срок\\s+годности|best\\s+before|use\\s+by", RegexOption.IGNORE_CASE)
    private val macro = mapOf(
        "protein" to "белк[иа]|proteins?",
        "fat" to "(?<![а-я])жир[ыа](?![а-я])|(?<![a-z])(?:total\\s+)?fat(?![a-z])",
        "carbs" to "углевод[ыа]|carbohydrates?|carbs",
    )

    fun parse(result: PackageOcrResult): ParsedNutritionLabel = parse(result.lines, result.width, result.height)

    /** Reuses units/basis/conflict rules. Zero means unscored; these values never pass confident/draft(). */
    fun parseUnscoredTranscription(text: String): ParsedNutritionLabel =
        parse(text.take(16_000).lineSequence().filter(String::isNotBlank)
            .map { OcrLine(it, 0f, emptyList()) }.toList())

    fun parse(lines: List<OcrLine>, width: Int = 0, height: Int = 0): ParsedNutritionLabel {
        val ordered = LabelReadingOrder.sort(withoutDishTotalColumn(lines))
        val normalized = ordered.map { normalize(it.text) }
        val offsets = mutableListOf<Int>()
        val text = buildString {
            normalized.forEachIndexed { index, line ->
                offsets += length
                val wrappedWord = Regex("[а-яё][-‐]$").containsMatchIn(line) &&
                    normalized.getOrNull(index + 1)?.firstOrNull()?.let { it in 'а'..'я' || it == 'ё' } == true
                append(if (wrappedWord) line.dropLast(1) else line)
                if (!wrappedWord && index < normalized.lastIndex) append('\n')
            }
        }
        fun evidence(range: IntRange, value: BigDecimal): LabelValue {
            val affected = ordered.indices.filter { offsets[it] <= range.last && offsets[it] + normalized[it].length >= range.first }
            return LabelValue(value.stripTrailingZeros().toPlainString(),
                // A detached dash is formatting, not uncertain nutrient text.
                affected.filter { ordered[it].text.any(Char::isLetterOrDigit) }
                    .minOfOrNull { ordered[it].confidence } ?: 0f,
                affected.joinToString("\n") { ordered[it].text })
        }

        // OCR can omit spaces or render the entire Cyrillic label with Latin lookalikes.
        val netRegex = Regex("(?:масса\\s*нетт[оo]|вес\\s*нетт[оo]|macca\\s*hetto|net\\s+(?:weight|wt)[.]?)\\s*[:—–-]?\\s*" +
            "(?:\\(\\s*1\\s*порц[.]?\\s*\\)\\s*[:—–-]?\\s*)?([0-9]+(?:[.,]\\s*[0-9]{1,3})?)\\s*(kg|кг|g|г)(?![a-zа-я])", RegexOption.IGNORE_CASE)
        val weights = netRegex.findAll(text).mapNotNull { match ->
            val grams = decimal(match.groupValues[1])?.multiply(if (match.groupValues[2] in listOf("kg", "кг")) BigDecimal(1000) else BigDecimal.ONE)
            grams?.takeIf { it > BigDecimal.ZERO && it <= BigDecimal(100_000) }?.let { evidence(match.range, it) }
        }.toList()
        val net = weights.takeIf { it.map(LabelValue::value).distinct().size == 1 }?.maxByOrNull { it.confidence }

        // Select an explicitly per-100-g nutrition block, not an ingredient amount or net mass.
        val headers = nutritionHeading.findAll(text).toList()
        var basis = LabelBasis.UNKNOWN
        val blocks = mutableListOf<Pair<Int, String>>()
        val contextConfidence = mutableMapOf<Int, Float>()
        fun nutritionEvidence(range: IntRange, value: BigDecimal, start: Int): LabelValue {
            val field = evidence(range, value)
            return field.copy(confidence = minOf(field.confidence, contextConfidence.getValue(start)))
        }
        for (header in headers) {
            val headerEnd = minOf(text.length, header.range.last + 180)
            val vicinity = text.substring(header.range.first, headerEnd)
            val hundred = per100.find(vicinity)
            val portion = serving.find(vicinity)
            if (hundred != null && portion != null && portion.range.first < hundred.range.last + 50) {
                basis = LabelBasis.AMBIGUOUS
                continue
            }
            if (hundred == null) {
                if (portion != null && basis == LabelBasis.UNKNOWN) basis = LabelBasis.PER_SERVING
                continue
            }
            val start = header.range.first + hundred.range.last + 1
            val end = minOf(text.length, start + 1000,
                stop.find(text, start)?.range?.first ?: text.length,
                serving.find(text, start)?.range?.first ?: text.length,
                headers.firstOrNull { it.range.first > start }?.range?.first ?: text.length)
            if (end > start) {
                blocks += start to text.substring(start, end)
                contextConfidence[start] = evidence(header.range.first until start, BigDecimal.ZERO).confidence
            }
        }
        // English compact labels sometimes use just "Per 100 g" above the nutrient rows.
        if (headers.isEmpty()) {
            per100.findAll(text).forEach { match ->
                val start = match.range.last + 1
                val end = minOf(text.length, start + 700, stop.find(text, start)?.range?.first ?: text.length,
                    serving.find(text, start)?.range?.first ?: text.length)
                val block = text.substring(start, end)
                val previousStop = stop.findAll(text.substring(0, match.range.first)).lastOrNull()
                val sameLine = previousStop != null && !text.substring(previousStop.range.last + 1, match.range.first).contains("\n")
                if (!sameLine && macro.values.any { Regex(it).containsMatchIn(block) }) {
                    blocks += start to block
                    contextConfidence[start] = evidence(match.range, BigDecimal.ZERO).confidence
                }
            }
            if (blocks.isEmpty() && serving.containsMatchIn(text)) basis = LabelBasis.PER_SERVING
        }
        if (blocks.isNotEmpty() && basis != LabelBasis.AMBIGUOUS) basis = LabelBasis.PER_100_G

        fun nutrient(key: String): LabelValue? {
            if (basis != LabelBasis.PER_100_G) return null
            val candidates = mutableListOf<LabelValue>()
            for ((start, block) in blocks) {
                val regex = Regex("(?:" + macro.getValue(key) + ")[\\s:—–-]*" + NUMBER +
                    "\\s*[гgr](?![a-zа-я])", RegexOption.IGNORE_CASE)
                regex.findAll(block).forEach { match ->
                    decimal(match.groupValues[1])?.takeIf { it >= BigDecimal.ZERO && it <= BigDecimal(100) }?.let {
                        candidates += nutritionEvidence((start + match.range.first)..(start + match.range.last), it, start)
                    }
                }
            }
            return unique(candidates)
        }
        val energies = mutableListOf<LabelValue>()
        if (basis == LabelBasis.PER_100_G) for ((start, block) in blocks) {
            // Tables print a standalone kcal unit to the left of its value.
            // The separate whole-dish column was removed by geometry, never by its numbers.
            Regex("(?m)^\\s*(?:ккал|kcal)\\s*[:—–-]?\\s*\\n\\s*" + NUMBER + "\\s*(?=\\n|$)", RegexOption.IGNORE_CASE)
                .findAll(block).forEach { match ->
                    decimal(match.groupValues[1])?.takeIf { it >= BigDecimal.ZERO && it <= BigDecimal(1000) }?.let {
                        energies += nutritionEvidence((start + match.range.first)..(start + match.range.last), it, start)
                    }
                }
            Regex("(?m)^\\s*(?:ккал|kcal)\\s*/\\s*(?:кдж|kj)\\s*[:—–-]?\\s*\\n\\s*" +
                NUMBER + "\\s*/\\s*[0-9]+(?:[.,][0-9]{1,2})?\\s*(?=\\n|$)", RegexOption.IGNORE_CASE)
                .findAll(block).forEach { match ->
                    decimal(match.groupValues[1])?.takeIf { it >= BigDecimal.ZERO && it <= BigDecimal(1000) }?.let {
                        energies += nutritionEvidence((start + match.range.first)..(start + match.range.last), it, start)
                    }
                }
            val energyLabel = Regex("энергетическ[а-я]+\\s+ценност[а-я]*(?:\\s*\\([^)]*\\))?|калорийност[а-я]*|energy|calories", RegexOption.IGNORE_CASE)
            energyLabel.findAll(block).forEach { label ->
                val valueStart = label.range.last + 1
                // Bound the energy segment: no addresses, shelf life or later nutrient fields.
                val valueEnd = minOf(block.length, valueStart + 100)
                val segment = block.substring(valueStart, valueEnd)
                val energyValues = Regex("(?<![0-9.,])" + NUMBER + "(?![0-9.,])\\s*(ккал|kcal|кдж|kj)(?![a-zа-я])", RegexOption.IGNORE_CASE).findAll(segment).toList()
                val kcal = energyValues.firstOrNull { it.groupValues[2].lowercase(Locale.ROOT) in listOf("ккал", "kcal") }
                val chosen = kcal ?: energyValues.firstOrNull()
                if (chosen != null) {
                    var value = decimal(chosen.groupValues[1])
                    if (kcal == null) value = value?.divide(BigDecimal("4.184"), 2, RoundingMode.HALF_UP)
                    value?.takeIf { it >= BigDecimal.ZERO && it <= BigDecimal(1000) }?.let {
                        val extracted = nutritionEvidence((start + label.range.first)..(start + valueStart + chosen.range.last), it, start)
                        // A second energy amount with a damaged kcal unit needs review even though
                        // the kJ conversion is mathematically valid. Never silently prefill it.
                        energies += if (kcal == null && EnergyLineRefinement.needed(segment))
                            extracted.copy(confidence = minOf(extracted.confidence, 0.79f)) else extracted
                    }
                }
            }
        }
        return ParsedNutritionLabel(productName(ordered, width, height), net, unique(energies),
            nutrient("protein"), nutrient("fat"), nutrient("carbs"), basis)
    }

    /** Keep the explicitly per-100-g table separate from a neighboring whole-dish callout. */
    private fun withoutDishTotalColumn(lines: List<OcrLine>): List<OcrLine> {
        val total = lines.firstOrNull { it.points.isNotEmpty() && it.confidence >= 0.8f &&
            Regex("^\\s*вс[её]\\s*блюдо\\s*[:：]?\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(it.text) } ?: return lines
        val header = lines.filter { it.points.isNotEmpty() && per100.containsMatchIn(normalize(it.text)) &&
            it.left < total.left && it.top <= total.bottom && it.bottom >= total.top - total.height * 4 }
            .maxByOrNull { it.bottom } ?: return lines
        val end = lines.filter { it.top > header.bottom &&
            Regex("температур|дата\\s+изготов|хранени|срок\\s+годности", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
            .minOfOrNull { it.top } ?: (header.bottom + header.height * 12)
        return lines.filterNot { it.top >= minOf(header.top, total.top) && it.top < end &&
            (it.left + it.right) / 2 >= total.left }
    }

    private fun unique(values: List<LabelValue>): LabelValue? =
        values.takeIf { it.map(LabelValue::value).distinct().size == 1 }?.maxByOrNull { it.confidence }

    private fun decimal(text: String): BigDecimal? = text.replace(" ", "").replace(',', '.').toBigDecimalOrNull()

    /** Correct only common Latin homoglyphs inside Cyrillic words, never rewrite raw OCR evidence. */
    private fun normalize(text: String): String {
        val homoglyphs = mapOf('a' to 'а', 'c' to 'с', 'e' to 'е', 'k' to 'к', 'm' to 'м', 'o' to 'о', 'p' to 'р', 't' to 'т', 'x' to 'х', 'y' to 'у')
        return Regex("[a-zа-яё]+").replace(text.lowercase(Locale.ROOT).replace('\u00a0', ' ')) { match ->
            if (match.value.any { it in 'а'..'я' || it == 'ё' }) match.value.map { homoglyphs[it] ?: it }.joinToString("")
            else match.value
        }.replace(Regex("([а-я])[-‐]\\s+([а-я])"), "$1$2")
            .replace(Regex("(?<=\\d)\\s*([.,])\\s*(?=\\d)"), "$1")
    }

    private fun productName(lines: List<OcrLine>, width: Int, height: Int): LabelValue? {
        if (lines.isEmpty()) return null
        // Prefer the consumer title: larger adjacent, left-aligned lines above the dense label text.
        val bodyTop = lines.filter { Regex("состав[: ]|ingredients?[: ]|пищев|nutrition", RegexOption.IGNORE_CASE)
            .containsMatchIn(normalize(it.text)) }.minOfOrNull { it.top } ?: Float.POSITIVE_INFINITY
        val candidates = lines.filter {
            val normalized = normalize(it.text)
            it.top < bodyTop && it.confidence >= 0.8f && it.text.length in 3..140 && it.text.count(Char::isLetter) >= 3 &&
                !Regex("[0-9]|готовая\\s+еда|вкусвилл|классика|состав|ingredients|нетто|weight|ценност|пищев|энергет|углевод|белки|жиры|ккал|кдж|крахмал|добавк|сорбат|мускат|консервант|рафинир|масло|сахар|соль|свекл|молот|высшего|пшенич|перец|nutrition|изготовител|срок|курица$|говядина$|energy|protein|fat|carb|[.]ru|штрих|издели[ея]|кулинарн", RegexOption.IGNORE_CASE).containsMatchIn(normalized) &&
                (height == 0 || it.top < height * 0.65f) && (width == 0 || it.left < width * 0.6f)
        }
        val first = candidates.maxByOrNull { it.height } ?: return null
        val title = candidates.filter { line ->
            line.height >= first.height * 0.65f && line.height <= first.height * 1.6f &&
                kotlin.math.abs(line.left - first.left) < first.height * 1.5f &&
                line.top <= first.bottom + first.height * 3 && line.bottom >= first.top - first.height * 3
        }.sortedBy { it.top }.take(5)
        val value = title.joinToString(" ") { it.text.trim() }.replace(Regex("\\s+"), " ")
        // A one-word display category (e.g. bowl) may have its complete name in the legal title.
        if (value.split(" ").size == 1) {
            val legalTitle = lines.firstOrNull {
                Regex("(?:кулинарное изделие|изделие кулинарное)[^:]{0,100}:", RegexOption.IGNORE_CASE).containsMatchIn(it.text)
            }
            if (legalTitle != null) {
                val name = legalTitle.text.substringAfter(':').substringBefore("МАССА", legalTitle.text.substringAfter(':')).trim()
                if (name.length in 8..160) return LabelValue(name, legalTitle.confidence, legalTitle.text)
            }
        }
        return LabelValue(value, title.minOf { it.confidence }, title.joinToString("\n") { it.text })
    }
}
