package com.justcal.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

@Serializable
data object Today : NavKey
@Serializable
data class FoodEditor(val id: Long? = null) : NavKey

@Composable
fun JustCalApp() {
    val backStack = rememberNavBackStack(Today)
    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Today> {
                val viewModel = hiltViewModel<TodayViewModel>()
                val state = viewModel.state.collectAsStateWithLifecycle().value
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshDate() }
                TodayScreen(
                    state = state,
                    onAdd = dropUnlessResumed { if (backStack.last() == Today) backStack.add(FoodEditor()) },
                    onEdit = { id -> if (backStack.last() == Today) backStack.add(FoodEditor(id)) },
                    onGoal = viewModel::setGoal,
                    onRetry = viewModel::retry,
                )
            }
            entry<FoodEditor> { key ->
                val viewModel = hiltViewModel<EditorViewModel, EditorViewModel.Factory>(
                    creationCallback = { factory -> factory.create(key.id) },
                )
                val state = viewModel.state.collectAsStateWithLifecycle().value
                val close = { if (backStack.lastOrNull() == key && backStack.size > 1) { backStack.removeLastOrNull() }; Unit }
                LaunchedEffect(state.completed) { if (state.completed) close() }
                EditorScreen(
                    state = state, editing = key.id != null, onChange = viewModel::change,
                    onSave = viewModel::save, onDelete = viewModel::delete,
                    onBack = close, onRetry = viewModel::retryLoad,
                )
            }
        },
    )
}
