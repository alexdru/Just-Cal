package com.justcal.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.justcal.app.ui.theme.LegacyCalTypography
import com.justcal.app.ui.theme.LocalMotionEnabled
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.animation.togetherWith
import com.justcal.app.ui.theme.CalMotion
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import com.justcal.app.domain.Appearance
import com.justcal.app.domain.ColorStyle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.justcal.app.camera.*
import com.justcal.app.ocr.PackageScanPrefill
import com.justcal.app.ui.theme.JustCalTheme
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable data class ImageAcquisition(val request: ScanRequest, val source: ImageSource) : NavKey
@Serializable data class PhotoReview(val image: PreparedImage) : NavKey
@Serializable data object LocalAiLab : NavKey
@Serializable data object DataBackup : NavKey
@Serializable data object OcrDiagnostics : NavKey

@Serializable data class DiaryDate(val dayEpoch: Long) : NavKey
@Serializable data class FoodEditor(
    val id: Long? = null,
    val dayEpoch: Long? = null,
    val origin: MainTab = MainTab.HOME,
    val scan: PackageScanPrefill? = null,
) : NavKey

private data class ThemeState(val loading: Boolean, val appearance: Appearance, val colorStyle: ColorStyle)
private fun SettingsState.themeState() = ThemeState(loading, draft.appearance, draft.colorStyle)

@Composable
fun JustCalApp(splashVisible: Boolean = false) {
    val settingsViewModel = hiltViewModel<SettingsViewModel>()
    // This is an initial snapshot only; subsequent updates are observed below.
    val initialTheme = remember(settingsViewModel) { settingsViewModel.state.value.themeState() }
    // Saving/profile emissions belong to Settings, not every Navigation 3 entry.
    val themeState by remember(settingsViewModel) {
        settingsViewModel.state.map { it.themeState() }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = initialTheme)
    // Mount the first palette only after preferences load; no wrong-color startup reveal.
    if (themeState.loading) return
    val appearance = themeState.appearance
    val colorStyle = themeState.colorStyle
    val dark = when (appearance) {
        Appearance.SYSTEM -> isSystemInDarkTheme()
        Appearance.LIGHT -> false
        Appearance.DARK -> true
    }
    val pager = rememberPagerState { MainTab.entries.size }
    var warmRootTabs by remember { mutableStateOf(false) }
    LaunchedEffect(splashVisible) {
        if (!splashVisible) {
            withFrameNanos { }
            withFrameNanos { }
            warmRootTabs = true
        }
    }
    val selected by remember { derivedStateOf { MainTab.entries[pager.settledPage] } }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var tabJob by remember { mutableStateOf<Job?>(null) }
    var requestedTab by remember { mutableStateOf<MainTab?>(null) }
    var barHeight by remember { mutableIntStateOf(0) }
    val barDp = with(LocalDensity.current) { barHeight.toDp() }
    val reserveBar: @Composable () -> Unit = { Spacer(Modifier.fillMaxWidth().height(barDp)) }
    val stacks = MainTab.entries.associateWith { tab ->
        key(tab) { rememberNavBackStack(tab) }
    }
    val animations = com.justcal.app.ui.theme.rememberMotionEnabled()
    val selectTab: (MainTab) -> Unit = { tab ->
        settingsViewModel.flushEdits()
        focus.clearFocus()
        keyboard?.hide()
        tabJob?.cancel()
        requestedTab = tab
        val job = scope.launch {
            if (animations) pager.animateScrollToPage(tab.ordinal, animationSpec = CalMotion.tab())
            else pager.scrollToPage(tab.ordinal)
        }
        tabJob = job
        job.invokeOnCompletion { if (tabJob === job) requestedTab = null }
    }
    LaunchedEffect(selected) {
        settingsViewModel.flushEdits()
        focus.clearFocus()
        keyboard?.hide()
    }
    var launcherDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var launcherMode by rememberSaveable { mutableStateOf<ScanMode?>(null) }

    JustCalTheme(darkTheme = dark, colorStyle = colorStyle, splashVisible = splashVisible) {
        val messages = remember { androidx.compose.material3.SnackbarHostState() }
        // Decorate every stack even while inactive, retaining its entry state and ViewModels.
        val entries = stacks.mapValues { (tab, stack) ->
            key(tab) {
                rememberDecoratedNavEntries(
                    backStack = stack,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        entry<MainTab> { root ->
                            MainScreen(
                                root, settingsViewModel, bar = reserveBar,
                                onLab = { if (stack.lastOrNull() == root) stack.add(LocalAiLab) },
                                onBackup = { if (stack.lastOrNull() == root) {
                                    settingsViewModel.flushEdits()
                                    stack.add(DataBackup)
                                } },
                                onDiagnostics = { if (stack.lastOrNull() == root) {
                                    settingsViewModel.flushEdits()
                                    stack.add(OcrDiagnostics)
                                } },
                                onEdit = { id ->
                                    if (stack.lastOrNull() == root) stack.add(FoodEditor(id = id, origin = tab))
                                },
                            ) { day ->
                                if (stack.lastOrNull() == root) stack.add(DiaryDate(day))
                            }
                        }
                        entry<DiaryDate> { route ->
                            val viewModel = hiltViewModel<DayViewModel, DayViewModel.Factory> {
                                it.create(route.dayEpoch)
                            }
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            TodayScreen(state,
                                onEdit = { id ->
                                    if (stack.lastOrNull() == route) stack.add(FoodEditor(id = id, origin = tab))
                                },
                                onGoal = viewModel::setGoal, onRetry = viewModel::retry,
                                onBack = { if (stack.lastOrNull() == route) stack.removeLastOrNull() },
                                bottomBar = reserveBar)
                        }
                        entry<DataBackup> { route ->
                            DiaryBackupScreen(hiltViewModel<DiaryBackupViewModel>(),
                                onBack = { if (stack.lastOrNull() == route) stack.removeLastOrNull() })
                        }
                        entry<OcrDiagnostics> { route ->
                            OcrDiagnosticsScreen(hiltViewModel<OcrDiagnosticsViewModel>(),
                                onBack = { if (stack.lastOrNull() == route) stack.removeLastOrNull() })
                        }
                        entry<LocalAiLab> { route ->
                            val viewModel = hiltViewModel<LocalAiLabViewModel>()
                            DisposableEffect(route) {
                                onDispose { if (route !in stack) viewModel.closeLab() }
                            }
                            // Keep the excluded Lab's existing typography/motion without touching its implementation.
                            MaterialTheme(typography = LegacyCalTypography, motionScheme = CalMotion.standard) {
                            LocalAiLabScreen(viewModel, onBack = {
                                if (stack.lastOrNull() == route) {
                                    viewModel.closeLab()
                                    stack.removeLastOrNull()
                                }
                            })
                            }
                        }
                        entry<ImageAcquisition> { route ->
                            val viewModel = hiltViewModel<ImageInputViewModel>()
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            LaunchedEffect(state.image) {
                                state.image?.let { image ->
                                    if (stack.lastOrNull() == route) {
                                        viewModel.transfer()
                                        stack[stack.lastIndex] = PhotoReview(image)
                                    }
                                }
                            }
                            ImageSourceScreen(route.request, route.source, state, viewModel.images,
                                onImage = { uri, source -> viewModel.prepare(uri, source, route.request) },
                                onRetry = viewModel::retry,
                                onPickerCancelled = { if (stack.lastOrNull() == route) stack.removeLastOrNull() },
                                onBack = { if (stack.lastOrNull() == route) stack.removeLastOrNull() })
                        }
                        entry<PhotoReview> { route ->
                            val viewModel = hiltViewModel<PhotoReviewViewModel>()
                            viewModel.attach(route.image)
                            val packageState by viewModel.state.collectAsStateWithLifecycle()
                            LaunchedEffect(packageState.prefill) {
                                packageState.prefill?.let {
                                    if (stack.lastOrNull() == route) stack[stack.lastIndex] =
                                        FoodEditor(dayEpoch = route.image.request.dayEpoch, origin = tab, scan = it)
                                }
                            }
                            DisposableEffect(route) {
                                onDispose {
                                    // A configuration change keeps the route; a pop/replacement releases it.
                                    if (route !in stack) viewModel.images.discard(route.image.uri)
                                }
                            }
                            PhotoReviewScreen(route.image, viewModel.images,
                                packageState = packageState,
                                onRecognize = { viewModel.recognize(route.image) },
                                onManual = {
                                    if (stack.lastOrNull() == route) stack[stack.lastIndex] =
                                        FoodEditor(dayEpoch = route.image.request.dayEpoch, origin = tab)
                                },
                                onReplace = {
                                    if (stack.lastOrNull() == route) {
                                        viewModel.images.discard(route.image.uri)
                                        stack[stack.lastIndex] = ImageAcquisition(route.image.request, route.image.source)
                                    }
                                },
                                onBack = {
                                    if (stack.lastOrNull() == route) {
                                        viewModel.images.discard(route.image.uri)
                                        stack.removeLastOrNull()
                                    }
                                })
                        }
                        entry<FoodEditor> { route ->
                            val viewModel = hiltViewModel<EditorViewModel, EditorViewModel.Factory> {
                                it.create(route.id, route.dayEpoch, route.scan)
                            }
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            val close = {
                                if ((stack.lastOrNull() == route) && (stack.size > 1)) stack.removeLastOrNull()
                                Unit
                            }
                            var removed by remember { mutableStateOf(false) }
                            val completeMessage = androidx.compose.ui.res.stringResource(
                                if (removed) com.justcal.app.R.string.food_deleted else com.justcal.app.R.string.food_saved,
                            )
                            val haptic = LocalHapticFeedback.current
                            LaunchedEffect(state.completed) {
                                if (state.completed) {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    close()
                                    scope.launch { messages.showSnackbar(completeMessage) }
                                }
                            }
                            EditorScreen(state, route.id != null, viewModel::change, viewModel::save,
                                { removed = true; viewModel.delete() }, close, viewModel::retryLoad, scan = route.scan,
                                claimInitialFocus = viewModel::claimInitialFocus)
                        }
                    },
                )
            }
        }
        val current = stacks.getValue(selected).lastOrNull()
        val showNavigation = current is MainTab || current is DiaryDate
        // A root handler has no role in a child gesture. Remove it entirely so a
        // cancelled child preview cannot leave a stale root handler in the event chain.
        if (current is MainTab) {
            val rootBack = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
            NavigationBackHandler(
                state = rootBack,
                isBackEnabled = (selected != MainTab.HOME || requestedTab?.let { it != MainTab.HOME } == true || pager.targetPage != 0) &&
                    launcherDay == null && WindowInsets.ime.getBottom(LocalDensity.current) == 0,
                onBackCompleted = { selectTab(MainTab.HOME) },
            )
        }
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().testTag("main_pager"),
                // Keep the three small root trees warm after first display. Keeping
                // only one neighbor composed mounts Settings during Home -> Diary
                // and repeats that expensive work on every return to Home.
                beyondViewportPageCount = if (warmRootTabs) MainTab.entries.lastIndex else 0,
                key = { MainTab.entries[it] },
                userScrollEnabled = current is MainTab && WindowInsets.ime.getBottom(LocalDensity.current) == 0 && launcherDay == null,
                overscrollEffect = null,
                flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = CalMotion.tab()),
            ) { index ->
                val tab = MainTab.entries[index]
                val active = pager.settledPage == index
                val lifecycle = rememberLifecycleOwner(
                    maxLifecycle = if (active) Lifecycle.State.RESUMED else Lifecycle.State.STARTED,
                )
                val dispatcher = rememberNavigationEventDispatcherOwner(enabled = active)
                CompositionLocalProvider(
                    LocalLifecycleOwner provides lifecycle,
                    LocalNavigationEventDispatcherOwner provides dispatcher,
                ) {
                    val noMotion = !LocalMotionEnabled.current
                    NavDisplay(
                        modifier = Modifier.fillMaxSize().then(
                            if (active) Modifier else Modifier.clearAndSetSemantics {},
                        ),
                        entries = entries.getValue(tab),
                        onBack = {
                            val stack = stacks.getValue(tab)
                            if (stack.size > 1) stack.removeLastOrNull()
                            else if (tab != MainTab.HOME) selectTab(MainTab.HOME)
                        },
                        transitionSpec = {
                            if (noMotion) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                            // Cancellation leaves the preview scene as the animation target.
                            // Infer direction from scene depth, not the now-idle gesture flag.
                            else CalMotion.destination(targetState.previousEntries.size >= initialState.previousEntries.size)
                        },
                        popTransitionSpec = {
                            if (noMotion) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                            else CalMotion.destination(false)
                        },
                        predictivePopTransitionSpec = {
                            if (noMotion) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                            else CalMotion.destination(false)
                        },
                    )
                }
            }
            androidx.compose.material3.SnackbarHost(messages,
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp)
                    .padding(bottom = if (showNavigation) barDp else 24.dp))
            if (showNavigation) Box(
                Modifier.align(Alignment.BottomCenter),
            ) {
                Box(Modifier.onSizeChanged { barHeight = it.height }) {
                FloatingNavigation(
                    selected, selectTab,
                    onAdd = if ((requestedTab ?: MainTab.entries[pager.targetPage]) == MainTab.SETTINGS) null else ({
                        launcherMode = null
                        launcherDay = (current as? DiaryDate)?.dayEpoch ?: LocalDate.now().toEpochDay()
                    }),
                    position = { pager.currentPage + pager.currentPageOffsetFraction },
                    launcherOpen = launcherDay != null,
                )
                }
            }
        }
        launcherDay?.let { day ->
            AddFoodLauncher(day, launcherMode,
                onDismiss = { launcherDay = null; launcherMode = null },
                onMode = { launcherMode = it },
                onManual = {
                    launcherDay = null
                    stacks.getValue(selected).add(FoodEditor(dayEpoch = day, origin = selected))
                },
                onSource = { request, source ->
                    launcherDay = null
                    stacks.getValue(selected).add(ImageAcquisition(request, source))
                })
        }
    }
}

@Composable
private fun MainScreen(
    tab: MainTab, settingsViewModel: SettingsViewModel,
    bar: @Composable () -> Unit, onLab: () -> Unit,
    onBackup: () -> Unit, onDiagnostics: () -> Unit, onEdit: (Long) -> Unit, onDay: (Long) -> Unit,
) {
    when (tab) {
        MainTab.HOME -> {
            val viewModel = hiltViewModel<TodayViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshDate() }
            TodayScreen(state, onEdit, viewModel::setGoal, viewModel::retry, bottomBar = bar)
        }
        MainTab.DIARY -> {
            val viewModel = hiltViewModel<HistoryViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()
            HistoryScreen(state, onDay, viewModel::retry, bottomBar = bar)
        }
        MainTab.SETTINGS -> {
            // Deliver edit echoes synchronously to Compose state, including cursor updates
            // between hardware key events within one frame.
            val state by settingsViewModel.state.collectAsStateWithLifecycle(context = kotlinx.coroutines.Dispatchers.Main.immediate)
            SettingsScreen(state, settingsViewModel::change,
                settingsViewModel::retry, onLab = onLab, bottomBar = bar,
                onBackup = onBackup, onDiagnostics = onDiagnostics)
        }
    }
}
