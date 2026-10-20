package com.justcal.app.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.justcal.app.BuildConfig
import com.justcal.app.R
import com.justcal.app.ui.theme.CalLayout
import com.justcal.app.ui.theme.CalSpacing
import java.text.NumberFormat

/** One native bottom sheet for checking, results, release details and APK progress. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdateSheet(
    state: UpdateState,
    canInstall: Boolean,
    onDismiss: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onReleasePage: () -> Unit,
    mockControls: (@Composable () -> Unit)? = null,
) {
    val release = state.update.takeIf { state.checked && !state.checking }
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        sheetMaxWidth = CalLayout.readingWidth,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = CalSpacing.page).padding(bottom = CalSpacing.page),
            verticalArrangement = Arrangement.spacedBy(CalSpacing.medium)) {
            Text(when {
                state.checking -> stringResource(R.string.updates_checking)
                release != null && state.apkUri != null -> stringResource(R.string.updates_ready)
                release != null -> stringResource(R.string.updates_available, release.version.toString())
                state.error != null -> stringResource(R.string.updates_check_failed)
                else -> stringResource(R.string.updates_current)
            }, Modifier.fillMaxWidth().semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                mockControls?.invoke()
                Text(stringResource(R.string.updates_installed_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.error?.let { Text(stringResource(updateErrorText(it)), color = MaterialTheme.colorScheme.error) }
                if (release != null) {
                    Text(stringResource(R.string.updates_apk_size, Formatter.formatFileSize(context, release.sizeBytes)),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (release.notes.isNotBlank() && state.apkUri == null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(stringResource(R.string.updates_notes), Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium)
                        Text(release.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = onReleasePage) { Text(stringResource(R.string.updates_release_page)) }
                    if (!canInstall) Text(stringResource(R.string.updates_debug), style = MaterialTheme.typography.bodyMedium)
                    if (state.apkUri != null) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = MaterialTheme.shapes.large) {
                            Column(Modifier.fillMaxWidth().padding(CalSpacing.medium),
                                verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                                    CalIcon(R.drawable.ic_attention)
                                    Text(stringResource(R.string.updates_install_help_title),
                                        Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                                }
                                Text(stringResource(R.string.updates_install_help), style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.updates_protect_help), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            // Actions remain visible when release notes scroll.
            if (state.downloading) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                    Text(stringResource(R.string.updates_progress, NumberFormat.getPercentInstance().format(state.progress)),
                        style = MaterialTheme.typography.bodyMedium)
                    CalProgress(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
                CalTonalButton(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.updates_cancel_download))
                }
            } else if (!state.checking) {
                if (release != null) {
                    CalButton(onClick = { if (state.apkUri != null) onInstall() else onDownload() },
                        enabled = canInstall, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (state.apkUri != null) R.string.updates_install else R.string.updates_download))
                    }
                } else if (state.error != null) {
                    CalTonalButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}
