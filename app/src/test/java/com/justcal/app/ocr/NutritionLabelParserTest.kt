package com.justcal.app.ocr

import com.justcal.app.domain.FoodValidator
import com.justcal.app.domain.NutritionCalculator
import org.junit.Assert.*
import org.junit.Test

class NutritionLabelParserTest {
    private fun parse(vararg texts: String): ParsedNutritionLabel = NutritionLabelParser.parse(texts.mapIndexed { index, text ->
        OcrLine(text, 0.98f, listOf(OcrPoint(0f, index * 30f), OcrPoint(1000f, index * 30f),
            OcrPoint(1000f, index * 30f + 20), OcrPoint(0f, index * 30f + 20)))
    })

    @Test fun recognizedNameUsesSentenceCaseAndCollapsesOnlyAdjacentWords() {
        val cases = mapOf(
            "  ЙОГУРТ   йогурт ЙоГуРт С ВИШНЕЙ  " to "Йогурт с вишней",
            "GREEK greek YOGURT YOGURT" to "Greek yogurt",
            "СЫР С СЫРОМ" to "Сыр с сыром",
            "РИС С РИСОМ РИС" to "Рис с рисом рис",
            "«ЁЖИК ёжик» В СОУСЕ" to "«Ёжик» в соусе",
            "БИО-ЙОГУРТ био-йогурт БИО БИО-ЙОГУРТ" to "Био-йогурт био био-йогурт",
            "3D ПРОДУКТ ПРОДУКТ 20 20" to "3D продукт 20 20",
            "МОЛОКО молоко, БЕЗ САХАРА" to "Молоко, без сахара",
            " \n\t " to "",
        )
        cases.forEach { (raw, expected) ->
            val label = ParsedNutritionLabel(name = LabelValue(raw, .98f, raw))
            assertEquals(raw, expected, label.draft().name)
            assertEquals(raw, label.name?.value)
            assertEquals(raw, label.name?.evidence)
        }
    }

    @Test fun nameFormattingIsIndependentOfDeviceLocaleAndKeepsUncertainNamesEmpty() {
        val originalLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"))
            val label = ParsedNutritionLabel(name = LabelValue("ICE ICE CREAM", .98f, "ICE ICE CREAM"))
            assertEquals("Ice cream", label.draft().name)
            assertEquals("", label.copy(name = label.name!!.copy(confidence = .5f)).draft().name)
            assertEquals("", ParsedNutritionLabel().draft().name)
        } finally {
            java.util.Locale.setDefault(originalLocale)
        }
    }

    @Test fun compactProductBasisKeepsExplicitHundredButNeverRepairsDamagedDigits() {
        val label = parse("Пищевая ценность 100 гпродукта (средние значения):",
            "Белки 12,3 г; жиры 4,3 г; углеводы 1,4 г.", "Энергетическая ценность: 93,5 ккал.")
        assertEquals(LabelBasis.PER_100_G, label.basis)
        assertEquals("12.3", label.draft().protein)
        assertEquals("4.3", label.draft().fat)
        assertEquals("1.4", label.draft().carbs)
        assertEquals("93.5", label.draft().energy)
        assertEquals(LabelBasis.UNKNOWN, parse("Пищевая ценность 10б г продукта:", "Белки 12,3 г").basis)
        assertEquals(LabelBasis.UNKNOWN, parse("Пищевая ценность 100 глюкозы:", "Белки 12,3 г").basis)
    }

    @Test fun russianDenseLabelDecimalCommasAndNetMass() {
        val label = parse("Масса нетто: 240 г", "Пищевая ценность 100 г продукта (средние значения): белки — 11 г; жиры — 6,4 г",
            "в т.ч. насыщенные жирные кислоты — 2,4 г; углеводы — 20,4 г; сахар — 1 г",
            "Энергетическая ценность (калорийность): 770,6 кДж / 183,2 ккал.")
        assertEquals(LabelBasis.PER_100_G, label.basis)
        assertEquals("240", label.netGrams?.value)
        assertEquals("183.2", label.energyKcal?.value)
        assertEquals("11", label.proteinGrams?.value)
        assertEquals("6.4", label.fatGrams?.value)
        assertEquals("20.4", label.carbsGrams?.value)
    }

    @Test fun pairedEnergyUnitsBeforeNumbersKeepDeclaredKcal() {
        val label = parse("Пищевая и энергетическая ценность на 100 г:", "Белки", "11 г",
            "Ккал/кДж", "189/790")
        assertEquals("189", label.energyKcal?.value)
        assertEquals("189", label.draft().energy)
        assertNull(parse("Пищевая ценность на 100 г", "Ккал/кДж", "189").energyKcal)
    }

    @Test fun wholeDishEnergyOnNextLineDoesNotConflictWithPer100g() {
        val label = parse("Пищевая ценность на 100 г продукта: белки — 55 г, жиры — 7,6 г, углеводы — 1,2 г.",
            "Энергетическая ценность на 100 г продукта: 293 ккал / 1227 кДж.",
            "Энергетическая ценность на всё блюдо: 103 ккал / 429 кДж.")
        assertEquals("293", label.energyKcal?.value)
        assertEquals("293", label.draft().energy)
        assertNull(label.netGrams)
    }

    @Test fun englishLabelAndKilograms() {
        val label = parse("Net weight: 0.205 kg", "Nutrition per 100 g", "Protein: 5.9 g Fat: 3.7 g Carbohydrates: 10.4 g", "Energy: 98.5 kcal")
        assertEquals("205", label.netGrams?.value)
        assertEquals("98.5", label.energyKcal?.value)
        assertEquals("5.9", label.proteinGrams?.value)
        assertEquals("3.7", label.fatGrams?.value)
        assertEquals("10.4", label.carbsGrams?.value)
    }

    @Test fun kilojoulesOnlyUseExactConversionAndTwoDecimalPlaces() {
        assertEquals("100", parse("Nutrition per 100 g", "Energy: 418.4 kJ").energyKcal?.value)
        assertEquals("98.95", parse("Пищевая ценность на 100 г", "Энергетическая ценность: 414 кДж").energyKcal?.value)
    }

    @Test fun missingValuesAreNotZeroAndPackageIsNotNutrition() {
        val label = parse("Net weight 200 g", "Nutrition per 100 g", "Protein 9.7 g")
        assertNull(label.energyKcal)
        assertNull(label.fatGrams)
        assertNull(label.carbsGrams)
        assertEquals("", label.draft().fat)
        assertEquals("200", label.draft().amount)
    }

    @Test fun ignoresIngredientsDatesBarcodesAddressesAndCooking() {
        val label = parse("Ingredients: protein 20 g, fat 5 g, sugar 10 g per 100 g",
            "04.10.2026 2100100533373 +7 (495) 663-86-02", "Cook at 700 W for 3 minutes", "Shelf life 5 days",
            "Net weight 200 g", "Nutrition per 100 g", "Protein 9.7 g", "Cooking instructions: Fat 20 g Energy 700 kcal")
        assertEquals("9.7", label.proteinGrams?.value)
        assertNull(label.fatGrams)
        assertNull(label.energyKcal)
    }

    @Test fun servingOnlyNeverPrefillsPer100gValues() {
        val label = parse("Net weight 205 g", "Nutrition per serving", "Protein 12 g Fat 8 g Carbohydrates 21 g Energy 202 kcal")
        assertEquals(LabelBasis.PER_SERVING, label.basis)
        assertEquals("", label.draft().energy)
        assertEquals("", label.draft().protein)
        assertEquals("205", label.draft().amount)
    }

    @Test fun laterServingEnergyDoesNotOverridePer100g() {
        val label = parse("Пищевая ценность 100 г продукта: белки — 5,9 г; жиры — 3,7 г; углеводы — 10,4 г",
            "Энергетическая ценность: 98,5 ккал.", "В одной порции (205 г) содержится 202 ккал.", "Масса нетто: 205 г")
        assertEquals("98.5", label.energyKcal?.value)
        assertEquals("205", label.netGrams?.value)
    }

    @Test fun conflictingTablesStayEditableWithoutGuessing() {
        val label = parse("Nutrition per 100 g", "Protein 2 g", "Nutrition per 100 g", "Protein 8 g")
        assertNull(label.proteinGrams)
    }

    @Test fun explicitZeroAndWrappedLabelAreSupported() {
        val label = parse("Пищевая ценность на 100 г", "Бeлки —", "0 г", "Жиры — 6,4 г", "Углеводы — 20,4 г")
        assertEquals("0", label.proteinGrams?.value)
        assertEquals("6.4", label.fatGrams?.value)
    }

    @Test fun lowConfidenceEvidenceDoesNotBecomePrefill() {
        val label = NutritionLabelParser.parse(listOf(
            OcrLine("Nutrition per 100 g", 0.99f, emptyList()),
            OcrLine("Protein 11 g", 0.5f, emptyList())))
        assertEquals("11", label.proteinGrams?.value)
        assertEquals("", label.draft().protein)
    }

    @Test fun unsupportedPrecisionCannotMatchJustTheDecimalSuffix() {
        assertNull(parse("Nutrition per 100 g", "Energy 196.800 kcal").energyKcal)
    }

    @Test fun largerAlignedTitleLinesReconstructMultilineProductName() {
        fun line(text: String, x: Float, y: Float, h: Float) = OcrLine(text, 0.98f,
            listOf(OcrPoint(x, y), OcrPoint(x + 400, y), OcrPoint(x + 400, y + h), OcrPoint(x, y + h)))
        val label = NutritionLabelParser.parse(listOf(
            line("ТАЛЬЯТЕЛЛЕ", 50f, 30f, 60f), line("С КУРИЦЕЙ", 50f, 100f, 60f),
            line("В СЫРНОМ СОУСЕ", 50f, 170f, 60f),
            line("Пищевая ценность 100 г", 50f, 500f, 20f)), 1000, 1000)
        assertEquals("ТАЛЬЯТЕЛЛЕ С КУРИЦЕЙ В СЫРНОМ СОУСЕ", label.name?.value)
    }

    @Test fun hyphenatedNutrientWordContinuesOnNextLine() {
        val label = parse("Пищевая ценность 100 г", "Угле-", "воды — 20,4 г",
            "Энергетическая", "ценность: 183,2 ккал")
        assertEquals("20.4", label.carbsGrams?.value)
        assertEquals("183.2", label.energyKcal?.value)
    }

    @Test fun lowConfidenceBasisNeverBecomesConfidentNutrition() {
        val label = NutritionLabelParser.parse(listOf(
            OcrLine("Nutrition per 100 g", 0.5f, emptyList()),
            OcrLine("Protein 11 g", 0.99f, emptyList())))
        assertEquals("11", label.proteinGrams?.value)
        assertEquals("", label.draft().protein)
    }

    @Test fun ingredientsWithoutNutritionHeadingNeverBecomeNutrition() {
        val label = parse("Ingredients: protein 20 g per 100 g, Fat 10 g")
        assertNull(label.fatGrams)
        assertNull(label.proteinGrams)
        assertEquals(LabelBasis.UNKNOWN, label.basis)
    }

    @Test fun ambiguousServingAndHundredGramHeaderRemainsEmpty() {
        val label = parse("Nutrition per serving / per 100 g", "Protein 11 g", "Energy 100 kcal")
        assertEquals(LabelBasis.AMBIGUOUS, label.basis)
        assertEquals("", label.draft().protein)
        assertEquals("", label.draft().energy)
    }

    @Test fun compactAndFullyLatinLookalikeNetLabelsAreRecognized() {
        assertEquals("200", parse("ОХЛАЖДЁННОЕ: ПРОДУКТ.МАССАНЕТТО:200 Г").netGrams?.value)
        assertEquals("240", parse("MACCA HETTO: 240 Г").netGrams?.value)
    }

    @Test fun detachedLowConfidenceDashesDoNotHideConfidentProtein() {
        val label = NutritionLabelParser.parse(listOf(
            OcrLine("Пищевая ценность 100 г продукта: белки", 0.99f, emptyList()),
            OcrLine("-", 0.3f, emptyList()),
            OcrLine("-9,7 г;жиры - 8,8 г,", 0.89f, emptyList()),
        ))
        assertEquals("9.7", label.proteinGrams?.value)
        assertEquals("9.7", label.draft().protein)
        assertEquals("8.8", label.fatGrams?.value)
        // Meaningful low-confidence numbers must still prevent prefilling.
        val uncertain = NutritionLabelParser.parse(listOf(
            OcrLine("Nutrition per 100 g", 0.99f, emptyList()),
            OcrLine("Protein", 0.99f, emptyList()),
            OcrLine("9.7 g", 0.3f, emptyList()),
        ))
        assertEquals("", uncertain.draft().protein)
    }

    @Test fun culinaryDescriptionDoesNotJoinTheConsumerTitle() {
        fun line(text: String, y: Float, h: Float) = OcrLine(text, 0.98f,
            listOf(OcrPoint(50f, y), OcrPoint(600f, y),
                OcrPoint(600f, y + h), OcrPoint(50f, y + h)))
        val label = NutritionLabelParser.parse(listOf(
            line("ТАЛЬЯТЕЛЛЕ", 30f, 60f),
            line("С КУРИЦЕЙ", 100f, 60f),
            line("В СЫРНОМ СОУСЕ", 170f, 60f),
            line("ИЗДЕЛИЕ КУЛИНАРНОЕ ИЗ МЯСА ПТИЦЫ ВАРЁНОЕ, ОХЛАЖДЁННОЕ:", 310f, 45f),
        ), 1000, 1000)
        assertEquals("ТАЛЬЯТЕЛЛЕ С КУРИЦЕЙ В СЫРНОМ СОУСЕ", label.name?.value)
    }

    @Test fun missingKcalLetterIsNotSilentlyInventedFromReferenceExpectations() {
        // Actual OCR text: declared kcal became "кал". Only kJ has a reliable unit.
        val label = parse("Nutrition per 100 g",
            "Энергетическаяценность (калорийность):770,6 кДж/183,2 кал.")
        assertEquals("184.18", label.energyKcal?.value)
        assertTrue(label.energyKcal?.evidence.orEmpty().contains("770,6"))
        assertFalse(label.energyKcal!!.confident)
        assertEquals("", label.draft().energy)
    }

    @Test fun tableKeepsStandaloneKcalAndNeighboringDishTotalSeparate() {
        fun line(text: String, x: Float, y: Float, w: Float = 140f) = OcrLine(text, .98f,
            listOf(OcrPoint(x, y), OcrPoint(x + w, y), OcrPoint(x + w, y + 20), OcrPoint(x, y + 20)))
        val label = NutritionLabelParser.parse(listOf(
            line("Пищевая и энергетическая", 20f, 0f, 350f),
            line("ценность на 100 г продукта:", 20f, 30f, 350f),
            line("Всё блюдо:", 600f, 30f),
            line("белки", 20f, 60f), line("5r", 250f, 60f), line("345 ккал", 600f, 60f),
            line("жиры", 20f, 90f), line("4,4r", 250f, 90f),
            line("углеводы", 20f, 120f), line("19,5r", 250f, 120f), line("1442 кДж", 600f, 120f),
            line("ккал", 20f, 150f), line("138", 250f, 150f),
            line("кДж", 20f, 180f), line("576", 250f, 180f),
        ))
        assertEquals(LabelBasis.PER_100_G, label.basis)
        assertEquals("138", label.draft().energy)
        assertEquals("5", label.draft().protein)
        assertEquals("4.4", label.draft().fat)
        assertEquals("19.5", label.draft().carbs)
        assertNull(label.netGrams)
    }

    @Test fun portionCalculationUsesExistingDeterministicCalculator() {
        val label = parse("Net weight 240 g", "Nutrition per 100 g", "Protein 11 g Fat 6.4 g Carbohydrates 20.4 g", "Energy 183.2 kcal")
        val food = requireNotNull(FoodValidator.validate(label.draft().copy(name = "Food")).food)
        val consumed = NutritionCalculator.consumed(food.per100g, food.eatenGramsHundredths)
        assertEquals("439.68", consumed.energyKcal.stripTrailingZeros().toPlainString())
        assertEquals("26.4", consumed.proteinGrams.stripTrailingZeros().toPlainString())
    }
}
