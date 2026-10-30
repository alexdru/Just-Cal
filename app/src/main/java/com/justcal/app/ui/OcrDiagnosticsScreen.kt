package com.justcal.app.ui

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import com.justcal.app.ui.theme.CalMotion
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.findViewTreeNavigationEventDispatcherOwner
import com.justcal.app.R
import com.justcal.app.diagnostics.DiagnosticScan
import com.justcal.app.ui.theme.CalLayout
import com.justcal.app.ui.theme.CalSpacing
import java.io.File
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import com.justcal.app.diagnostics.DiagnosticStatistics

@Composable
fun OcrDiagnosticsScreen(viewModel: OcrDiagnosticsViewModel, onBack: () -> Unit) {
    val scans by viewModel.scans.collectAsStateWithLifecycle(initialValue = emptyList())
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    OcrDiagnosticsContent(scans, busy, error, saved, report, onBack,
        viewModel::clear, viewModel::setBrand, viewModel::correctNetWeight, viewModel::export,
        viewModel::saveReport, viewModel::reportHandled, viewModel::reportFailed)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun OcrDiagnosticsContent(
    scans: List<DiagnosticScan>, busy: Boolean, error: Boolean,
    saved: Boolean, report: File?, onBack: () -> Unit,
    onClear: () -> Unit, onBrand: (String, String) -> Unit,
    onNetWeight: (String, String) -> Unit,
    onExport: (Set<String>?, Boolean, List<android.net.Uri>, Boolean) -> Unit, onSave: (File, android.net.Uri) -> Unit,
    onReportHandled: () -> Unit, onReportFailed: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val pager = rememberPagerState { 5 }
    val coroutineScope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var slowest by rememberSaveable { mutableStateOf(false) }
    var failures by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    var clear by rememberSaveable { mutableStateOf(false) }
    var exportMode by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingMode by rememberSaveable { mutableStateOf<String?>(null) }
    var photos by remember { mutableStateOf(emptyList<android.net.Uri>()) }
    var attachments by rememberSaveable { mutableStateOf(false) }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { photos = it }
    LaunchedEffect(scans) { selected = selected.intersect(scans.map { it.id }.toSet()) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && report != null) onSave(report, uri) else onReportHandled()
    }
    val shareTitle = stringResource(R.string.diag_share)
    LaunchedEffect(report) {
        report?.let { file ->
            try {
            if (pendingMode == "share") {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".diagnostics.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("Diagnostic report", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, shareTitle))
                onReportHandled()
            } else saveLauncher.launch("just-cal-ocr-diagnostics.zip")
            } catch (_: Exception) { onReportFailed() }
        }
    }
    fun filtered(tab: Int) = scans.let { scans ->
        scans.filter { scan ->
            (!failures || scan.status == "FAILURE" || scan.status == "CANCELLED" || scan.status == "INTERRUPTED") &&
                (tab != 3 || scan.errors.isNotEmpty() || scan.status != "SUCCESS") &&
                (tab != 2 || scan.status !in listOf("RUNNING", "INTERRUPTED")) &&
                (query.isBlank() || listOfNotNull(scan.productName, scan.brand).plus(scan.errors)
                    .any { it.contains(query, ignoreCase = true) })
        }.let { if (slowest || tab == 2) it.sortedByDescending { s -> s.totalMs } else it.sortedByDescending { s -> s.timestampMillis } }
    }
    var tabHostHeight by remember { mutableIntStateOf(0) }
    val direction = LocalLayoutDirection.current
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints {
        val compactIme = imeBottom > 0.dp && maxHeight - imeBottom < 280.dp
        Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = { CalTopBar({ Text(stringResource(R.string.ocr_diagnostics)) }, onBack, CalLayout.readingWidth) },
            bottomBar = {
                if (!compactIme) DiagnosticReportActions(selected.size, busy, error, saved,
                    onSave = { photos = emptyList(); attachments = false; exportMode = "save" },
                    onShare = { photos = emptyList(); attachments = false; exportMode = "share" })
            },
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { padding ->
            val actionSpace = (padding.calculateBottomPadding() - imeBottom).coerceAtLeast(0.dp) + CalSpacing.page
            val actionSpacePx = with(LocalDensity.current) { actionSpace.toPx() }
            val defaultBringIntoView = LocalBringIntoViewSpec.current
            val bringIntoView = remember(defaultBringIntoView, actionSpacePx) {
                object : BringIntoViewSpec {
                    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                        defaultBringIntoView.calculateScrollDistance(
                            offset, size, (containerSize - actionSpacePx).coerceAtLeast(0f))
                }
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
                // Keep lists behind the transparent action host; reserve its measured height at the end.
                Box(Modifier.padding(
                    start = padding.calculateStartPadding(direction),
                    end = padding.calculateEndPadding(direction),
                    bottom = imeBottom,
                ).consumeWindowInsets(padding).calContent(CalLayout.readingWidth)) {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().testTag("diagnostics_pager"), key = { it }) { tab ->
                        val filtered = remember(scans, query, slowest, failures, tab) { filtered(tab) }
                        LazyColumn(Modifier.fillMaxSize().testTag("diagnostics_page_$tab"), contentPadding = PaddingValues(start = CalSpacing.page, end = CalSpacing.page,
                                top = with(LocalDensity.current) { tabHostHeight.toDp() } + CalSpacing.page, bottom = actionSpace),
                            verticalArrangement = Arrangement.spacedBy(if (tab in 1..3) 0.dp else CalSpacing.section)) {
                            if (tab == 0) {
                                item { Counts(scans) }
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                                        SectionTitle(stringResource(R.string.diag_local_capture))
                                        CalGroup {
                                            Text(stringResource(R.string.diag_capture), style = MaterialTheme.typography.bodyMedium)
                                            Text(stringResource(R.string.diag_retention), style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(stringResource(R.string.diag_small_sample), style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                item {
                                    TextButton(onClick = { clear = true }, enabled = !busy) {
                                        Text(stringResource(R.string.diag_clear), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            if (tab == 2) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                                        SectionTitle(stringResource(R.string.diag_metrics))
                                        TimingSummary(scans)
                                        Text(stringResource(R.string.diag_small_sample), style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                item { SectionTitle(stringResource(R.string.diag_slowest)) }
                            }
                            if (tab == 3) {
                                val categories = scans.flatMap { it.errors }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
                                item { SectionTitle(stringResource(R.string.diag_errors)) }
                                if (categories.isEmpty()) item { Text(stringResource(R.string.diag_no_errors),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                items(categories, key = { "error:" + it.key }) {
                                    CalValueRow(it.key, NumberFormat.getIntegerInstance(locale).format(it.value))
                                }
                                item { SectionTitle(stringResource(R.string.diag_corrected)) }
                                val corrections = scans.flatMap { it.correctedFields }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
                                items(corrections, key = { "field:" + it.key }) {
                                    CalValueRow(fieldLabel(it.key), NumberFormat.getIntegerInstance(locale).format(it.value))
                                }
                                item { Text(stringResource(R.string.diag_unchanged), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            if (tab == 4) {
                                item { Text(stringResource(R.string.diag_small_sample), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                val groups = scans.groupBy { it.brand }.entries.sortedByDescending { it.value.size }
                                if (groups.isEmpty()) item { Text(stringResource(R.string.diag_empty)) }
                                items(groups, key = { it.key ?: "unknown" }) { group ->
                                    Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                                        SectionTitle(group.key ?: stringResource(R.string.diag_unknown))
                                        CalGroup {
                                            val eligible = group.value.filter { it.detailedConsent && it.status in listOf("SUCCESS", "PARTIAL") }
                                            Metric(R.string.diag_total, NumberFormat.getIntegerInstance(locale).format(group.value.size))
                                            Text(stringResource(R.string.diag_correction_samples, eligible.size,
                                                eligible.count { it.correctedFields.isNotEmpty() }), style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                            if (tab in 1..3) {
                                item {
                                    Column(Modifier.padding(bottom = CalSpacing.related), verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                                    OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.diag_search)) },
                                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    SettingsChoiceGroup(options = listOf(false, true), selected = failures,
                                        label = { stringResource(if (it) R.string.diag_failures else R.string.diag_all_scans) }) {
                                        failures = it
                                    }
                                    if (tab != 2) SettingsChoiceGroup(options = listOf(false, true), selected = slowest,
                                        label = { stringResource(if (it) R.string.diag_slowest else R.string.diag_newest) }) {
                                        slowest = it
                                    }
                                    }
                                }
                                if (filtered.isEmpty()) item { Text(stringResource(R.string.diag_empty)) }
                                itemsIndexed(filtered, key = { _, scan -> scan.id }) { index, scan ->
                                    Row(Modifier.fillMaxWidth().animateItem(
                                        fadeInSpec = CalMotion.effects(), placementSpec = CalMotion.spatial(), fadeOutSpec = CalMotion.effects(),
                                    ).calGroupedRow(index, filtered.size), verticalAlignment = Alignment.CenterVertically) {
                                        val scanLabel = stringResource(R.string.diag_scan_details) + ": " +
                                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(scan.timestampMillis))
                                        Checkbox(scan.id in selected, { selected = if (it) selected + scan.id else selected - scan.id },
                                            modifier = Modifier.semantics { contentDescription = scanLabel })
                                        SegmentedListItem(
                                            onClick = { opened = scan.id },
                                            modifier = Modifier.weight(1f),
                                            shapes = ListItemDefaults.segmentedShapes(index, filtered.size,
                                                ListItemDefaults.shapes(shape = MaterialTheme.shapes.extraSmall)),
                                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                                            contentPadding = PaddingValues(CalSpacing.related),
                                            supportingContent = {
                                            Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.micro)) {
                                            Text(statusLabel(scan.status) + " · " + scanTime(scan), style = MaterialTheme.typography.bodyMedium)
                                            if (scan.missingFields.isNotEmpty()) Text(stringResource(R.string.diag_missing) + ": " + fieldNames(scan.missingFields),
                                                style = MaterialTheme.typography.bodyMedium)
                                            if (scan.correctedFields.isNotEmpty()) Text(stringResource(R.string.diag_corrected) + ": " + fieldNames(scan.correctedFields),
                                                style = MaterialTheme.typography.bodyMedium)
                                            if (scan.errors.isNotEmpty()) Text(scan.errors.joinToString(", "), style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.error)
                                            }
                                            },
                                        ) {
                                            Text(scan.productName ?: scan.brand ?: DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(scan.timestampMillis)),
                                                style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SecondaryScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = CalSpacing.medium,
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().onSizeChanged { tabHostHeight = it.height }
                                .padding(top = padding.calculateTopPadding())) {
                        listOf(R.string.diag_overview, R.string.diag_history, R.string.diag_performance,
                            R.string.diag_errors, R.string.diag_brands).forEachIndexed { index, label ->
                            Tab(selected = pager.currentPage == index, onClick = { coroutineScope.launch { pager.animateScrollToPage(index, animationSpec = CalMotion.spatial()) } }, text = { Text(stringResource(label)) })
                        }
                    }
                }
            }
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text(stringResource(R.string.diag_clear_title)) },
        text = { Text(stringResource(R.string.diag_clear_hint)) },
        confirmButton = { TextButton(onClick = { clear = false; selected = emptySet(); onClear() }) { Text(stringResource(R.string.diag_clear)) } },
        dismissButton = { TextButton(onClick = { clear = false }) { Text(stringResource(R.string.diag_cancel)) } })
    if (exportMode != null) AlertDialog(onDismissRequest = { exportMode = null },
        title = { Text(stringResource(R.string.diag_report_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
            Text(stringResource(R.string.diag_report_preview, if (selected.isEmpty()) scans.size else selected.size))
            Text(stringResource(R.string.diag_report_content), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.diag_no_photos), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .testTag("diagnostic_attachments")
                .then(Modifier.toggleable(attachments, role = androidx.compose.ui.semantics.Role.Checkbox) { attachments = it }),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(attachments, onCheckedChange = null)
                Text(stringResource(R.string.diag_include_attachments), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(stringResource(R.string.diag_add_photos))
            }
            if (photos.isNotEmpty()) Text(stringResource(R.string.diag_photo_warning, photos.size), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = {
            pendingMode = exportMode; exportMode = null
            onExport(selected.ifEmpty { null }, true, photos, attachments)
        }) { Text(stringResource(R.string.diag_continue)) } },
        dismissButton = { TextButton(onClick = { exportMode = null }) { Text(stringResource(R.string.diag_cancel)) } })
    scans.firstOrNull { it.id == opened }?.let { scan ->
        ScanDetail(scan, busy, onDismiss = { opened = null }, onBrand = { onBrand(scan.id, it) }, onNetWeight = { onNetWeight(scan.id, it) })
    }
}

@Composable
private fun DiagnosticReportActions(selected: Int, busy: Boolean, error: Boolean, saved: Boolean,
    onSave: () -> Unit, onShare: () -> Unit) {
    // Same transparent pinned-action geometry as the food editor.
    Box(Modifier.fillMaxWidth().testTag("diagnostics_actions")
        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)
            .union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)))
        .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = CalLayout.readingWidth).fillMaxWidth()
        .padding(horizontal = CalSpacing.page, vertical = CalSpacing.related)) {
            Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                if (selected > 0) Text(stringResource(R.string.diag_selected, selected), style = MaterialTheme.typography.labelLarge)
                if (busy) CalWorkProgressRow()
                if (error) Text(stringResource(R.string.diag_error), color = MaterialTheme.colorScheme.error)
                if (saved) Text(stringResource(R.string.diag_saved), color = MaterialTheme.colorScheme.primary)
                BoxWithConstraints {
                    val stacked = maxWidth < 320.dp * LocalConfiguration.current.fontScale + CalSpacing.related
                    val save: @Composable (Modifier) -> Unit = { modifier ->
                        CalButton(onClick = onSave, enabled = !busy,
                            modifier = modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.diag_save), textAlign = TextAlign.Center)
                        }
                    }
                    val share: @Composable (Modifier) -> Unit = { modifier ->
                        CalTonalButton(onClick = onShare, enabled = !busy,
                            modifier = modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.diag_share), textAlign = TextAlign.Center)
                        }
                    }
                    if (stacked) Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                        save(Modifier.fillMaxWidth())
                        share(Modifier.fillMaxWidth())
                    } else Row(horizontalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                        save(Modifier.weight(1f))
                        share(Modifier.weight(1f))
                    }
                }
            }
    }
}

@Composable
private fun Counts(scans: List<DiagnosticScan>) {
    val locale = LocalConfiguration.current.locales[0]
    val number = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
        SectionTitle(stringResource(R.string.diag_total))
        CalGroup {
            AnimatedNutrition(number.format(scans.size),
                style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum", color = MaterialTheme.colorScheme.primary))
            Metric(R.string.diag_success, number.format(scans.count { it.status == "SUCCESS" }))
            Metric(R.string.diag_partial, number.format(scans.count { it.status == "PARTIAL" }))
            Metric(R.string.diag_failed, number.format(scans.count { it.status == "FAILURE" }))
            Metric(R.string.diag_cancelled, number.format(scans.count { it.status == "CANCELLED" }))
            Metric(R.string.diag_running, number.format(scans.count { it.status == "RUNNING" }))
            Metric(R.string.diag_interrupted, number.format(scans.count { it.status == "INTERRUPTED" }))
        }
    }
}

@Composable
private fun TimingSummary(scans: List<DiagnosticScan>) {
    val statistics = DiagnosticStatistics.from(scans)
    val locale = LocalConfiguration.current.locales[0]
    CalGroup {
        Text(stringResource(R.string.diag_all_attempts), style = MaterialTheme.typography.bodyMedium)
        Metric(R.string.diag_timing_samples, NumberFormat.getIntegerInstance(locale).format(scans.count { it.status !in listOf("RUNNING", "INTERRUPTED") }))
        Metric(R.string.diag_average, statistics.averageMs?.let { stringResource(R.string.diag_ms, NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(it)) } ?: stringResource(R.string.diag_not_measured))
        Metric(R.string.diag_median, statistics.medianMs?.let { stringResource(R.string.diag_ms, NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(it)) } ?: stringResource(R.string.diag_not_measured))
        Metric(R.string.diag_p95, timeText(statistics.p95Ms))
    }
}

@Composable
private fun Metric(label: Int, value: String) {
    CalValueRow(stringResource(label), value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanDetail(scan: DiagnosticScan, busy: Boolean, onDismiss: () -> Unit, onBrand: (String) -> Unit, onNetWeight: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var brand by rememberSaveable(scan.id) { mutableStateOf(scan.brand.orEmpty()) }
    var netWeight by rememberSaveable(scan.id) { mutableStateOf("") }
    var technical by rememberSaveable(scan.id) { mutableStateOf(false) }
    var raw by rememberSaveable(scan.id) { mutableStateOf(false) }
    val technicalExpansion = stringResource(if (technical) R.string.expanded else R.string.collapsed)
    val rawExpansion = stringResource(if (raw) R.string.expanded else R.string.collapsed)
    val validWeight = netWeight.replace(',', '.').toBigDecimalOrNull()?.let {
        it > java.math.BigDecimal.ZERO && it <= java.math.BigDecimal("1000000") && it.scale() <= 2
    } == true
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = CalLayout.readingWidth,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        DiagnosticSheetBackInput(sheetState.isVisible)
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(horizontal = CalSpacing.page),
                verticalArrangement = Arrangement.spacedBy(CalSpacing.section)) {
                Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                    Text(scan.productName ?: stringResource(R.string.diag_scan_details), style = MaterialTheme.typography.headlineSmall)
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
                        .format(Date(scan.timestampMillis)), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(statusLabel(scan.status), style = MaterialTheme.typography.titleMedium)
                    Text(scanTime(scan), style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.primary)
                    scan.brand?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                    SectionTitle(stringResource(R.string.diag_metrics))
                    CalGroup {
                        Metric(R.string.diag_init, timeText(scan.initializationMs))
                        Metric(R.string.diag_detection, timeText(scan.detectionMs))
                        Metric(R.string.diag_recognition, timeText(scan.recognitionMs))
                        Metric(R.string.diag_parsing, timeText(scan.parsingMs))
                        Metric(R.string.diag_regions, scan.detectedRegions?.let {
                            NumberFormat.getIntegerInstance(locale).format(it)
                        } ?: stringResource(R.string.diag_not_measured))
                        Metric(R.string.diag_resolution, stringResource(R.string.image_dimensions, scan.width, scan.height))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                    SectionTitle(stringResource(R.string.diag_recognition_quality))
                    CalGroup {
                        listOf(R.string.diag_confidence_mean to scan.confidenceMean,
                            R.string.diag_confidence_min to scan.confidenceMin,
                            R.string.diag_confidence_max to scan.confidenceMax).forEach { (label, value) ->
                            Metric(label, value?.let {
                                NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 3 }.format(it)
                            } ?: stringResource(R.string.diag_not_measured))
                        }
                        listOf(R.string.diag_missing to scan.missingFields, R.string.diag_ambiguous to scan.ambiguousFields,
                            R.string.diag_corrected to scan.correctedFields).forEach { (label, fields) ->
                            if (fields.isNotEmpty()) CalValueRow(stringResource(label), fieldNames(fields), stacked = true)
                        }
                    }
                    Text(stringResource(R.string.diag_unchanged), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (scan.errors.isNotEmpty()) SelectionContainer {
                        Text(scan.errors.joinToString("\n"), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error)
                    } else Text(stringResource(R.string.diag_no_errors), style = MaterialTheme.typography.bodyMedium)
                }
                if (scan.parsedFields.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                    SectionTitle(stringResource(R.string.diag_parsed))
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                            scan.parsedFields.forEach { (field, value) -> CalValueRow(fieldLabel(field), value) }
                        }
                    }
                }
                if (scan.corrections.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                    SectionTitle(stringResource(R.string.diag_corrected))
                    scan.corrections.forEach { correction ->
                        Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.micro)) {
                            CalValueRow(fieldLabel(correction.field),
                                (correction.before ?: "—") + " → " + (correction.after ?: "—"), stacked = true)
                            Text(stringResource(if (correction.category == "USER_PORTION_ADJUSTMENT")
                                R.string.diag_portion_adjustment else R.string.diag_explicit_correction),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (scan.aiAttempts.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                    SectionTitle(stringResource(R.string.ai_diagnostics))
                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        scan.aiAttempts.forEachIndexed { index, attempt ->
                            CalGroup(index = index, count = scan.aiAttempts.size) {
                                Text("${attempt.profile} · ${attempt.stage}", style = MaterialTheme.typography.titleMedium)
                                CalValueRow(stringResource(R.string.diag_runtime), "LiteRT-LM ${attempt.runtimeVersion}")
                                Text(stringResource(R.string.lab_backend_value, attempt.actualBackend ?: attempt.requestedBackend),
                                    style = MaterialTheme.typography.bodyMedium)
                                Metric(R.string.diag_init, timeText(attempt.initializationMs))
                                Metric(R.string.diag_recognition, timeText(attempt.inferenceMs))
                                SelectionContainer {
                                    Text(attempt.artifactHash, style = MaterialTheme.typography.bodySmall)
                                }
                                attempt.fallbackReason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                attempt.validationError?.let {
                                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                }
                                attempt.processExit?.let {
                                    Text(stringResource(R.string.diag_process_exit, it.reason, it.status), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                scan.rawText?.let {
                    Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                        TextButton(onClick = { raw = !raw }, modifier = Modifier.fillMaxWidth()
                            .semantics { stateDescription = rawExpansion }) { Text(stringResource(R.string.diag_raw)) }
                        AnimatedVisibility(raw, enter = CalMotion.enter(), exit = CalMotion.exit()) {
                            CalGroup { SelectionContainer { Text(it, style = MaterialTheme.typography.bodyMedium) } }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                    TextButton(onClick = { technical = !technical }, modifier = Modifier.fillMaxWidth()
                        .semantics { stateDescription = technicalExpansion }) { Text(stringResource(R.string.diag_technical)) }
                    AnimatedVisibility(technical, enter = CalMotion.enter(), exit = CalMotion.exit()) {
                        CalGroup {
                            Metric(R.string.diag_app_version, scan.appVersion)
                            Metric(R.string.diag_android_version, scan.androidVersion)
                            Metric(R.string.diag_device, scan.device)
                            Metric(R.string.lab_model, scan.model)
                            Metric(R.string.diag_runtime, scan.runtime)
                            Metric(R.string.diag_orientation, scan.orientation)
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                                    scan.modelRevisions.forEach { (name, revision) ->
                                        Text("$name: $revision", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    scan.modelHashes.forEach { (name, hash) ->
                                        Text("$name: $hash", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
                if (scan.detailedConsent) {
                    SectionTitle(stringResource(R.string.diag_annotations))
                    OutlinedTextField(brand, { brand = it.take(120) }, label = { Text(stringResource(R.string.diag_brand)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text(stringResource(R.string.diag_brand_hint), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onBrand(brand) }, enabled = !busy) { Text(stringResource(R.string.diag_save_brand)) }
                    OutlinedTextField(netWeight, { netWeight = it.take(20) }, label = { Text(stringResource(R.string.diag_net_weight)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, suffix = { Text(stringResource(R.string.grams)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        isError = netWeight.isNotBlank() && !validWeight,
                        supportingText = { Text(stringResource(R.string.diag_weight_hint)) })
                    TextButton(onClick = { onNetWeight(netWeight.replace(',', '.')) }, enabled = validWeight && !busy) { Text(stringResource(R.string.diag_save_weight)) }
                }
                Spacer(Modifier.height(CalSpacing.related))
            }
            CalTonalButton(onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(horizontal = CalSpacing.page, vertical = CalSpacing.related).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.diag_close))
            }
        }
    }
}
/**
 * Material3 1.5 inherits the tab's explicitly scoped owner into its dialog composition.
 * System Back arrives at the dialog window instead. Relay the complete gesture to the
 * native sheet handler, retaining its predictive animation and cancellation behavior.
 * No bridge is needed when the sheet already resolves its window's owner.
 */
@Composable
private fun DiagnosticSheetBackInput(visible: Boolean) {
    val scoped = androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner.current
    val window = androidx.compose.ui.platform.LocalView.current.findViewTreeNavigationEventDispatcherOwner()
    DisposableEffect(scoped, window, visible) {
        if (visible && scoped != null && window != null && scoped !== window) {
            val input = androidx.navigationevent.DirectNavigationEventInput()
            scoped.navigationEventDispatcher.addInput(input)
            val handler = object : androidx.navigationevent.NavigationEventHandler<androidx.navigationevent.NavigationEventInfo>(
                initialInfo = androidx.navigationevent.NavigationEventInfo.None,
                isBackEnabled = true,
                isForwardEnabled = false,
            ) {
                override fun onBackStarted(event: androidx.navigationevent.NavigationEvent) = input.backStarted(event)
                override fun onBackProgressed(event: androidx.navigationevent.NavigationEvent) = input.backProgressed(event)
                override fun onBackCancelled() = input.backCancelled()
                override fun onBackCompleted() = input.backCompleted()
            }
            window.navigationEventDispatcher.addHandler(handler)
            onDispose {
                handler.remove()
                scoped.navigationEventDispatcher.removeInput(input)
            }
        } else onDispose {}
    }
}

@Composable
private fun scanTime(scan: DiagnosticScan): String = if (scan.status == "RUNNING" || scan.status == "INTERRUPTED") stringResource(R.string.diag_duration_unavailable) else timeText(scan.totalMs)

@Composable
private fun timeText(value: Long?): String = if (value == null) stringResource(R.string.diag_not_measured)
    else stringResource(R.string.diag_ms, NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(value))

@Composable
private fun statusLabel(status: String): String = stringResource(when (status) {
    "SUCCESS" -> R.string.diag_status_success
    "PARTIAL" -> R.string.diag_status_partial
    "CANCELLED" -> R.string.diag_status_cancelled
    "RUNNING" -> R.string.diag_running
    "INTERRUPTED" -> R.string.diag_interrupted
    else -> R.string.diag_status_failure
})

@Composable
private fun fieldNames(fields: List<String>): String = fields.map { fieldLabel(it) }.joinToString(", ")

@Composable
private fun fieldLabel(field: String): String = when (field) {
    "NAME", "PRODUCT_NAME", "productName", "name", "product_name" -> stringResource(R.string.diag_name)
    "NET_WEIGHT", "netWeight", "netWeightGrams", "net_weight" -> stringResource(R.string.diag_net_weight)
    "ENERGY", "CALORIES", "calories", "caloriesPer100g", "kcal" -> stringResource(R.string.energy)
    "PROTEIN", "protein", "proteinPer100g" -> stringResource(R.string.protein)
    "FAT", "fat", "fatPer100g" -> stringResource(R.string.fat)
    "CARBS", "CARBOHYDRATES", "carbohydrates", "carbs", "carbsPer100g" -> stringResource(R.string.carbs)
    "AMOUNT", "AMOUNT_EATEN", "amountEaten", "amount", "consumedGrams" -> stringResource(R.string.diag_amount)
    else -> field
}
