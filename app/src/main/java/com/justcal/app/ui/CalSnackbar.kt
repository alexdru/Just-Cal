package com.justcal.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.justcal.app.R
import com.justcal.app.ui.theme.CalLayout

/** Optional semantic icon; ordinary diary confirmations retain their existing check mark. */
internal data class CalSnackbarVisuals(
    override val message: String,
    val icon: Int?,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals

/** Native host retains expressive motion, queuing, timeout and TalkBack announcements. */
@Composable
internal fun CalSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier.widthIn(max = CalLayout.readingWidth)) { CalSnackbar(it) }
}

@Composable
internal fun CalSnackbar(data: SnackbarData) {
    val colors = MaterialTheme.colorScheme
    Snackbar(
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = colors.inverseSurface,
        contentColor = colors.inverseOnSurface,
        action = data.visuals.actionLabel?.let { label -> {
            TextButton(
                onClick = data::performAction,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.inversePrimary),
            ) { Text(label) }
        } },
        dismissAction = if (data.visuals.withDismissAction) ({
            IconButton(onClick = data::dismiss) { CalIcon(R.drawable.ic_clear, stringResource(R.string.close)) }
        }) else null,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = if (data.visuals is CalSnackbarVisuals) (data.visuals as CalSnackbarVisuals).icon else R.drawable.ic_check
            icon?.let { CalIcon(it) }
            Text(data.visuals.message, modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleMedium)
        }
    }
}
