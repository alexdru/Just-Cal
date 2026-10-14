package com.justcal.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.justcal.app.R
import com.justcal.app.ui.theme.CalMotion
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
enum class MainTab(val label: Int, val icon: Int, val selectedIcon: Int) : NavKey {
    HOME(R.string.home, R.drawable.ic_home, R.drawable.ic_home_filled),
    DIARY(R.string.history, R.drawable.ic_diary, R.drawable.ic_diary_filled),
    SETTINGS(R.string.settings, R.drawable.ic_settings, R.drawable.ic_settings_filled),
}

/** One fixed floating host shared by pager pages; the indicator follows the finger. */
@Composable
fun FloatingNavigation(
    selected: MainTab, onSelect: (MainTab) -> Unit, onAdd: (() -> Unit)?,
    position: () -> Float = { selected.ordinal.toFloat() },
    launcherOpen: Boolean = false,
) {
    val fabAngle by animateFloatAsState(if (launcherOpen) 45f else 0f, CalMotion.fast(), label = "add food invitation")
    val fabScale by animateFloatAsState(if (launcherOpen) 0.92f else 1f, CalMotion.fast(), label = "add food depth")
    BoxWithConstraints(
        Modifier.fillMaxWidth().navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val tabWidth = minOf(120.dp, (maxWidth - 12.dp) / MainTab.entries.size)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.CenterEnd) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = onAdd != null,
                    enter = fadeIn(CalMotion.effects()) + scaleIn(CalMotion.fast(), initialScale = 0.7f),
                    exit = fadeOut(androidx.compose.animation.core.tween(CalMotion.fabExitMillis)) +
                        scaleOut(androidx.compose.animation.core.tween(CalMotion.fabExitMillis), targetScale = 0.7f),
                ) {
                FloatingActionButton(
                    onClick = { onAdd?.invoke() }, shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(56.dp).graphicsLayer { scaleX = fabScale; scaleY = fabScale },
                ) {
                    Box(Modifier.graphicsLayer { rotationZ = fabAngle }) {
                        CalIcon(R.drawable.ic_add, stringResource(R.string.add_food))
                    }
                }
                }
            }
            Surface(shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 2.dp) {
                Box(Modifier.padding(6.dp).width(tabWidth * 3).height(IntrinsicSize.Min)) {
                    Box(Modifier.width(tabWidth).fillMaxHeight()
                        .graphicsLayer { translationX = position().coerceIn(0f, 2f) * tabWidth.toPx() }
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(24.dp)))
                    Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                        MainTab.entries.forEach { tab ->
                            val intensity = (1f - abs(position() - tab.ordinal)).coerceIn(0f, 1f)
                            val foreground = lerp(MaterialTheme.colorScheme.onSurfaceVariant,
                                MaterialTheme.colorScheme.onSecondaryContainer, intensity)
                            Column(
                                Modifier.width(tabWidth).clip(RoundedCornerShape(24.dp)).selectable(selected = selected == tab, role = Role.Tab) { onSelect(tab) }
                                    .heightIn(min = 52.dp).padding(horizontal = 4.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                CompositionLocalProvider(LocalContentColor provides foreground) {
                                    CalIcon(if (intensity > 0.5f) tab.selectedIcon else tab.icon)
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
}
