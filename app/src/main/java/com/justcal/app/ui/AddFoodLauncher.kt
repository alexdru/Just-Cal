package com.justcal.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.justcal.app.R
import com.justcal.app.camera.ScanMode
import com.justcal.app.camera.ScanRequest
import com.justcal.app.camera.ImageSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodLauncher(
    dayEpoch: Long, mode: ScanMode?, onDismiss: () -> Unit,
    onMode: (ScanMode?) -> Unit, onManual: () -> Unit, onSource: (ScanRequest, ImageSource) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                if (mode != null) IconButton(onClick = { onMode(null) }) {
                    CalIcon(R.drawable.ic_back, stringResource(R.string.close))
                }
                Text(stringResource(if (mode == null) R.string.add_food else mode.label()),
                    Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.headlineSmall)
            }
            Text(stringResource(R.string.scan_unavailable),
                Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (mode == null) {
                LauncherAction(R.drawable.ic_package, R.string.scan_package, R.string.scan_package_hint) { onMode(ScanMode.PACKAGE) }
                LauncherAction(R.drawable.ic_meal, R.string.scan_meal, R.string.scan_meal_hint) { onMode(ScanMode.MEAL) }
                LauncherAction(R.drawable.ic_add, R.string.add_manually, R.string.add_manually_hint, onManual)
            } else {
                LauncherAction(R.drawable.ic_camera, R.string.take_photo) { onSource(ScanRequest(mode, dayEpoch), ImageSource.CAMERA) }
                LauncherAction(R.drawable.ic_photo, R.string.choose_photo) { onSource(ScanRequest(mode, dayEpoch), ImageSource.PHOTO_PICKER) }
            }
        }
    }
}

@Composable
private fun LauncherAction(icon: Int, title: Int, subtitle: Int? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = subtitle?.let { { Text(stringResource(it)) } },
        leadingContent = { CalIcon(icon) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
}

fun ScanMode.label(): Int = when (this) {
    ScanMode.PACKAGE -> R.string.mode_package
    ScanMode.MEAL -> R.string.mode_meal
}
