package com.justcal.app.ui

import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class NutritionFormattingTest {
    @Test fun formattingFollowsTheAppLocaleAndPreservesExactRounding() {
        val value = BigDecimal("1234.25")
        assertEquals("1234.3", formatNutrition(value, 1, Locale.ENGLISH))
        assertEquals("1234,3", formatNutrition(value, 1, Locale.forLanguageTag("ru")))
        assertEquals("9007199254740993", formatNutrition(BigDecimal("9007199254740992.5"), 0, Locale.ENGLISH))
        assertEquals("0,00", formatNutrition(BigDecimal.ZERO, 2, Locale.forLanguageTag("ru")))
    }
}
