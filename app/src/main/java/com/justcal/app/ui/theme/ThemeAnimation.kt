package com.justcal.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.hypot

@Stable
class ThemeRevealOrigin {
    var position: Offset? = null
    internal var controlsArea by mutableStateOf(Path())
    internal var controlsPosition = Offset.Zero
    internal var regions by mutableStateOf<List<ThemePaletteRegion>>(emptyList())
    internal val controlLayers = mutableMapOf<List<Color>, GraphicsLayer>()
}

internal class ThemePaletteRegion(val colors: ColorScheme, val area: Path) {
    val key = colors.paletteColors()
}

val LocalThemeRevealOrigin = staticCompositionLocalOf { ThemeRevealOrigin() }

/** Freeze only color coverage; control selections remain current in each retained palette. */
private fun ThemeRevealOrigin.freezeRegions(colors: ColorScheme, size: Size,
    point: Offset, fraction: Float): List<ThemePaletteRegion> {
    val full = Path().apply { addRect(Rect(Offset.Zero, size)) }
    if (fraction >= 1f || regions.isEmpty()) return listOf(ThemePaletteRegion(colors, full))
    if (fraction <= 0f) return regions
    val circle = Path().apply { addOval(Rect(point, revealRadius(point, size, fraction))) }
    val covered = Path.combine(PathOperation.Intersect, full, circle)
    val remaining = regions.map {
        ThemePaletteRegion(it.colors, Path.combine(PathOperation.Difference, it.area, covered))
    }.toMutableList()
    val key = colors.paletteColors()
    val existing = remaining.indexOfFirst { it.key == key }
    if (existing < 0) remaining += ThemePaletteRegion(colors, covered)
    else remaining[existing] = ThemePaletteRegion(colors,
        Path.combine(PathOperation.Union, remaining[existing].area, covered))
    return remaining.filterNot { it.area.getBounds().isEmpty }
}

private fun revealRadius(point: Offset, size: Size, fraction: Float): Float =
    hypot(maxOf(point.x, size.width - point.x), maxOf(point.y, size.height - point.y)) * fraction

/**
 * A native-resolution layer snapshot preserves the old palette outside the reveal circle.
 * The theme commits once. Animation reads happen only in drawing; there are no
 * per-frame color allocations or duplicated screen compositions.
 */
@Composable
internal fun AnimatedBrandTheme(
    target: ColorScheme,
    splashVisible: Boolean,
    selectionKey: Any? = null,
    content: @Composable () -> Unit,
) {
    val enabled = rememberMotionEnabled()
    val view = LocalView.current
    val origin = remember { ThemeRevealOrigin() }
    val screenLayer = rememberGraphicsLayer()
    val progress = remember { Animatable(1f) }
    var displayed by remember { mutableStateOf(target) }
    var previousSurface by remember { mutableStateOf(target.surface) }
    var center by remember { mutableStateOf<Offset?>(null) }
    var frozen by remember { mutableStateOf<ImageBitmap?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(target, enabled, selectionKey) {
        if (target.paletteColors() == displayed.paletteColors()) {
            if (!enabled) progress.snapTo(1f)
            else if (progress.value < 1f) progress.animateTo(1f,
                tween(CalMotion.themeRevealMillis, easing = FastOutSlowInEasing))
            frozen = null
            origin.regions = emptyList()
            origin.position = null
            return@LaunchedEffect
        }
        if (!enabled || splashVisible) {
            displayed = target
            progress.snapTo(1f)
            frozen = null
            origin.regions = emptyList()
        } else {
            // Capturing the currently rendered composite also replaces an unfinished
            // transition without flashing an older palette on rapid selections.
            // Keep interrupted color coverage for the small live control layers as well.
            val size = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat())
            val oldRegions = origin.freezeRegions(displayed, size,
                center ?: Offset(size.width / 2f, size.height / 2f), progress.value)
            val frame = screenLayer.nativeThemeFrame()
            origin.regions = oldRegions
            // Record the old-palette controls with the new checked state before
            // installing any frozen overlay. Selection feedback stays visible meanwhile.
            withFrameNanos { }
            withFrameNanos { }
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
            origin.regions = emptyList()
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
            Box(Modifier.fillMaxSize().onSizeChanged { canvasSize = it }.drawWithContent {
                screenLayer.record { this@drawWithContent.drawContent() }
                drawLayer(screenLayer)
            }.background(displayed.surface)) {
                Box(Modifier.fillMaxSize().graphicsLayer()) { content() }
                // The temporary native-resolution frame is released at completion/disposal.
                // Never recycle while RenderThread can still reference its display list.
                val frame = frozen
                Canvas(Modifier.matchParentSize().graphicsLayer()) {
                    if (progress.value < 1f) {
                        val point = center ?: Offset(size.width / 2f, size.height / 2f)
                        val radius = revealRadius(point, size, progress.value)
                        val hole = Path().apply { addOval(Rect(point, radius)) }
                        clipPath(hole, ClipOp.Difference) {
                            val regions = origin.regions
                            fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOldFrame() {
                                if (frame != null) drawImage(frame, filterQuality = FilterQuality.None)
                                else drawRect(previousSurface, alpha = 1f - progress.value)
                            }
                            drawOldFrame()
                            if (regions.isNotEmpty()) {
                                // Replay an opaque rectangle over the old frame. Only the
                                // native panel draws its rounded contour; complementary
                                // antialiased cutouts would expose a transient outline.
                                clipPath(origin.controlsArea) {
                                    regions.forEach { region ->
                                        val layer = origin.controlLayers[region.key]
                                        if (layer == null || layer.isReleased || layer.size == IntSize.Zero) return@forEach
                                        clipPath(region.area) {
                                            // Draw the native rounded contour once over
                                            // an opaque parent surface, without cutout seams.
                                            drawRect(region.colors.surface)
                                            translate(origin.controlsPosition.x, origin.controlsPosition.y) { drawLayer(layer) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Native GPU layer capture: no software View.draw, downsampling or image scaling. */
private suspend fun GraphicsLayer.nativeThemeFrame(): ImageBitmap? {
    // Keep one transient frame within 24 MiB; oversized windows use the surface fade.
    if (isReleased || size == IntSize.Zero || size.width.toLong() * size.height * 4 > 24L * 1024 * 1024) return null
    return try {
        toImageBitmap()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
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
