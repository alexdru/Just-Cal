package com.justcal.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
enum class MainTab(val label: Int, val icon: Int, val selectedIcon: Int) : NavKey {
    HOME(R.string.home, R.drawable.ic_home, R.drawable.ic_home_filled),
    DIARY(R.string.history, R.drawable.ic_diary, R.drawable.ic_diary_filled),
    SETTINGS(R.string.settings, R.drawable.ic_settings, R.drawable.ic_settings_filled),
}

/** Transparent host; its measured height reserves space for both floating controls. */
@Composable
fun FloatingNavigation(selected: MainTab, onSelect: (MainTab) -> Unit, onAdd: (() -> Unit)?) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val tabMaxWidth = minOf(120.dp, (maxWidth - 12.dp) / MainTab.entries.size)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Reserve the same action row in Settings so the pill never moves or resizes.
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.CenterEnd) {
                if (onAdd != null) FloatingActionButton(onClick = onAdd, shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(56.dp)) {
                    CalIcon(R.drawable.ic_add, stringResource(R.string.add_food))
                }
            }
            Surface(shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 2.dp) {
                Row(Modifier.selectableGroup().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MainTab.entries.forEach { tab ->
                        val active = selected == tab
                        val background by animateColorAsState(
                            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
                            animationSpec = tween(CalMotion.navigationDurationMillis), label = "navigation selection",
                        )
                        val foreground by animateColorAsState(
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(CalMotion.navigationDurationMillis), label = "navigation content",
                        )
                        Surface(shape = RoundedCornerShape(24.dp), color = background, contentColor = foreground) {
                            Column(
                                Modifier.widthIn(min = minOf(80.dp, tabMaxWidth), max = tabMaxWidth)
                                    .selectable(selected = active, role = Role.Tab) { onSelect(tab) }
                                    .heightIn(min = 52.dp).padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                CalIcon(if (active) tab.selectedIcon else tab.icon)
                                Text(stringResource(tab.label), style = MaterialTheme.typography.labelSmall,
                                    color = foreground, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}
