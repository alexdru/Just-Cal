package com.justcal.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** A single tonal group, rather than a separate Card for every row. */
@Composable
fun CalGroup(
    modifier: Modifier = Modifier,
    index: Int = 0,
    count: Int = 1,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, shape = calGroupShape(index, count), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Shared connected corners for navigation rows and related form sections. */
@Composable
private fun calGroupShape(index: Int, count: Int): androidx.compose.foundation.shape.CornerBasedShape {
    val group = MaterialTheme.shapes.extraLarge
    val inner = MaterialTheme.shapes.extraSmall.topStart
    return group.copy(
        topStart = if (index == 0) group.topStart else inner,
        topEnd = if (index == 0) group.topEnd else inner,
        bottomStart = if (index == count - 1) group.bottomStart else inner,
        bottomEnd = if (index == count - 1) group.bottomEnd else inner,
    )
}

/** Connected lazy rows retain independent stable keys and placement animations. */
@Composable
fun Modifier.calGroupedRow(index: Int, count: Int): Modifier {
    val shape = calGroupShape(index, count)
    val surface = MaterialTheme.colorScheme.surfaceContainer
    return padding(bottom = if (index < count - 1) ListItemDefaults.SegmentedGap else 0.dp)
        .clip(shape).background(surface)
}

/** Labels and values keep their own columns, stacking when scaled text needs the width. */
@Composable
fun CalValueRow(label: String, value: String, stacked: Boolean = false) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val vertical = stacked || maxWidth < 300.dp * androidx.compose.ui.platform.LocalConfiguration.current.fontScale
        val labelContent: @Composable (Modifier) -> Unit = { modifier ->
            Text(label, modifier, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val valueContent: @Composable (Modifier) -> Unit = { modifier ->
            Text(value, modifier, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                textAlign = if (vertical) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End)
        }
        if (vertical) Column(verticalArrangement = Arrangement.spacedBy(com.justcal.app.ui.theme.CalSpacing.micro)) {
            labelContent(Modifier)
            valueContent(Modifier)
        } else Row(horizontalArrangement = Arrangement.spacedBy(com.justcal.app.ui.theme.CalSpacing.medium)) {
            labelContent(Modifier.weight(1f))
            valueContent(Modifier.weight(0.8f))
        }
    }
}

@Composable
fun CalLeadingIcon(@DrawableRes icon: Int) {
    Surface(Modifier.size(48.dp), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.primary) {
        Box(contentAlignment = Alignment.Center) { CalIcon(icon) }
    }
}

@Composable
fun CalSettingsRow(title: String, @DrawableRes icon: Int, subtitle: String? = null,
    index: Int = 0, count: Int = 1, enabled: Boolean = true, onClick: () -> Unit) {
    SegmentedListItem(onClick = onClick, enabled = enabled, leadingContent = { CalLeadingIcon(icon) },
        trailingContent = { CalIcon(com.justcal.app.R.drawable.ic_chevron) },
        supportingContent = subtitle?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shapes = ListItemDefaults.segmentedShapes(index, count,
            ListItemDefaults.shapes(shape = MaterialTheme.shapes.extraSmall))) {
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}
