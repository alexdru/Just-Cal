package com.justcal.app.ai

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NutritionExtractionTest {
    private val valid = """{"productName":"Yogurt","caloriesKcal":123.45,"nutritionBasisGrams":100,
        "proteinGrams":0,"fatGrams":null,"carbohydrateGrams":12.3,"packageWeightGrams":150}"""

    @Test fun keepsBasisPackageWeightAndMissingValuesDistinct() {
        val result = NutritionExtraction.parse(valid)
        assertEquals(BigDecimal("123.45"), result.caloriesKcal)
        assertEquals(BigDecimal("100"), result.nutritionBasisGrams)
        assertEquals(BigDecimal("150"), result.packageWeightGrams)
        assertEquals(BigDecimal.ZERO, result.proteinGrams)
        assertNull(result.fatGrams)
    }

    @Test fun acceptsCompleteFencedJsonAndEscapedNames() {
        assertEquals("Yogurt", NutritionExtraction.parse("```json\n$valid\n```").productName)
        assertEquals("Food \"light\"", NutritionExtraction.parse(
            valid.replace("Yogurt", "Food \\\"light\\\"")).productName)
    }

    @Test fun allMissingValuesRemainNull() {
        val result = NutritionExtraction.parse("""{"productName":null,"caloriesKcal":null,
            "nutritionBasisGrams":null,"proteinGrams":null,"fatGrams":null,
            "carbohydrateGrams":null,"packageWeightGrams":null}""")
        assertNull(result.caloriesKcal)
        assertNull(result.nutritionBasisGrams)
        assertNull(result.packageWeightGrams)
    }

    @Test fun rejectsInvalidAndAmbiguousNutrition() {
        listOf(
            valid.replace("123.45", "-1"),
            valid.replace("123.45", "\"123.45\""),
            valid.replace("123.45", "true"),
            valid.replace("123.45", "1e999"),
            valid.replace("123.45", "NaN"),
            valid.replace("\"nutritionBasisGrams\":100", "\"nutritionBasisGrams\":0"),
            valid.replace("\"packageWeightGrams\":150", "\"packageWeightGrams\":-150"),
            valid.replace("\"proteinGrams\":0,", ""),
            valid.replace("\"productName\":", "\"extra\":1,\"productName\":"),
            "$valid\n$valid",
            "Here is the result: $valid",
            valid.dropLast(1),
            "[]",
        ).forEach { bad -> assertTrue("Accepted: $bad", runCatching { NutritionExtraction.parse(bad) }.isFailure) }
    }
}
