package com.justcal.app.ai

import com.justcal.app.ocr.OcrLine
import com.justcal.app.ocr.OcrPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LabelImageCropperTest {
    @Test fun foodGradeSaltAndAdditivesNeverStartTheNutritionCrop() {
        val heading = line("Пищевая ценность на 100 г продукта")
        assertSame(heading, findNutritionCropHeading(listOf(
            line("Состав: вода, соль поваренная пищевая, масло."),
            line("пищевая добавка сорбат калия"),
            heading,
            line("Белки 4,5 г, жиры 3,6 г, углеводы 24,8 г"),
        )))
        assertNull(findNutritionCropHeading(listOf(
            line("Состав: соль пищевая, масло, мука."),
            line("пищевая добавка сорбат калия"),
        )))
    }

    @Test fun wrappedRussianHeadingAndEnglishNutritionHeadingsAreSupported() {
        for (text in listOf(
            "Пищевая и энергетическая",
            "Пищевая иэнергетическая",
            "Пищеваяценность 100 г продукта",
            "ПИЩЕВАЯ ЦЕННОСТЬ 100 г продукта",
            "Nutrition Facts",
            "Nutritional information per 100 g",
            "Nutrition",
        )) {
            val heading = line(text)
            assertSame(text, heading, findNutritionCropHeading(listOf(line("nutritional yeast"), heading)))
        }
    }

    @Test fun uncertainOrUnrelatedTextDoesNotInventACrop() {
        assertNull(findNutritionCropHeading(listOf(
            line("Пищевая ценность на 100 г", .79f),
            line("nutritionist advice"),
            line("пищевые волокна 5 г"),
            line("Энергетическая ценность 100 ккал"),
        )))
    }

    @Test fun ingredientRowsStorageAndSeparateWholeDishColumnStayOutsideTheBlock() {
        val heading = box("Пищевая иэнергетическая", 100f, 100f, 400f)
        val basis = box("ценность на 100 г продукта:", 100f, 125f, 430f)
        val protein = box("Белки 4,5 г", 100f, 155f, 350f)
        val energy = box("ккал 150", 100f, 185f, 350f)
        val block = requireNotNull(selectNutritionCropBlock(listOf(
            box("соль поваренная пищевая", 100f, 70f, 700f),
            heading, basis,
            box("Всё блюдо:", 600f, 125f, 750f),
            protein,
            box("374 ккал", 600f, 155f, 750f),
            energy,
            box("Температура хранения: +2…+6 °C", 100f, 220f, 700f),
        )))
        assertSame(heading, block.heading)
        assertEquals(listOf(heading, basis, protein, energy), block.lines)
    }

    @Test fun separatePortionColumnCanAlsoBeOnTheLeft() {
        val heading = box("Nutrition information per 100 g", 500f, 100f, 800f)
        val energy = box("Energy 150 kcal", 500f, 150f, 750f)
        val block = requireNotNull(selectNutritionCropBlock(listOf(
            heading,
            box("Per serving", 50f, 125f, 300f),
            box("374 kcal", 50f, 150f, 300f),
            energy,
        )))
        assertEquals(listOf(heading, energy), block.lines)
        assertNull(selectNutritionCropBlock(listOf(heading)))
    }

    private fun box(text: String, left: Float, top: Float, right: Float) = OcrLine(
        text, .99f, listOf(
            OcrPoint(left, top), OcrPoint(right, top),
            OcrPoint(right, top + 20f), OcrPoint(left, top + 20f),
        ),
    )

    private fun line(text: String, confidence: Float = .99f) = OcrLine(text, confidence, emptyList())
}
