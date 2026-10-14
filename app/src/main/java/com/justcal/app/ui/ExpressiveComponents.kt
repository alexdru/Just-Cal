package com.justcal.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.justcal.app.ui.theme.CalMotion
import com.justcal.app.ui.theme.LocalMotionEnabled

/** Native expressive button: shape feedback, ripple and typography remain Material-owned. */
@Composable
fun CalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
    content: @Composable RowScope.() -> Unit,
) {
    Button(onClick, shapes = ButtonDefaults.shapes(pressedShape = ButtonDefaults.mediumPressedShape),
        modifier = modifier.heightIn(min = 56.dp), enabled = enabled,
        colors = colors, contentPadding = contentPadding, content = content)
}

@Composable
fun CalTonalButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(onClick, shapes = ButtonDefaults.shapes(),
        modifier = modifier.heightIn(min = 48.dp), enabled = enabled, content = content)
}

/** Only this boundary opts into the experimental morphing loading indicator. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CalLoading(modifier: Modifier = Modifier) {
    if (LocalMotionEnabled.current) LoadingIndicator(modifier)
    else CircularProgressIndicator(progress = { 0.35f }, modifier = modifier
        .clearAndSetSemantics { progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate })
}

/** Animate presentation only; every target is already formatted from exact domain arithmetic. */
@Composable
fun AnimatedNutrition(value: String, style: TextStyle, modifier: Modifier = Modifier) =
    AnimatedNutrition(AnnotatedString(value), style, modifier)

@Composable
fun AnimatedNutrition(value: AnnotatedString, style: TextStyle, modifier: Modifier = Modifier) {
    val motion = LocalMotionEnabled.current
    AnimatedContent(
        targetState = value, contentKey = { it.text }, modifier = modifier,
        transitionSpec = {
            if (motion) (fadeIn(CalMotion.effects()) + slideInVertically(CalMotion.fast()) { it / 4 }) togetherWith
                (fadeOut(CalMotion.effects()) + slideOutVertically(CalMotion.fast()) { -it / 4 })
            else EnterTransition.None togetherWith ExitTransition.None
        },
        contentAlignment = Alignment.CenterStart,
        label = "nutrition value",
    ) { Text(it, style = style) }
}
