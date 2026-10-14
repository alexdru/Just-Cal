package com.justcal.app.ui

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
import androidx.compose.ui.layout.positionInRoot
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
                ToggleButton(
                    checked = selected == option,
                    onCheckedChange = { if (selected != option) { origin.position = center; onSelect(option) } },
                    enabled = enabled && optionEnabled(option),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton }
                        .onGloballyPositioned { center = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f) },
                ) {
                    Text(label(option))
                }
            }
        }
    }
}
