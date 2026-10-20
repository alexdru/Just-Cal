package com.justcal.app.ui

import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.justcal.app.R
import com.justcal.app.domain.AppUpdate
import com.justcal.app.domain.ReleaseVersion
import com.justcal.app.domain.UpdateError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private enum class MockStage { CHECKING, AVAILABLE, CURRENT, DOWNLOADING, READY, ERROR }

/** Debug fixtures open the production sheet over Settings, with no Activity or network access. */
@Composable
internal fun UpdateMockAction() {
    val release = AppUpdate(ReleaseVersion(99, 0, 0), stringResource(R.string.update_mock_notes), "", "",
        85_551_550, "")
    var state by remember { mutableStateOf(UpdateState(loaded = true)) }
    var stage by remember { mutableStateOf(MockStage.AVAILABLE) }
    val scope = rememberCoroutineScope()
    val viewModel = LocalUpdateViewModel.current
    var operation by remember { mutableStateOf<Job?>(null) }
    fun showStage(value: MockStage) {
        operation?.cancel()
        stage = value
        if (value == MockStage.CURRENT) {
            state = state.copy(showSheet = false)
            viewModel?.notifyCurrentVersion()
            return
        }
        state = UpdateState(loaded = true, showSheet = value != MockStage.CHECKING, checked = value != MockStage.CHECKING && value != MockStage.ERROR,
            checking = value == MockStage.CHECKING,
            update = if (value == MockStage.ERROR) null else release,
            downloading = value == MockStage.DOWNLOADING, progress = 0.46f,
            apkUri = if (value == MockStage.READY) "content://mock/verified.apk" else null,
            error = if (value == MockStage.ERROR) UpdateError.NETWORK else null)
    }
    fun check() {
        showStage(MockStage.CHECKING)
        operation = scope.launch {
            delay(1500.milliseconds)
            showStage(MockStage.AVAILABLE)
        }
    }
    TextButton(onClick = ::check) {
        UpdateCheckButtonContent(state.checking, stringResource(R.string.updates_mock))
    }
    if (state.showSheet) {
        UpdateSheet(state, canInstall = true,
            onDismiss = { operation?.cancel(); state = state.copy(showSheet = false) },
            onCheck = ::check,
            onDownload = {
                showStage(MockStage.DOWNLOADING)
                operation = scope.launch {
                    repeat(20) {
                        delay(100.milliseconds)
                        state = state.copy(progress = (it + 1) / 20f)
                    }
                    showStage(MockStage.READY)
                }
            },
            onCancelDownload = { showStage(MockStage.AVAILABLE) },
            onInstall = { state = state.copy(showSheet = false) },
            onReleasePage = {},
            mockControls = {
                SettingsChoiceGroup(options = MockStage.entries, selected = stage, label = {
                    stringResource(when (it) {
                        MockStage.CHECKING -> R.string.updates_checking
                        MockStage.AVAILABLE -> R.string.update_mock_available
                        MockStage.CURRENT -> R.string.update_mock_current
                        MockStage.DOWNLOADING -> R.string.update_mock_downloading
                        MockStage.READY -> R.string.update_mock_ready
                        MockStage.ERROR -> R.string.update_mock_error
                    })
                }, onSelect = { if (it == MockStage.CHECKING) check() else showStage(it) })
            })
    }
}
