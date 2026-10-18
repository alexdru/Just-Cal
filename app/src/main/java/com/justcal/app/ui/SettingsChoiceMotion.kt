package com.justcal.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Shared by the real control and its draw-only old-palette copies during a reveal. */
internal class SettingsChoiceMotion(initiallyRounded: Boolean) {
    val interactions = MutableInteractionSource()
    val roundness = Animatable(if (initiallyRounded) 1f else 0f)
}

internal val LocalSettingsChoiceMotions =
    staticCompositionLocalOf<MutableMap<Pair<List<*>, Any?>, SettingsChoiceMotion>?> { null }

/** A single externally animated outline prevents ToggleButton from starting another spring. */
internal data class SettingsChoiceShape(
    val connected: Shape,
    val capsule: Shape,
    val fraction: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val start = connected.createOutline(size, layoutDirection, density)
        val end = capsule.createOutline(size, layoutDirection, density)
        return if (start is Outline.Rounded && end is Outline.Rounded) {
            Outline.Rounded(lerp(start.roundRect, end.roundRect, fraction.coerceIn(0f, 1f)))
        } else if (fraction < 0.5f) start else end
    }
}
