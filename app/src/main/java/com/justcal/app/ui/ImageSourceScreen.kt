package com.justcal.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.justcal.app.R
import com.justcal.app.camera.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSourceScreen(
    request: ScanRequest, source: ImageSource, state: ImageInputState, images: ImagePreprocessor,
    onImage: (Uri, ImageSource) -> Unit, onRetry: () -> Unit, onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var refresh by remember { mutableIntStateOf(0) }
    var cameraError by remember { mutableStateOf(false) }
    var launched by rememberSaveable { mutableStateOf(false) }
    val permissionHistory = remember { context.getSharedPreferences("camera-permission", 0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImage(uri, ImageSource.PHOTO_PICKER)
    }
    fun choose() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    fun ask() {
        permissionHistory.edit { putBoolean("requested", true) }
        permission.launch(Manifest.permission.CAMERA)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    // refresh triggers a fresh platform check, including after one-time permission revocation.
    val granted = remember(refresh) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val rationale = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } == true
    val settingsRequired = !granted && permissionHistory.getBoolean("requested", false) && !rationale
    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            if (source == ImageSource.PHOTO_PICKER) choose()
            else if (!granted && !settingsRequired && !rationale) ask()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(request.mode.label())) },
                navigationIcon = { IconButton(onClick = onBack) { CalIcon(R.drawable.ic_back, stringResource(R.string.close)) } },
                actions = {
                    TextButton(onClick = { choose() }, enabled = !state.busy) { Text(stringResource(R.string.choose_photo)) }
                })
        },
    ) { padding ->
        when {
            state.busy -> Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(stringResource(R.string.preparing_image), Modifier.padding(24.dp))
            }
            source == ImageSource.CAMERA && granted && !cameraError && !state.error -> {
                CameraCapture(images, { onImage(it, ImageSource.CAMERA) }, { cameraError = true },
                    Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()))
            }
            else -> Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.error) Text(stringResource(R.string.image_error))
                else if (cameraError) Text(stringResource(R.string.camera_error))
                else if (source == ImageSource.CAMERA && !granted) {
                    Text(stringResource(R.string.camera_permission_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(if (settingsRequired) R.string.camera_settings_message else R.string.camera_permission_message))
                }
                if (source == ImageSource.CAMERA && !granted) {
                    Button(onClick = {
                        if (settingsRequired) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)))
                        else ask()
                    }) { Text(stringResource(if (settingsRequired) R.string.open_settings else R.string.allow_camera)) }
                }
                if (source == ImageSource.CAMERA && granted && (cameraError || state.error)) TextButton(onClick = {
                    cameraError = false; refresh++; onRetry()
                }) { Text(stringResource(R.string.retry)) }
                Button(onClick = { choose() }) { Text(stringResource(R.string.choose_photo)) }
                TextButton(onClick = onBack) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}
