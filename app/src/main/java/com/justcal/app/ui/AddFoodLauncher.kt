package com.justcal.app.ui

import androidx.compose.animation.*
import com.justcal.app.ui.theme.CalMotion
import com.justcal.app.ui.theme.CalLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetMaxWidth = CalLayout.readingWidth, sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            val headerAction = if (mode != null) {
                Modifier.clip(MaterialTheme.shapes.large).clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.close),
                ) { onMode(null) }
            } else Modifier
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth().then(headerAction).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (mode != null) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CalIcon(R.drawable.ic_back, null)
                }
                Text(
                    stringResource(mode?.label() ?: R.string.add_food),
                    Modifier.padding(vertical = 10.dp),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            AnimatedContent(mode, transitionSpec = { CalMotion.destination(targetState != null) }, label = "food acquisition choices") { currentMode ->
            Column {
            if (currentMode == null) {
                LauncherAction(R.drawable.ic_package, R.string.scan_package) { onMode(ScanMode.PACKAGE) }
                LauncherAction(R.drawable.ic_meal, R.string.scan_meal) { onMode(ScanMode.MEAL) }
                LauncherAction(R.drawable.ic_edit, R.string.add_manually, onManual)
            } else {
                LauncherAction(R.drawable.ic_camera, R.string.take_photo) { onSource(ScanRequest(currentMode, dayEpoch), ImageSource.CAMERA) }
                LauncherAction(R.drawable.ic_photo, R.string.choose_photo) { onSource(ScanRequest(currentMode, dayEpoch), ImageSource.PHOTO_PICKER) }
            }
            }
            }
        }
    }
}

@Composable
private fun LauncherAction(icon: Int, title: Int, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        leadingContent = { CalLeadingIcon(icon) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp),
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) { Text(stringResource(title), style = MaterialTheme.typography.titleMedium) }
}

fun ScanMode.label(): Int = when (this) {
    ScanMode.PACKAGE -> R.string.mode_package
    ScanMode.MEAL -> R.string.mode_meal
}
