package com.justcal.app.ui

import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.focus.focusProperties
import com.justcal.app.ui.theme.CalMotion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
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
    val sharedMotions = LocalSettingsChoiceMotions.current
    val motionEnabled = LocalMotionEnabled.current
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalArrangement = Arrangement.spacedBy(CalSpacing.small),
    ) {
        options.forEachIndexed { index, option ->
            var center by remember { mutableStateOf(Offset.Zero) }
            val motion = remember(options, option, sharedMotions) {
                sharedMotions?.getOrPut(options to option) { SettingsChoiceMotion(selected == option) }
                    ?: SettingsChoiceMotion(selected == option)
            }
            val pressed by motion.interactions.collectIsPressedAsState()
            val rounded = selected == option || pressed
            if (interactive) {
                LaunchedEffect(motion, rounded, motionEnabled) {
                    val target = if (rounded) 1f else 0f
                    if (motionEnabled) motion.roundness.animateTo(target, CalMotion.choiceShape())
                    else motion.roundness.snapTo(target)
                }
            }
            val connected = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes().shape
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes().shape
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes().shape
            }
            val shape = SettingsChoiceShape(connected, ButtonGroupDefaults.connectedButtonCheckedShape,
                motion.roundness.value)
            ToggleButton(
                checked = selected == option,
                onCheckedChange = { if (interactive && selected != option) {
                    if (themeSelection) origin.position = center
                    onSelect(option)
                } },
                interactionSource = motion.interactions,
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                enabled = enabled && optionEnabled(option),
                shapes = ToggleButtonShapes(shape = shape, pressedShape = shape, checkedShape = shape),
                modifier = Modifier.heightIn(min = 48.dp).focusProperties { canFocus = interactive }
                    .semantics { role = Role.RadioButton }
                    .onGloballyPositioned { center = it.boundsInRoot().center },
            ) {
                Text(label(option))
            }
        }
    }
}
