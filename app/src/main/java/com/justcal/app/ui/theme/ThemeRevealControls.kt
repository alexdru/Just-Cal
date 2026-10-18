package com.justcal.app.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import com.justcal.app.ui.LocalSettingsChoiceMotions
import com.justcal.app.ui.SettingsChoiceMotion
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.flow.first

/**
 * Only the small appearance panel is recorded in previous palettes. Its checked state stays
 * current; the root draws these layers outside the very same circle as the rest of the screen.
 * Old copies are draw-only, hidden from semantics and beneath the real hit/focus targets.
 */
@Composable
internal fun ThemeRevealControls(content: @Composable (snapshot: Boolean) -> Unit) {
    val origin = LocalThemeRevealOrigin.current
    val choices = remember { mutableMapOf<Pair<List<*>, Any?>, SettingsChoiceMotion>() }
    val edgeGutter = with(LocalDensity.current) { 2.dp.toPx() }
    SideEffect {
        origin.awaitControlMotion = {
            snapshotFlow { choices.values.none { it.roundness.isRunning } }.first { it }
        }
    }
    DisposableEffect(origin) {
        onDispose {
            origin.controlsArea = Path()
            origin.awaitControlMotion = null
        }
    }
    CompositionLocalProvider(LocalSettingsChoiceMotions provides choices) {
        Box(Modifier.onGloballyPositioned { coordinates ->
            origin.controlsPosition = coordinates.positionInRoot()
            // Include a small parent-surface gutter around the native panel contour.
            // Clamp to the scroll viewport so replay never paints over adjacent chrome.
            val bounds = coordinates.boundsInRoot()
            val viewport = coordinates.parentLayoutCoordinates?.boundsInRoot() ?: bounds
            origin.controlsArea = Path().apply { addRect(bounds.inflate(edgeGutter).intersect(viewport)) }
        }) {
            origin.regions.forEach { region ->
                key(region.key) {
                    val layer = rememberGraphicsLayer()
                    SideEffect { origin.controlLayers[region.key] = layer }
                    DisposableEffect(origin, layer) {
                        onDispose {
                            if (origin.controlLayers[region.key] === layer) origin.controlLayers.remove(region.key)
                        }
                    }
                    MaterialTheme(colorScheme = region.colors) {
                        Box(Modifier.clearAndSetSemantics {}.drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                        }) { content(true) }
                    }
                }
            }
            content(false)
        }
    }
}
