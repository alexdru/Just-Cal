package com.justcal.app.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Rational
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.justcal.app.ui.CalIcon
import com.justcal.app.ui.theme.CalLayout
import com.justcal.app.ui.theme.CalSpacing
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.justcal.app.R

/** Entry-owned camera with a shared 4:3 viewport for preview and still capture. */
@Composable
fun CameraCapture(
    images: ImagePreprocessor, onCaptured: (Uri) -> Unit, onError: () -> Unit,
    onGallery: () -> Unit, modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val owner = LocalLifecycleOwner.current
    val lifecycleState by owner.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val rotation = view.display?.rotation ?: android.view.Surface.ROTATION_0
    val resolution = remember { ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build() }
    val capture = remember { ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setResolutionSelector(resolution)
        .setJpegQuality(95).setTargetRotation(rotation).build() }
    var surface by remember { mutableStateOf<SurfaceRequest?>(null) }
    var ready by remember { mutableStateOf(false) }
    var takingPhoto by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var flash by rememberSaveable { mutableStateOf(false) }
    var controlsHeight by remember { mutableIntStateOf(0) }
    val latestCaptured by rememberUpdatedState(onCaptured)
    val latestError by rememberUpdatedState(onError)
    var composed by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { composed = false } }
    LaunchedEffect(flash, hasFlash) {
        capture.flashMode = if (flash && hasFlash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }
    BoxWithConstraints(modifier.background(MaterialTheme.colorScheme.surface)) {
        val landscape = maxWidth > maxHeight
        val ratio = if (landscape) 4f / 3f else 3f / 4f
        DisposableEffect(owner, resumed, landscape, rotation) {
            var active = resumed
            var provider: ProcessCameraProvider? = null
            val preview = Preview.Builder().setResolutionSelector(resolution).setTargetRotation(rotation).build()
            val executor = ContextCompat.getMainExecutor(context)
            val orientation = object : OrientationEventListener(context) {
                override fun onOrientationChanged(degrees: Int) {
                    if (degrees != ORIENTATION_UNKNOWN) {
                        val target = ImageCapture.snapToSurfaceRotation(degrees)
                        capture.targetRotation = target
                        preview.targetRotation = target
                    }
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
                            val viewport = ViewPort.Builder(
                                if (landscape) Rational(4, 3) else Rational(3, 4), rotation,
                            ).setScaleType(ViewPort.FILL_CENTER).build()
                            val group = UseCaseGroup.Builder().setViewPort(viewport)
                                .addUseCase(preview).addUseCase(capture).build()
                            val camera = provider!!.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                            hasFlash = camera.cameraInfo.hasFlashUnit()
                            capture.flashMode = if (flash && hasFlash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                            ready = true
                            if (orientation.canDetectOrientation()) orientation.enable()
                        } catch (_: Exception) { latestError() }
                    }
                }, executor)
            }
            onDispose {
                active = false
                ready = false
                hasFlash = false
                takingPhoto = false
                surface = null
                orientation.disable()
                preview.surfaceProvider = null
                provider?.unbind(preview, capture)
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = with(density) { controlsHeight.toDp() }),
            contentAlignment = Alignment.Center) {
            val width = minOf(maxWidth, maxHeight * ratio)
            Box(Modifier.width(width).aspectRatio(ratio).background(Color.Black).testTag("camera_frame")) {
                surface?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
                if (!ready) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
        }
        val captureLabel = stringResource(R.string.capture_photo)
        val capturingLabel = stringResource(R.string.capturing_photo)
        val flashLabel = stringResource(if (!hasFlash) R.string.flash_unavailable else if (flash) R.string.flash_on else R.string.flash_off)
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { controlsHeight = it.height }
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)))
            .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = CalLayout.readingWidth).fillMaxWidth()
            .padding(horizontal = CalSpacing.page, vertical = CalSpacing.related),
            horizontalArrangement = Arrangement.spacedBy(CalSpacing.related),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                FilledTonalIconToggleButton(checked = flash && hasFlash, onCheckedChange = { flash = it },
                    enabled = ready && hasFlash && !takingPhoto,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = flashLabel }) {
                    CalIcon(R.drawable.ic_flash, null)
                }
                Text(flashLabel, style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        Button(
            onClick = {
                if (!ready || takingPhoto || !resumed) return@Button
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    latestError()
                    return@Button
                }
                takingPhoto = true
                var pending: Uri? = null
                try {
                    val file = images.newCaptureFile()
                    pending = Uri.fromFile(file)
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
                    pending?.let { images.discard(it.toString()) }
                    takingPhoto = false
                    if (composed && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) latestError()
                }
            },
            enabled = ready && !takingPhoto && resumed,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.size(88.dp).background(Color.Black, CircleShape).padding(4.dp)
                .border(3.dp, Color.White, CircleShape).padding(6.dp)
                .semantics { contentDescription = captureLabel; if (takingPhoto) stateDescription = capturingLabel },
        ) {
            if (takingPhoto) CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black)
        }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                FilledTonalIconButton(onClick = onGallery, enabled = !takingPhoto,
                    modifier = Modifier.size(48.dp)) { CalIcon(R.drawable.ic_photo, stringResource(R.string.choose_photo)) }
                Text(stringResource(R.string.camera_gallery), style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
