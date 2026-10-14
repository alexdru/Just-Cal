package com.justcal.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.graphics.createBitmap
import android.graphics.Canvas as AndroidCanvas
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.hypot
import kotlin.math.roundToInt

@Stable
class ThemeRevealOrigin {
    var position: Offset? = null
}
val LocalThemeRevealOrigin = staticCompositionLocalOf { ThemeRevealOrigin() }

private fun revealRadius(point: Offset, size: Size, fraction: Float): Float =
    hypot(maxOf(point.x, size.width - point.x), maxOf(point.y, size.height - point.y)) * fraction

/**
 * A bounded, temporary old frame is erased radially to reveal the new palette.
 * The theme commits once. Animation reads happen only in drawing; there are no
 * per-frame color allocations or duplicated screen compositions.
 */
@Composable
internal fun AnimatedBrandTheme(target: ColorScheme, splashVisible: Boolean, content: @Composable () -> Unit) {
    val enabled = rememberMotionEnabled()
    val view = LocalView.current
    val origin = remember { ThemeRevealOrigin() }
    val progress = remember { Animatable(1f) }
    var displayed by remember { mutableStateOf(target) }
    var previousSurface by remember { mutableStateOf(target.surface) }
    var center by remember { mutableStateOf<Offset?>(null) }
    var frozen by remember { mutableStateOf<ImageBitmap?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(target, enabled) {
        if (target.paletteColors() == displayed.paletteColors()) {
            if (!enabled) progress.snapTo(1f)
            else if (progress.value < 1f) progress.animateTo(1f,
                tween(CalMotion.themeRevealMillis, easing = FastOutSlowInEasing))
            frozen = null
            origin.position = null
            return@LaunchedEffect
        }
        if (!enabled || splashVisible) {
            displayed = target
            progress.snapTo(1f)
            frozen = null
        } else {
            // Capturing the currently rendered composite also replaces an unfinished
            // transition without flashing an older palette on rapid selections.
            val frame = view.boundedThemeFrame()
            previousSurface = displayed.surface
            center = origin.position
            frozen = frame
            progress.snapTo(0f)
            displayed = target
            // First draw the new palette beneath the old frame, then start motion.
            withFrameNanos { }
            withFrameNanos { }
            progress.animateTo(1f, tween(CalMotion.themeRevealMillis, easing = FastOutSlowInEasing))
            frozen = null
        }
        origin.position = null
    }

    LaunchedEffect(view, splashVisible) {
        val activity = view.context.findActivity() ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(activity.window, view)
        snapshotFlow {
            val size = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat())
            val point = center ?: Offset(size.width / 2f, size.height / 2f)
            val extent = revealRadius(point, size, progress.value)
            val insets = view.rootWindowInsets
            val status = insets?.getInsets(android.view.WindowInsets.Type.statusBars())?.top ?: 0
            val navigation = insets?.getInsets(android.view.WindowInsets.Type.navigationBars())?.bottom ?: 0
            fun lightAt(sample: Offset): Boolean {
                val background = if (progress.value < 1f && (sample - point).getDistance() > extent)
                    previousSurface else displayed.surface
                return !splashVisible && background.luminance() > 0.45f
            }
            lightAt(Offset(size.width * 0.16f, status / 2f)) to
                lightAt(Offset(size.width / 2f, size.height - navigation / 2f))
        }.distinctUntilChanged().collect { (status, navigation) ->
            controller.isAppearanceLightStatusBars = status
            controller.isAppearanceLightNavigationBars = navigation
        }
    }
    CompositionLocalProvider(
        LocalMotionEnabled provides enabled,
        LocalThemeRevealOrigin provides origin,
        androidx.compose.material3.LocalContentColor provides displayed.onSurface,
    ) {
        MaterialTheme(colorScheme = displayed, typography = CalTypography, shapes = CalShapes,
            motionScheme = if (enabled) CalMotion.expressive else CalMotion.standard) {
            Box(Modifier.fillMaxSize().onSizeChanged { canvasSize = it }.background(displayed.surface)) {
                Box(Modifier.fillMaxSize().graphicsLayer()) { content() }
                // The frame is released from composition at completion/disposal.
                // Let renderer/GC release its backing allocation, never recycle a
                // bitmap while RenderThread may still reference its display list.
                val frame = frozen
                Canvas(Modifier.matchParentSize().graphicsLayer()) {
                    if (progress.value < 1f) {
                        val point = center ?: Offset(size.width / 2f, size.height / 2f)
                        val radius = revealRadius(point, size, progress.value)
                        val hole = Path().apply { addOval(Rect(point, radius)) }
                        clipPath(hole, ClipOp.Difference) {
                            if (frame != null) drawImage(frame, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                                filterQuality = FilterQuality.Medium)
                            else drawRect(previousSurface, alpha = 1f - progress.value)
                        }
                    }
                }
            }
        }
    }
}

/** At most 0.6 MP / 2.4 MB, once per selection; no full-resolution screen capture. */
private fun View.boundedThemeFrame(): ImageBitmap? {
    if (width <= 0 || height <= 0) return null
    val scale = minOf(1f, 1080f / maxOf(width, height),
        kotlin.math.sqrt(600_000f / (width.toFloat() * height)))
    return runCatching {
        val bitmap = createBitmap((width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1))
        val canvas = AndroidCanvas(bitmap)
        canvas.scale(scale, scale)
        draw(canvas)
        bitmap.asImageBitmap()
    }.getOrNull()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findActivity()
    else -> null
}

private fun ColorScheme.paletteColors() = listOf(
    primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary,
    secondary, onSecondary, secondaryContainer, onSecondaryContainer,
    tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
    background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant,
    surfaceTint, inverseSurface, inverseOnSurface, error, onError, errorContainer, onErrorContainer,
    outline, outlineVariant, scrim, surfaceBright, surfaceDim,
    surfaceContainer, surfaceContainerHigh, surfaceContainerHighest,
    surfaceContainerLow, surfaceContainerLowest,
    primaryFixed, primaryFixedDim, onPrimaryFixed, onPrimaryFixedVariant,
    secondaryFixed, secondaryFixedDim, onSecondaryFixed, onSecondaryFixedVariant,
    tertiaryFixed, tertiaryFixedDim, onTertiaryFixed, onTertiaryFixedVariant,
)
