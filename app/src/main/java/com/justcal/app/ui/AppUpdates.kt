package com.justcal.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.justcal.app.ui.theme.LocalMotionEnabled
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justcal.app.R
import com.justcal.app.domain.UpdateError
import com.justcal.app.ui.theme.CalSpacing
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

internal val LocalUpdateViewModel = staticCompositionLocalOf<UpdateViewModel?> { null }

private data class UpdateSettingsUi(val loaded: Boolean, val automatic: Boolean, val saving: Boolean, val checking: Boolean)
private fun UpdateState.settingsUi() = UpdateSettingsUi(loaded, automatic, savingPreference, checking)

@Composable
internal fun UpdateSettings() {
    val viewModel = LocalUpdateViewModel.current ?: return
    // Only the checking indicator changes; download progress cannot recompose this group.
    val initial = remember(viewModel) { viewModel.state.value.settingsUi() }
    val state by remember(viewModel) {
        viewModel.state.map { it.settingsUi() }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = initial)
    UpdateSettingsContent(state.loaded, state.automatic, state.saving, state.checking,
        onCheck = { viewModel.check() }, onAutomatic = viewModel::setAutomatic)
}

/** Transient check feedback never inserts/removes rows in this scrollable Settings group. */
@Composable
internal fun UpdateSettingsContent(
    loaded: Boolean,
    automatic: Boolean,
    saving: Boolean,
    checking: Boolean,
    onCheck: () -> Unit,
    onAutomatic: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
        SectionTitle(stringResource(R.string.updates_title))
        CalGroup {
            val enabled = loaded && !saving
            Row(Modifier.fillMaxWidth().toggleable(automatic, enabled = enabled, role = Role.Switch,
                onValueChange = onAutomatic).padding(vertical = CalSpacing.small),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                Text(stringResource(R.string.updates_automatic), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = automatic, onCheckedChange = null, enabled = enabled)
            }
            val checkingLabel = stringResource(R.string.updates_checking)
            CalTonalButton(onClick = onCheck, enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("update_check_button").semantics {
                    if (checking) stateDescription = checkingLabel
                }) {
                UpdateCheckButtonContent(checking, stringResource(R.string.updates_check))
            }
            UpdateMockAction()
        }
    }
}

/** Symmetric slots keep the label centered and stationary when the indicator appears. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RowScope.UpdateCheckButtonContent(checking: Boolean, label: String) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (checking) {
            val color = LocalContentColor.current
            if (LocalMotionEnabled.current) LoadingIndicator(Modifier.fillMaxSize(), color = color)
            else LoadingIndicator(progress = { 0.35f }, color = color,
                modifier = Modifier.fillMaxSize().clearAndSetSemantics {
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                })
        }
    }
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(label, Modifier.weight(1f, fill = false))
    Spacer(Modifier.width(24.dp + ButtonDefaults.IconSpacing))
}

@Composable
internal fun AppUpdateHost(
    viewModel: UpdateViewModel,
    automaticPromptAllowed: Boolean,
    messages: SnackbarHostState,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val productionPackage = context.packageName == "com.justcal.app"
    val currentUri by rememberUpdatedState(state.apkUri)
    val resources by rememberUpdatedState(LocalResources.current)
    LaunchedEffect(viewModel, messages) {
        viewModel.feedback.collect { feedback ->
            val message = resources.getString(when (feedback) {
                UpdateFeedback.CurrentVersion -> R.string.updates_current
                is UpdateFeedback.Failure -> updateErrorText(feedback.error)
            })
            val icon = if (feedback == UpdateFeedback.CurrentVersion) null else R.drawable.ic_update
            this@LaunchedEffect.launch { messages.showSnackbar(CalSnackbarVisuals(message, icon)) }
        }
    }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Cancellation is a no-op: retain the verified APK and the current navigation/drafts.
    }
    fun launchInstaller() {
        val uri = currentUri ?: return
        try {
            if (!context.packageManager.canRequestPackageInstalls()) return
            installer.launch(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uri), "application/vnd.android.package-archive")
                clipData = ClipData.newRawUri("Just Cal update", Uri.parse(uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: ActivityNotFoundException) {
            viewModel.installerFailed()
        } catch (_: SecurityException) {
            viewModel.installerFailed()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) launchInstaller()
    }
    fun install() {
        try {
            if (context.packageManager.canRequestPackageInstalls()) launchInstaller()
            else permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")))
        } catch (_: ActivityNotFoundException) { viewModel.installerFailed() }
        catch (_: SecurityException) { viewModel.installerFailed() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.check(automatic = true) }
    // A background check must not cover an editor, camera or another modal flow.
    if (state.showSheet && (automaticPromptAllowed || !state.automaticPrompt)) {
        UpdateSheet(state, canInstall = productionPackage, onCheck = { viewModel.check() },
            onDismiss = viewModel::dismiss, onDownload = viewModel::download,
            onCancelDownload = viewModel::cancelDownload, onInstall = ::install,
            onReleasePage = {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.update!!.pageUrl))) }
                catch (_: ActivityNotFoundException) { viewModel.installerFailed() }
                catch (_: SecurityException) { viewModel.installerFailed() }
            })
    }
}

internal fun updateErrorText(error: UpdateError): Int = when (error) {
    UpdateError.NETWORK -> R.string.updates_network_error
    UpdateError.RATE_LIMIT -> R.string.updates_rate_limit
    UpdateError.INVALID_RELEASE -> R.string.updates_release_error
    UpdateError.INVALID_APK -> R.string.updates_apk_error
    UpdateError.STORAGE -> R.string.updates_storage_error
    UpdateError.INSTALLER -> R.string.updates_installer_error
}
