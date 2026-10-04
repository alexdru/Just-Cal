package com.justcal.app.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Format exact diary values without converting them to floating point. */
internal fun formatNutrition(value: BigDecimal, scale: Int, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        minimumFractionDigits = scale
        maximumFractionDigits = scale
        roundingMode = RoundingMode.HALF_UP
    }.format(value)
