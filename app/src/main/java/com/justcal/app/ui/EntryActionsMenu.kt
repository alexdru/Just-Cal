package com.justcal.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.justcal.app.R

enum class EntryAction(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    DUPLICATE(R.string.duplicate_entry, R.drawable.ic_copy),
    CHANGE_DATE(R.string.change_entry_date, R.drawable.ic_edit_calendar),
    DELETE(R.string.delete_food, R.drawable.ic_delete),
}

/** Native Expressive popup and segmented items, shared by the editor and diary rows. */
@Composable
internal fun EntryActionsMenu(
    expanded: Boolean, onDismiss: () -> Unit, onAction: (EntryAction) -> Unit,
) {
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
            EntryAction.entries.forEachIndexed { index, action ->
                val colors = MenuDefaults.itemColors()
                DropdownMenuItem(
                    onClick = { onDismiss(); onAction(action) },
                    text = { Text(stringResource(action.label)) },
                    leadingIcon = { CalIcon(action.icon) },
                    shape = when (index) {
                        0 -> MenuDefaults.leadingItemShape
                        EntryAction.entries.lastIndex -> MenuDefaults.trailingItemShape
                        else -> MenuDefaults.middleItemShape
                    },
                    colors = if (action == EntryAction.DELETE) colors.copy(
                        textColor = MaterialTheme.colorScheme.error,
                        leadingIconColor = MaterialTheme.colorScheme.error,
                    ) else colors,
                )
            }
        }
    }
}

@Composable
internal fun EntryDeleteDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title)) },
        text = { Text(stringResource(R.string.delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
