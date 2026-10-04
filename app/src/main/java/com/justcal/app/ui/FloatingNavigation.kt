package com.justcal.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.justcal.app.R
import com.justcal.app.ui.theme.CalMotion
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
enum class MainTab(val label: Int, val icon: Int) : NavKey {
    HOME(R.string.home, R.drawable.ic_home),
    DIARY(R.string.history, R.drawable.ic_diary),
    SETTINGS(R.string.settings, R.drawable.ic_settings),
}

/** A floating navigation surface with native selection, ripple and accessible tab semantics. */
@Composable
fun FloatingNavigation(selected: MainTab, onSelect: (MainTab) -> Unit, onAdd: (() -> Unit)?) {
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(Modifier.weight(1f), shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f), shadowElevation = 3.dp) {
                Row(Modifier.selectableGroup().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MainTab.entries.forEach { tab ->
                        val active = selected == tab
                        val background by animateColorAsState(
                            if (active) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f) else Color.Transparent,
                            animationSpec = tween(CalMotion.navigationDurationMillis), label = "navigation selection",
                        )
                        Surface(Modifier.weight(1f), shape = CircleShape, color = background) {
                            Column(
                                Modifier.selectable(selected = active, role = Role.Tab) { onSelect(tab) }
                                    .heightIn(min = 52.dp).padding(horizontal = 4.dp, vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                val color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                CompositionLocalProvider(LocalContentColor provides color) {
                                    CalIcon(tab.icon)
                                    Text(stringResource(tab.label), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
            if (onAdd != null) FloatingActionButton(onClick = onAdd, shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(64.dp)) {
                CalIcon(R.drawable.ic_add, stringResource(R.string.add_food))
            }
        }
    }
}
