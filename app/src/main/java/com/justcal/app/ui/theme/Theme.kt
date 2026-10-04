package com.justcal.app.ui.theme

import android.app.Activity
import android.os.Build
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
private val LightColors = lightColorScheme(
    primary = Color(0xFF365E3A), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F294), onPrimaryContainer = Color(0xFF233817),
    secondary = Color(0xFF5C674E), secondaryContainer = Color(0xFFE3E9D6),
    surface = Color(0xFFFAFAF4), onSurface = Color(0xFF1C211A),
    surfaceContainer = Color(0xFFF0F2E8), surfaceContainerLow = Color(0xFFF4F5ED),
    onSurfaceVariant = Color(0xFF525A4D), outlineVariant = Color(0xFFD4DACB),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB3D599), onPrimary = Color(0xFF20351C),
    primaryContainer = Color(0xFF374B2B), onPrimaryContainer = Color(0xFFD9F294),
    secondary = Color(0xFFC0CBAD), secondaryContainer = Color(0xFF3C4631),
    surface = Color(0xFF121610), onSurface = Color(0xFFE4E8DB),
    surfaceContainer = Color(0xFF20261B), surfaceContainerLow = Color(0xFF1B2017),
    onSurfaceVariant = Color(0xFFBFC8B5), outlineVariant = Color(0xFF424B3B),
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

/** Expressive hierarchy and shapes, with Android's dynamic palette on supported devices. */
@Composable
fun JustCalTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
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
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors, typography = CalTypography, shapes = CalShapes,
        content = content,
    )
}
