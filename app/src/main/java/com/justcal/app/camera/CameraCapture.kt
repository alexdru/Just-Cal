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
import java.util.concurrent.TimeUnit
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
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
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusStatus by remember { mutableStateOf<Int?>(null) }
    var focusRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusRequest, focusStatus) {
        if (focusPoint != null) {
            delay(if (focusStatus == R.string.camera_focusing) 5.seconds else 2.seconds)
            focusPoint = null
            focusStatus = null
        }
    }
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
                            val cameraProvider = future.get()
                            provider = cameraProvider
                            preview.setSurfaceProvider { request ->
                                if (active) surface = request else request.willNotProvideSurface()
                            }
                            val viewport = ViewPort.Builder(
                                if (landscape) Rational(4, 3) else Rational(3, 4), rotation,
                            ).setScaleType(ViewPort.FILL_CENTER).build()
                            val group = UseCaseGroup.Builder().setViewPort(viewport)
                                .addUseCase(preview).addUseCase(capture).build()
                            val camera = cameraProvider.bindToLifecycle(owner, mainCameraSelector(context, cameraProvider), group)
                            boundCamera = camera
                            // Binding uses CameraX continuous AF; cancelling restores it after a tap.
                            camera.cameraControl.cancelFocusAndMetering()
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
                focusRequest++
                focusPoint = null
                focusStatus = null
                boundCamera?.cameraControl?.cancelFocusAndMetering()
                boundCamera = null
                surface = null
                orientation.disable()
                preview.surfaceProvider = null
                provider?.unbind(preview, capture)
            }
        }
        val navigationHeight = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
        val minimumControls = maxOf(with(density) { controlsHeight.toDp() }, 136.dp + navigationHeight)
        val frameWidth = minOf(maxWidth, (maxHeight - minimumControls).coerceAtLeast(0.dp) * ratio)
        val frameHeight = frameWidth / ratio
        Box(Modifier.align(Alignment.TopCenter).width(frameWidth).height(frameHeight)
            .background(Color.Black).testTag("camera_frame")) {
            surface?.let { request ->
                val transformer = remember(request) { MutableCoordinateTransformer() }
                var viewfinderSize by remember(request) { mutableStateOf(IntSize.Zero) }
                val focusLabel = stringResource(R.string.camera_focus_here)
                fun focusAt(offset: Offset): Boolean {
                    val camera = boundCamera ?: return false
                    if (!ready || takingPhoto || !resumed) return false
                    val coordinates = with(transformer) { offset.transform() }
                    if (!coordinates.x.isFinite() || !coordinates.y.isFinite()) return false
                    val point = SurfaceOrientedMeteringPointFactory(
                        request.resolution.width.toFloat(), request.resolution.height.toFloat(),
                    ).createPoint(coordinates.x, coordinates.y)
                    val action = FocusMeteringAction.Builder(
                        point,
                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                    )
                        .setAutoCancelDuration(5, TimeUnit.SECONDS).build()
                    if (!camera.cameraInfo.isFocusMeteringSupported(action)) return false
                    focusPoint = offset
                    focusStatus = R.string.camera_focusing
                    val id = ++focusRequest
                    try {
                        val future = camera.cameraControl.startFocusAndMetering(action)
                        future.addListener({
                            if (composed && (boundCamera === camera) && id == focusRequest && focusPoint != null) {
                                focusStatus = try {
                                    if (future.get().isFocusSuccessful) R.string.camera_focused
                                    else R.string.camera_focus_unconfirmed
                                } catch (_: Exception) { R.string.camera_focus_unconfirmed }
                            }
                        }, ContextCompat.getMainExecutor(context))
                    } catch (_: Exception) { focusStatus = R.string.camera_focus_unconfirmed }
                    return true
                }
                CameraXViewfinder(surfaceRequest = request, coordinateTransformer = transformer,
                    modifier = Modifier.fillMaxSize().onSizeChanged { viewfinderSize = it }
                        .pointerInput(request, boundCamera, takingPhoto, resumed) {
                            detectTapGestures { focusAt(it) }
                        }.semantics {
                            contentDescription = focusLabel
                            onClick(label = focusLabel) {
                                focusAt(Offset(viewfinderSize.width / 2f, viewfinderSize.height / 2f))
                            }
                        })
            }
            focusPoint?.let { point ->
                val radius = with(density) { 32.dp.toPx() }
                val description = stringResource(focusStatus ?: R.string.camera_focusing)
                Canvas(Modifier.offset { IntOffset((point.x - radius).roundToInt(), (point.y - radius).roundToInt()) }
                    .size(64.dp).testTag("camera_focus_ring").semantics {
                        contentDescription = description
                        liveRegion = LiveRegionMode.Polite
                    }) {
                    drawCircle(Color.Black, radius = (size.minDimension / 2) - 3.dp.toPx(), style = Stroke(4.dp.toPx()))
                    drawCircle(Color.White, radius = (size.minDimension / 2) - 3.dp.toPx(), style = Stroke(2.dp.toPx()))
                }
            }
            if (!ready) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
        val captureLabel = stringResource(R.string.capture_photo)
        val capturingLabel = stringResource(R.string.capturing_photo)
        val flashLabel = stringResource(if (!hasFlash) R.string.flash_unavailable else if (flash) R.string.flash_on else R.string.flash_off)
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(maxHeight - frameHeight)
            .testTag("camera_controls")
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))),
            contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = CalLayout.readingWidth).fillMaxWidth()
                .onSizeChanged { controlsHeight = it.height + with(density) { navigationHeight.roundToPx() } }
                .padding(horizontal = CalSpacing.page, vertical = CalSpacing.page),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CalSpacing.related),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        FilledTonalIconToggleButton(checked = flash && hasFlash, onCheckedChange = { flash = it },
                            enabled = ready && hasFlash && !takingPhoto,
                            modifier = Modifier.size(48.dp).semantics { contentDescription = flashLabel }) {
                            CalIcon(R.drawable.ic_flash, null)
                        }
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
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        FilledTonalIconButton(onClick = onGallery, enabled = !takingPhoto,
                            modifier = Modifier.size(48.dp)) { CalIcon(R.drawable.ic_photo, stringResource(R.string.choose_photo)) }
                    }
                }
            }
        }
    }
}
