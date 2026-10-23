package com.justcal.app.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Product motion roles. Native animations also honor the platform duration scale. */
object CalMotion {
    const val navigationDurationMillis = 160 // Legacy Lab compatibility only.
    const val themeRevealMillis = 600
    const val fabExitMillis = 90
    const val choiceShapeMillis = 120
    const val debounceMillis = 550L
    const val splashExitMillis = 80L
    val expressive = MotionScheme.expressive()
    val standard = MotionScheme.standard()
    fun <T> tab(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 500f)
    fun progress() = standard.defaultEffectsSpec<Float>()
    fun choiceShape(): FiniteAnimationSpec<Float> = tween(choiceShapeMillis, easing = FastOutSlowInEasing)
    fun <T> spatial(): FiniteAnimationSpec<T> = expressive.defaultSpatialSpec()
    fun <T> fast(): FiniteAnimationSpec<T> = expressive.fastSpatialSpec()
    fun <T> effects(): FiniteAnimationSpec<T> = standard.fastEffectsSpec()
    fun size(): FiniteAnimationSpec<IntSize> = standard.defaultSpatialSpec()
    fun destination(forward: Boolean): ContentTransform {
        val sign = if (forward) 1 else -1
        return (slideInHorizontally(spatial<IntOffset>()) { sign * it / 5 } + fadeIn(effects())) togetherWith
            (slideOutHorizontally(spatial<IntOffset>()) { -sign * it / 12 } + fadeOut(effects()))
    }
    // Move the fixed floating host as a layer; never resize the screen during navigation.
    fun navigationEnter() = fadeIn(effects()) + slideInVertically(spatial<IntOffset>()) { it }
    fun navigationExit() = fadeOut(effects()) + slideOutVertically(spatial<IntOffset>()) { it }
    fun enter() = fadeIn(effects()) + expandVertically(size())
    fun exit() = fadeOut(effects()) + shrinkVertically(size())
}

val LocalMotionEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberMotionEnabled(): Boolean {
    var enabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { enabled = ValueAnimator.areAnimatorsEnabled() }
    return enabled
}
