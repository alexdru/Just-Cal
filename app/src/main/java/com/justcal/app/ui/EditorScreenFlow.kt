package com.justcal.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import com.justcal.app.domain.*
import com.justcal.app.domain.Retention
import com.justcal.app.ocr.PackageScanPrefill
import com.justcal.app.ui.theme.CalMotion
import com.justcal.app.ui.theme.LocalMotionEnabled
import kotlinx.serialization.Serializable

@Serializable private data object MatchingPage : NavKey
@Serializable private data object CheckValuesPage : NavKey

/** Navigation owns only the page. The parent EditorViewModel still owns the single draft. */
@Composable
fun EditorScreen(
    state: EditorState, editing: Boolean, onChange: (FoodField, String) -> Unit,
    onSave: () -> Unit, onDelete: () -> Unit, onBack: () -> Unit, onRetry: () -> Unit,
    scan: PackageScanPrefill? = null, claimInitialFocus: () -> Boolean = { false },
    onChangeDate: (Long) -> Unit = {}, onDuplicate: (Long) -> Unit = {},
    onMatchAction: (String) -> Unit = {}, onInspectMatch: (String) -> Unit = {}, onReuseMatch: (String) -> Unit = {},
    onRetention: (Retention) -> Unit = {}, onProductDetails: (ProductDetails) -> Unit = {},
) {
    val currentState = rememberUpdatedState(state)
    val pages = rememberNavBackStack(if (scan != null && !editing) MatchingPage else CheckValuesPage)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val motion = LocalMotionEnabled.current
    fun navigate(page: NavKey) {
        focus.clearFocus(); keyboard?.hide(); onMatchAction("cancel")
        if (page == MatchingPage) onMatchAction("expand")
        val index = pages.indexOf(page)
        if (index >= 0) while (pages.lastIndex > index) pages.removeLastOrNull()
        else if (pages.lastOrNull() != page) pages.add(page)
    }
    fun back() {
        onMatchAction("cancel")
        focus.clearFocus(); keyboard?.hide()
        if (pages.size > 1) {
            pages.removeLastOrNull()
            if (pages.lastOrNull() == MatchingPage) onMatchAction("expand")
        } else onBack()
    }
    NavDisplay(
        backStack = pages, onBack = ::back,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        transitionSpec = { if (motion) CalMotion.destination(true) else EnterTransition.None togetherWith ExitTransition.None },
        popTransitionSpec = { if (motion) CalMotion.destination(false) else EnterTransition.None togetherWith ExitTransition.None },
        predictivePopTransitionSpec = { if (motion) CalMotion.destination(false) else EnterTransition.None togetherWith ExitTransition.None },
        entryProvider = { page ->
            NavEntry(page) {
                when (page) {
                    MatchingPage -> FoodMatchingScreen(currentState.value, scan, onChange, onMatchAction, onInspectMatch,
                        onReuseMatch, onBack = ::back, onCheck = { navigate(CheckValuesPage) })
                    CheckValuesPage -> CheckFoodScreen(currentState.value, editing, onChange, onSave, onDelete, ::back, onRetry,
                        scan, claimInitialFocus, onChangeDate, onDuplicate, onMatchAction,
                        onReuseMatch, onRetention, onProductDetails, onMatching = { navigate(MatchingPage) })
                    else -> error("Unknown editor page")
                }
            }
        },
    )
}
