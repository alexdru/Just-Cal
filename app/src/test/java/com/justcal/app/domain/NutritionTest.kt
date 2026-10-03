package com.justcal.app.domain

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NutritionTest {
    private val label = NutritionPer100g(20550, 1234, 567, 2001)
    private fun assertDecimal(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual))

    @Test fun hundredGramsMatchesLabel() {
        val result = NutritionCalculator.consumed(label, 10000)
        assertDecimal("205.5", result.energyKcal)
        assertDecimal("12.34", result.proteinGrams)
        assertDecimal("5.67", result.fatGrams)
        assertDecimal("20.01", result.carbsGrams)
    }
    @Test fun fractionalPortionUsesExactDecimalArithmetic() {
        val result = NutritionCalculator.consumed(label, 3750)
        assertDecimal("77.0625", result.energyKcal)
        assertDecimal("4.6275", result.proteinGrams)
        assertDecimal("2.12625", result.fatGrams)
        assertDecimal("7.50375", result.carbsGrams)
    }
    @Test fun totalsRoundOnlyAfterSummingAllEntries() {
        val entry = DiaryEntry(name = "Food", per100g = NutritionPer100g(49, 49, 0, 0),
            eatenGramsHundredths = 10000, dayEpoch = 1, createdAtMillis = 1)
        val total = NutritionCalculator.total(listOf(entry, entry))
        assertDecimal("0.98", total.energyKcal)
        assertEquals("1", total.energyKcal.display(0))
        assertEquals("1.0", total.proteinGrams.display(1))
    }
    @Test fun emptyDayIsZeroAndRemainingCanBeNegative() {
        assertDecimal("0", NutritionCalculator.total(emptyList()).energyKcal)
        assertDecimal("-25.5", NutritionCalculator.remaining(ConsumedNutrition(energyKcal = BigDecimal("2025.5")), 2000))
    }
    @Test fun multiplicationDoesNotOverflowLong() {
        val result = NutritionCalculator.consumed(NutritionPer100g(Long.MAX_VALUE, 0, 0, 0), Long.MAX_VALUE)
        assertTrue(result.energyKcal > BigDecimal.valueOf(Long.MAX_VALUE))
    }
    @Test(expected = IllegalArgumentException::class) fun zeroPortionIsRejected() {
        NutritionCalculator.consumed(label, 0)
    }
    @Test fun displayUsesHalfUp() {
        assertEquals("11", BigDecimal("10.5").display(0))
        assertEquals("1.3", BigDecimal("1.25").display(1))
    }
}
