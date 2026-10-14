package com.justcal.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.justcal.app.R

/** One OFL variable font for Latin and Cyrillic. Roboto Flex has no roundness axis. */
private fun type(size: Int, line: Int, weight: Int = 400, width: Float = 100f, grade: Int = 0): TextStyle =
    TextStyle(
        fontFamily = FontFamily(Font(
            R.font.roboto_flex,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight), FontVariation.opticalSizing(size.sp),
                FontVariation.width(width), FontVariation.grade(grade),
            ),
        )),
        fontWeight = FontWeight(weight),
        fontSize = size.sp,
        lineHeight = line.sp,
    )

val CalTypography = Typography(
    displayLarge = type(76, 84, 720, 110f).copy(letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
    displayMedium = type(52, 60, 680),
    displaySmall = type(40, 48, 650),
    headlineLarge = type(34, 42, 680).copy(letterSpacing = (-0.6).sp),
    headlineMedium = type(28, 36, 650),
    headlineSmall = type(24, 32, 620),
    titleLarge = type(22, 30, 620),
    titleMedium = type(16, 24, 600),
    titleSmall = type(14, 20, 600),
    bodyLarge = type(16, 24, grade = 5),
    bodyMedium = type(14, 20, grade = 5),
    bodySmall = type(12, 18, grade = 5),
    labelLarge = type(14, 20, 600),
    labelMedium = type(12, 18, 600),
    labelSmall = type(11, 16, 600),
)
