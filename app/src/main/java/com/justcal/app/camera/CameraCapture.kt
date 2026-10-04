package com.justcal.app.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.OrientationEventListener
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.justcal.app.R

/** Only preview and still capture. The entry lifecycle owns the camera, never a ViewModel. */
@Composable
fun CameraCapture(
    images: ImagePreprocessor, onCaptured: (Uri) -> Unit, onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val captureLabel = stringResource(R.string.capture_photo)
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val lifecycleState by owner.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .setJpegQuality(95).setTargetRotation(view.display?.rotation ?: android.view.Surface.ROTATION_0).build() }
    var surface by remember { mutableStateOf<SurfaceRequest?>(null) }
    var ready by remember { mutableStateOf(value = false) }
    var takingPhoto by remember { mutableStateOf(false) }
    val latestCaptured by rememberUpdatedState(onCaptured)
    val latestError by rememberUpdatedState(onError)

    // Unbind immediately on pause (including navigation transitions / system picker), and on disposal.
    DisposableEffect(owner, resumed) {
        var active = resumed
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build()
        val executor = ContextCompat.getMainExecutor(context)
        val orientation = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees != ORIENTATION_UNKNOWN) capture.targetRotation = ImageCapture.snapToSurfaceRotation(degrees)
            }
        }
        if (resumed) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (active) {
                    try {
                        check(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                        provider = future.get()
                        preview.setSurfaceProvider { request ->
                            if (active) surface = request else request.willNotProvideSurface()
                        }
                        provider!!.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                        ready = true
                        if (orientation.canDetectOrientation()) orientation.enable()
                    } catch (_: Exception) { latestError() }
                }
            }, executor)
        }
        onDispose {
            active = false
            ready = false
            takingPhoto = false
            surface = null
            orientation.disable()
            preview.surfaceProvider = null
            provider?.unbind(preview, capture)
        }
    }

    var composed by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { composed = false } }
    Box(modifier.background(Color.Black)) {
        surface?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
        // Match the system icon appearance chosen by JustCalTheme, even over a dark viewfinder.
        val barColor = MaterialTheme.colorScheme.surface
        Spacer(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.navigationBars).background(barColor))
        Spacer(Modifier.align(Alignment.CenterStart).fillMaxHeight()
            .windowInsetsStartWidth(WindowInsets.navigationBars).background(barColor))
        Spacer(Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            .windowInsetsEndWidth(WindowInsets.navigationBars).background(barColor))
        Button(
            onClick = {
                if (!ready || takingPhoto || !resumed) return@Button
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    latestError()
                    return@Button
                }
                takingPhoto = true
                val file = images.newCaptureFile()
                try {
                    capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                takingPhoto = false
                                if (composed && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                    latestCaptured(Uri.fromFile(file))
                                } else images.discard(Uri.fromFile(file).toString())
                            }
                            override fun onError(exception: ImageCaptureException) {
                                images.discard(Uri.fromFile(file).toString())
                                takingPhoto = false
                                if (composed) latestError()
                            }
                        })
                } catch (_: Exception) {
                    images.discard(Uri.fromFile(file).toString())
                    takingPhoto = false
                    latestError()
                }
            },
            enabled = ready && !takingPhoto && resumed,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp)
                .size(80.dp).border(3.dp, Color.White, CircleShape).padding(6.dp)
                .semantics { contentDescription = captureLabel },
        ) {
            if (takingPhoto) CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black)
        }
        if (!ready) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
    }
}
