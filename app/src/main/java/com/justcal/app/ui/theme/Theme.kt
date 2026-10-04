package com.justcal.app.ui.theme

import android.app.Activity
import android.os.Build
import com.justcal.app.domain.ColorStyle
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object CalMotion {
    const val navigationDurationMillis = 160
    fun progress() = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

object CalSpacing {
    val small = 8.dp
    val medium = 16.dp
    val page = 24.dp
    val section = 32.dp
}
// Green leaf identity: evergreen primary, lime containers and warm neutral surfaces.
internal val LightColors = lightColorScheme(
    primary = Color(0xFF244D32), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F294), onPrimaryContainer = Color(0xFF233817),
    secondary = Color(0xFF526348), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E9D6), onSecondaryContainer = Color(0xFF26341F),
    tertiary = Color(0xFF38665F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBCECE1), onTertiaryContainer = Color(0xFF123C35),
    background = Color(0xFFFAFAF4), onBackground = Color(0xFF1C211A),
    surface = Color(0xFFFAFAF4), onSurface = Color(0xFF1C211A),
    surfaceDim = Color(0xFFDADDD2), surfaceBright = Color(0xFFFAFAF4),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4F5ED),
    surfaceContainer = Color(0xFFF0F2E8), surfaceContainerHigh = Color(0xFFE8EBDD),
    surfaceContainerHighest = Color(0xFFE2E6D8), surfaceVariant = Color(0xFFE2E6D8),
    onSurfaceVariant = Color(0xFF525A4D), outline = Color(0xFF727C6B), outlineVariant = Color(0xFFD4DACB),
    inverseSurface = Color(0xFF2D3328), inverseOnSurface = Color(0xFFF0F2E8),
    inversePrimary = Color(0xFFB3D599), surfaceTint = Color(0xFF244D32),
)
internal val DarkColors = darkColorScheme(
    primary = Color(0xFFB3D599), onPrimary = Color(0xFF20351C),
    primaryContainer = Color(0xFF374B2B), onPrimaryContainer = Color(0xFFD9F294),
    secondary = Color(0xFFC0CBAD), onSecondary = Color(0xFF29351F),
    secondaryContainer = Color(0xFF3C4631), onSecondaryContainer = Color(0xFFE3E9D6),
    tertiary = Color(0xFFA1D0C5), onTertiary = Color(0xFF05382F),
    tertiaryContainer = Color(0xFF214E45), onTertiaryContainer = Color(0xFFBCECE1),
    background = Color(0xFF121610), onBackground = Color(0xFFE4E8DB),
    surface = Color(0xFF121610), onSurface = Color(0xFFE4E8DB),
    surfaceDim = Color(0xFF121610), surfaceBright = Color(0xFF373D31),
    surfaceContainerLowest = Color(0xFF0D110B), surfaceContainerLow = Color(0xFF1B2017),
    surfaceContainer = Color(0xFF20261B), surfaceContainerHigh = Color(0xFF2A3024),
    surfaceContainerHighest = Color(0xFF353B2E), surfaceVariant = Color(0xFF424B3B),
    onSurfaceVariant = Color(0xFFBFC8B5), outline = Color(0xFF8D9883), outlineVariant = Color(0xFF424B3B),
    inverseSurface = Color(0xFFE4E8DB), inverseOnSurface = Color(0xFF2D3328),
    inversePrimary = Color(0xFF244D32), surfaceTint = Color(0xFFB3D599),
)
val CalTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 72.sp, letterSpacing = (-2).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)
private val CalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp),
)

/** Branded colors by default; dynamic colors are an independent, optional preference. */
@Composable
fun JustCalTheme(darkTheme: Boolean = isSystemInDarkTheme(), colorStyle: ColorStyle = ColorStyle.JUST_CAL,
    content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val activity = context as? Activity
        activity?.let {
            WindowCompat.getInsetsController(it.window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    val colors = if (colorStyle == ColorStyle.MATERIAL_YOU && Build.VERSION.SDK_INT >= 31) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors, typography = CalTypography, shapes = CalShapes,
        content = content,
    )
}
