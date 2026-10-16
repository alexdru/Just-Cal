package com.justcal.app.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import com.justcal.app.ui.theme.CalMotion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Offset
import com.justcal.app.ui.theme.LocalThemeRevealOrigin
import com.justcal.app.ui.theme.LocalMotionEnabled
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.justcal.app.ui.theme.CalSpacing

/**
 * Connected single-select group, following the 1.5.0-beta01 AndroidX sample.
 * FlowRow keeps every choice visible at narrow widths and larger font sizes.
 * Selecting the current option cannot clear a required preference.
 */
@Composable
internal fun <T> SettingsChoiceGroup(
    options: List<T>,
    selected: T,
    enabled: Boolean = true,
    optionEnabled: (T) -> Boolean = { true },
    themeSelection: Boolean = false,
    interactive: Boolean = true,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val origin = LocalThemeRevealOrigin.current
    MaterialTheme(motionScheme = if (LocalMotionEnabled.current) MotionScheme.expressive() else MotionScheme.standard()) {
        FlowRow(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalArrangement = Arrangement.spacedBy(CalSpacing.small),
        ) {
            options.forEachIndexed { index, option ->
                var center by remember { mutableStateOf(Offset.Zero) }
                val interactions = remember { MutableInteractionSource() }
                val pressed by interactions.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    if (pressed && LocalMotionEnabled.current) 0.97f else 1f,
                    CalMotion.fast(),
                    label = "settings choice press",
                )
                val pressShape = ButtonGroupDefaults.connectedButtonCheckedShape
                ToggleButton(
                    checked = selected == option,
                    onCheckedChange = { if (interactive && selected != option) {
                        if (themeSelection) origin.position = center
                        onSelect(option)
                    } },
                    interactionSource = interactions,
                    enabled = enabled && optionEnabled(option),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes(pressedShape = pressShape)
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes(pressedShape = pressShape)
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes(pressedShape = pressShape)
                    },
                    modifier = Modifier.heightIn(min = 48.dp).focusProperties { canFocus = interactive }
                        .semantics { role = Role.RadioButton }
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(pressShape)
                        .onGloballyPositioned { center = it.boundsInRoot().center },
                ) {
                    Text(label(option))
                }
            }
        }
    }
}
