package com.justcal.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import com.justcal.app.ui.theme.CalMotion
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val date = LocalDate.ofEpochDay(image.request.dayEpoch).format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0]))
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(stringResource(image.request.mode.label()), style = MaterialTheme.typography.titleLarge)
                    Text(date, style = MaterialTheme.typography.bodyMedium)
                }
            }, navigationIcon = { IconButton(onClick = onBack) { CalIcon(R.drawable.ic_back, stringResource(R.string.close)) } })
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)))
                .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = CalLayout.readingWidth).fillMaxWidth()
                .padding(horizontal = CalSpacing.page, vertical = CalSpacing.related)) {
                Button(onClick = {
                    try { onRecognize() } catch (_: Exception) { failed = true }
                }, enabled = bitmap != null && !failed && !packageState.busy && (!isPackage || !packageModel.loading),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(if (packageState.failed) R.string.retry else R.string.use_photo))
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().consumeWindowInsets(padding)) {
            // Keep the portrait preview/action balance; the scroll viewport extends behind the action host.
            val halfHeight = ((maxHeight - padding.calculateTopPadding() - padding.calculateBottomPadding()) / 2)
                .coerceAtLeast(0.dp)
            val actions: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier.verticalScroll(rememberScrollState())
                    .padding(start = CalSpacing.page, end = CalSpacing.page, top = CalSpacing.page,
                        bottom = padding.calculateBottomPadding() + CalSpacing.page)
                    .animateContentSize(CalMotion.size()),
                    verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                    Text(stringResource(R.string.review_photo),
                        Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.titleMedium)
                    if (isPackage) {
                        val enabled = !packageState.busy && !packageModel.loading && modelName != null
                        Row(Modifier.fillMaxWidth().testTag("package_ai_assistance")
                            .toggleable(value = assistance, enabled = enabled, role = Role.Switch,
                                onValueChange = onPackageAssistance)
                            .padding(vertical = CalSpacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CalSpacing.micro)) {
                                Text(stringResource(R.string.ai_package_assistance), style = MaterialTheme.typography.titleMedium)
                                Text(when {
                                    packageModel.loading -> stringResource(R.string.ai_package_assistance_checking)
                                    modelName != null -> stringResource(R.string.ai_package_assistance_model, modelName)
                                    else -> stringResource(R.string.ai_package_assistance_missing)
                                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = assistance, onCheckedChange = null, enabled = enabled)
                        }
                        TextButton(onClick = onModels, enabled = !packageState.busy) { Text(stringResource(R.string.ai_models)) }
                        if (modelName != null) Text(
                            stringResource(if (assistance) R.string.ai_package_assistance_scope else R.string.ai_package_assistance_off),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AnimatedVisibility(packageState.busy, enter = CalMotion.enter(), exit = CalMotion.exit()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                            CalLoading(Modifier.size(48.dp))
                            Text(
                                if (isPackage && packageState.processingModel != null)
                                    stringResource(R.string.ai_package_model_processing, packageState.processingModel)
                                else stringResource(if (isPackage) R.string.ocr_processing else R.string.ai_processing),
                                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (packageState.failed) Text(stringResource(if (isPackage) R.string.ocr_failed else R.string.ai_runtime_error),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
                    if (failed) Text(stringResource(R.string.image_error),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
                    Text(stringResource(if (isPackage) R.string.review_package_guidance else R.string.review_meal_guidance),
                        style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(if (isPackage) R.string.review_package_ocr else R.string.ai_meal_unavailable),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onReplace, enabled = !packageState.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(if (image.source == ImageSource.CAMERA) R.string.retake else R.string.choose_another))
                    }
                    if (!isPackage) TextButton(onClick = onModels, enabled = !packageState.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.ai_models))
                    }
                    TextButton(onClick = onManual,
                        enabled = !packageState.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.ai_use_photo_manual))
                    }
                }
            }
            Column(Modifier.calContent(CalLayout.readingWidth).padding(top = padding.calculateTopPadding())) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    bitmap?.let {
                        Image(it.asImageBitmap(), stringResource(R.string.photo_description),
                            Modifier.fillMaxSize().padding(CalSpacing.small), contentScale = ContentScale.Fit)
                    }
                    if (bitmap == null && !failed) CalLoading()
                }
                actions(Modifier.fillMaxWidth().height(halfHeight + padding.calculateBottomPadding()))
            }
        }
    }
}
