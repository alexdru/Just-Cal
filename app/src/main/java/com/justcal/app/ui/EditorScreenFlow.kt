package com.justcal.app.ui

import androidx.compose.runtime.Composable
import com.justcal.app.domain.FoodField
import com.justcal.app.domain.ProductDetails
import com.justcal.app.domain.Retention
import com.justcal.app.ocr.PackageScanPrefill

/**
 * One editor owns recognition, saved-food suggestions, editable values and the sole Save.
 * Matching never creates a second draft or navigation step.
 */
@Composable
fun EditorScreen(
    state: EditorState, editing: Boolean, onChange: (FoodField, String) -> Unit,
    onSave: () -> Unit, onDelete: () -> Unit, onBack: () -> Unit, onRetry: () -> Unit,
    scan: PackageScanPrefill? = null, claimInitialFocus: () -> Boolean = { false },
    onChangeDate: (Long) -> Unit = {}, onDuplicate: (Long) -> Unit = {},
    onMatchAction: (String) -> Unit = {}, onInspectMatch: (String) -> Unit = {},
    onSelectMatch: (String) -> Unit = {}, onReuseMatch: (String) -> Unit = {},
    onRetention: (Retention) -> Unit = {}, onProductDetails: (ProductDetails) -> Unit = {},
) {
    CheckFoodScreen(
        state = state,
        editing = editing,
        onChange = onChange,
        onSave = onSave,
        onDelete = onDelete,
        onBack = onBack,
        onRetry = onRetry,
        scan = scan,
        claimInitialFocus = claimInitialFocus,
        onChangeDate = onChangeDate,
        onDuplicate = onDuplicate,
        onMatchAction = onMatchAction,
        onInspectMatch = onInspectMatch,
        onSelectMatch = onSelectMatch,
        onReuseMatch = onReuseMatch,
        onRetention = onRetention,
        onProductDetails = onProductDetails,
    )
}
