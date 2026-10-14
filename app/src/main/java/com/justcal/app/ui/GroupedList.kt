package com.justcal.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/** A single tonal group, rather than a separate Card for every row. */
@Composable
fun CalGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Connected lazy rows retain independent stable keys and placement animations. */
@Composable
fun Modifier.calGroupedRow(index: Int, count: Int): Modifier {
    val group = MaterialTheme.shapes.extraLarge
    val square = androidx.compose.foundation.shape.CornerSize(0.dp)
    val shape = group.copy(
        topStart = if (index == 0) group.topStart else square,
        topEnd = if (index == 0) group.topEnd else square,
        bottomStart = if (index == count - 1) group.bottomStart else square,
        bottomEnd = if (index == count - 1) group.bottomEnd else square,
    )
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val divider = MaterialTheme.colorScheme.outlineVariant
    return clip(shape).background(surface).drawWithContent {
        drawContent()
        if (index < count - 1) drawLine(divider,
            Offset(16.dp.toPx(), size.height - 0.5.dp.toPx()),
            Offset(size.width - 16.dp.toPx(), size.height - 0.5.dp.toPx()), 1.dp.toPx())
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
fun CalSettingsRow(title: String, @DrawableRes icon: Int, subtitle: String? = null, onClick: () -> Unit) {
    ListItem(onClick = onClick, leadingContent = { CalLeadingIcon(icon) },
        trailingContent = { CalIcon(com.justcal.app.R.drawable.ic_chevron) },
        supportingContent = subtitle?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp),
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}
