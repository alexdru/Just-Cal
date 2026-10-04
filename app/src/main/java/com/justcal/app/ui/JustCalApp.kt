package com.justcal.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import com.justcal.app.ui.theme.CalMotion
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import com.justcal.app.domain.Appearance
import com.justcal.app.ui.theme.JustCalTheme
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable data class DiaryDate(val dayEpoch: Long) : NavKey
@Serializable data class FoodEditor(
    val id: Long? = null,
    val dayEpoch: Long? = null,
    val origin: MainTab = MainTab.HOME,
) : NavKey

@Composable
fun JustCalApp() {
    val settingsViewModel = hiltViewModel<SettingsViewModel>()
    val appearance by settingsViewModel.appearance.collectAsStateWithLifecycle()
    val dark = when (appearance) {
        Appearance.SYSTEM -> isSystemInDarkTheme()
        Appearance.LIGHT -> false
        Appearance.DARK -> true
    }
    var selected by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val stacks = MainTab.entries.associateWith { tab ->
        key(tab) { rememberNavBackStack(tab) }
    }
    val selectTab: (MainTab) -> Unit = { selected = it }

    JustCalTheme(darkTheme = dark) {
        // Decorate every stack even while inactive, retaining its entry state and ViewModels.
        val entries = stacks.mapValues { (tab, stack) ->
            key(tab) {
                // One date-carrying Add Food action, shared by roots and Day Detail.
                val openAddFood: (Long) -> Unit = { day -> stack.add(FoodEditor(dayEpoch = day, origin = tab)) }
                rememberDecoratedNavEntries(
                    backStack = stack,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        entry<MainTab> { root ->
                            // The global action boundary can later open a launcher with this date.
                            val addFood = dropUnlessResumed {
                                if (stack.lastOrNull() == root) {
                                    openAddFood(LocalDate.now().toEpochDay())
                                }
                            }
                            MainScreen(
                                root, settingsViewModel, onSelect = selectTab, onAdd = addFood,
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
                                bottomBar = {
                                    FloatingNavigation(tab, selectTab,
                                        onAdd = dropUnlessResumed {
                                            if (stack.lastOrNull() == route) {
                                                openAddFood(route.dayEpoch)
                                            }
                                        })
                                })
                        }
                        entry<FoodEditor> { route ->
                            val viewModel = hiltViewModel<EditorViewModel, EditorViewModel.Factory> {
                                it.create(route.id, route.dayEpoch)
                            }
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            val close = {
                                if ((stack.lastOrNull() == route) && (stack.size > 1)) stack.removeLastOrNull()
                                Unit
                            }
                            LaunchedEffect(state.completed) { if (state.completed) close() }
                            EditorScreen(state, route.id != null, viewModel::change, viewModel::save,
                                viewModel::delete, close, viewModel::retryLoad)
                        }
                    },
                )
            }
        }
        // Exit through Home; inactive destination stacks remain intact.
        val visibleEntries = if (selected == MainTab.HOME) entries.getValue(MainTab.HOME)
            else entries.getValue(MainTab.HOME) + entries.getValue(selected)
        NavDisplay(
            entries = visibleEntries,
            onBack = {
                val stack = stacks.getValue(selected)
                if (stack.size > 1) stack.removeLastOrNull() else selected = MainTab.HOME
            },
            transitionSpec = {
                fadeIn(tween(CalMotion.navigationDurationMillis)) togetherWith
                    fadeOut(tween(CalMotion.navigationDurationMillis))
            },
            popTransitionSpec = {
                fadeIn(tween(CalMotion.navigationDurationMillis)) togetherWith
                    fadeOut(tween(CalMotion.navigationDurationMillis))
            },
            // Keep Navigation 3's platform predictive-back transition.
        )
    }
}

@Composable
private fun MainScreen(
    tab: MainTab, settingsViewModel: SettingsViewModel,
    onSelect: (MainTab) -> Unit, onAdd: () -> Unit, onEdit: (Long) -> Unit, onDay: (Long) -> Unit,
) {
    val bar: @Composable () -> Unit = {
        FloatingNavigation(tab, onSelect, onAdd = onAdd.takeUnless { tab == MainTab.SETTINGS })
    }
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
            val state by settingsViewModel.state.collectAsStateWithLifecycle()
            SettingsScreen(state, settingsViewModel::change,
                settingsViewModel::save, settingsViewModel::retry, bottomBar = bar)
        }
    }
}
