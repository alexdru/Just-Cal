package com.justcal.app.domain

import org.junit.Assert.*
import org.junit.Test

class FoodValidatorTest {
    private val draft = FoodDraft(" Yogurt ", "205,50", "12.34", "5.67", "20.01", "37,50")
    @Test fun parsesBothDecimalSeparatorsAndTrimsName() {
        val result = FoodValidator.validate(draft)
        assertTrue(result.errors.isEmpty())
        val food = requireNotNull(result.food)
        assertEquals("Yogurt", food.name)
        assertEquals(20550L, food.per100g.energyKcalHundredths)
        assertEquals(3750L, food.eatenGramsHundredths)
    }
    @Test fun reportsErrorsForTheActualFields() {
        val result = FoodValidator.validate(draft.copy(name = " ", energy = "-1", protein = "1.234", fat = "NaN", amount = "0"))
        assertEquals(InputError.REQUIRED, result.errors[FoodField.NAME])
        assertEquals(InputError.NON_NEGATIVE, result.errors[FoodField.ENERGY])
        assertEquals(InputError.PRECISION, result.errors[FoodField.PROTEIN])
        assertEquals(InputError.NUMBER, result.errors[FoodField.FAT])
        assertEquals(InputError.POSITIVE, result.errors[FoodField.AMOUNT])
        assertNull(result.food)
    }
    @Test fun missingMacrosAreNotSilentlyRecordedAsZero() {
        assertEquals(InputError.REQUIRED, FoodValidator.validate(draft.copy(carbs = "")).errors[FoodField.CARBS])
    }
    @Test fun unrepresentableValuesAreRejectedWithoutOverflow() {
        assertEquals(InputError.TOO_LARGE,
            FoodValidator.validate(draft.copy(energy = "92233720368547758.08")).errors[FoodField.ENERGY])
    }
    @Test fun rejectsScientificNotationAndMixedSeparators() {
        assertEquals(InputError.NUMBER, FoodValidator.validate(draft.copy(amount = "1e3")).errors[FoodField.AMOUNT])
        assertEquals(InputError.NUMBER, FoodValidator.validate(draft.copy(amount = "1,2.3")).errors[FoodField.AMOUNT])
    }
    @Test fun zeroMacrosAreValidAndOriginalValuesCanBeRecreated() {
        val input = requireNotNull(FoodValidator.validate(draft.copy(protein = "0", fat = "0", carbs = "0")).food)
        val entry = DiaryEntry(name = input.name, per100g = input.per100g, eatenGramsHundredths = input.eatenGramsHundredths,
            dayEpoch = 1, createdAtMillis = 1)
        assertEquals(input, FoodValidator.validate(FoodDraft.from(entry)).food)
    }
}
