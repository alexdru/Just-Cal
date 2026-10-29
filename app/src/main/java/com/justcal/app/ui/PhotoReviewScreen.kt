package com.justcal.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.justcal.app.ui.CalButton as Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import com.justcal.app.ui.theme.CalLayout
import com.justcal.app.ui.theme.CalSpacing
import com.justcal.app.R
import com.justcal.app.camera.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoReviewScreen(
    image: PreparedImage, images: ImagePreprocessor,
    onReplace: () -> Unit, onBack: () -> Unit,
    packageState: PackageReviewState = PackageReviewState(), onRecognize: () -> Unit = {}, onManual: () -> Unit = {},
    onModels: () -> Unit = {},
    packageModel: PackageAssistanceModel = PackageAssistanceModel(),
    packageAssistanceEnabled: Boolean = true, onPackageAssistance: (Boolean) -> Unit = {},
) {
    var bitmap by remember(image.uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(image.uri) { mutableStateOf(false) }
    LaunchedEffect(image.uri) {
        try {
            bitmap = images.decode(image, preview = true)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    DisposableEffect(image.uri) { onDispose { bitmap?.recycle() } }
    val isPackage = image.request.mode == ScanMode.PACKAGE
    val modelName = packageModel.name
    val assistance = packageAssistanceEnabled && modelName != null
    val density = LocalDensity.current
    var primaryHeight by remember { mutableIntStateOf(0) }
    val date = LocalDate.ofEpochDay(image.request.dayEpoch).format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0]))
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CalTopBar(onBack = onBack, maxWidth = CalLayout.readingWidth, title = {
                Column {
                    Text(stringResource(image.request.mode.label()), style = MaterialTheme.typography.titleLarge)
                    Text(date, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            })
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)))
                .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = CalLayout.readingWidth).fillMaxWidth()
                .padding(horizontal = CalSpacing.page, vertical = CalSpacing.related)) {
                if (packageState.busy) {
                    // Retain the action host height so the photo does not move when analysis starts.
                    Spacer(Modifier.fillMaxWidth().height(with(density) { primaryHeight.toDp() }.coerceAtLeast(56.dp)))
                } else {
                    Button(onClick = {
                        try { onRecognize() } catch (_: Exception) { failed = true }
                    }, enabled = bitmap != null && !failed && (!isPackage || !packageModel.loading),
                        modifier = Modifier.fillMaxWidth().testTag("photo_review_primary").onSizeChanged { primaryHeight = it.height }) {
                        Text(stringResource(if (packageState.failed) R.string.retry else R.string.use_photo))
                    }
                }
            }
        },
    ) { padding ->
        val overlayPx = with(density) { (padding.calculateBottomPadding() + CalSpacing.related).toPx() }
        val defaultFocusSpec = LocalBringIntoViewSpec.current
        val focusSpec = remember(defaultFocusSpec, overlayPx) {
            object : BringIntoViewSpec {
                override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                    defaultFocusSpec.calculateScrollDistance(offset, size, (containerSize - overlayPx).coerceAtLeast(0f))
            }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides focusSpec) {
            BoxWithConstraints(Modifier.fillMaxSize().consumeWindowInsets(padding)) {
                val halfHeight = ((maxHeight - padding.calculateTopPadding() - padding.calculateBottomPadding()) / 2)
                    .coerceAtLeast(0.dp)
                Column(Modifier.calContent(CalLayout.readingWidth).padding(top = padding.calculateTopPadding())) {
                    Box(Modifier.weight(1f).fillMaxWidth().testTag("review_photo_preview"), contentAlignment = Alignment.Center) {
                        bitmap?.let {
                            Image(it.asImageBitmap(), stringResource(R.string.photo_description),
                                Modifier.fillMaxSize().padding(CalSpacing.small), contentScale = ContentScale.Fit)
                        }
                        if (bitmap == null && !failed) CalLoading()
                    }
                    // Only this lower region changes state; its viewport continues behind the pinned action.
                    Box(Modifier.fillMaxWidth().height(halfHeight + padding.calculateBottomPadding())) {
                        if (packageState.busy) {
                            Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
                                contentAlignment = Alignment.Center) {
                                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                                    .padding(CalSpacing.page).testTag("photo_review_processing"),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(CalSpacing.medium)) {
                                    CalLoading(Modifier.size(64.dp))
                                    Text(stringResource(when {
                                        !isPackage -> R.string.ai_processing
                                        packageState.processingModel != null -> R.string.ai_package_model_processing
                                        else -> R.string.ocr_processing
                                    }), Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
                                        style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                                    packageState.processingModel?.let {
                                        Text(it, style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        } else {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(start = CalSpacing.page, end = CalSpacing.page, top = CalSpacing.medium,
                                    bottom = padding.calculateBottomPadding() + CalSpacing.page),
                                verticalArrangement = Arrangement.spacedBy(CalSpacing.medium)) {
                                Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                                    Text(stringResource(if (packageState.failed) R.string.review_failed else R.string.review_photo),
                                        Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
                                        style = MaterialTheme.typography.titleLarge)
                                    Text(stringResource(when {
                                        failed -> R.string.review_image_unavailable
                                        packageState.failed -> R.string.review_retry_hint
                                        isPackage -> R.string.review_package_guidance
                                        else -> R.string.review_meal_guidance
                                    }), style = MaterialTheme.typography.bodyMedium,
                                        color = if (failed || packageState.failed) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isPackage) {
                                    if (modelName != null || packageModel.loading) {
                                        Row(Modifier.fillMaxWidth().calGroupedRow(0, 1).testTag("package_ai_assistance")
                                            .toggleable(value = assistance, enabled = !packageModel.loading,
                                                role = Role.Switch, onValueChange = onPackageAssistance)
                                            .padding(CalSpacing.medium),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CalSpacing.micro)) {
                                                Text(stringResource(R.string.ai_package_assistance),
                                                    style = MaterialTheme.typography.titleMedium)
                                                Text(if (modelName != null) stringResource(R.string.ai_package_assistance_model, modelName)
                                                    else stringResource(R.string.ai_package_assistance_checking),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(checked = assistance, onCheckedChange = null, enabled = !packageModel.loading)
                                        }
                                    } else {
                                        CalSettingsRow(stringResource(R.string.ai_models), R.drawable.ic_memory,
                                            subtitle = stringResource(R.string.ai_package_assistance_missing), onClick = onModels)
                                    }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                                    TextButton(onClick = onReplace, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text(stringResource(if (image.source == ImageSource.CAMERA) R.string.retake else R.string.choose_another))
                                    }
                                    TextButton(onClick = onManual, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text(stringResource(R.string.ai_use_photo_manual))
                                    }
                                    if (!isPackage || modelName != null) {
                                        TextButton(onClick = onModels, modifier = Modifier.heightIn(min = 48.dp)) {
                                            Text(stringResource(R.string.ai_models))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
