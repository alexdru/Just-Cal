package com.justcal.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justcal.app.domain.DiaryEntry
import com.justcal.app.domain.DiaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiaryEntryActionState(
    val entryId: Long? = null,
    val action: EntryAction? = null,
    val dayEpoch: Long? = null,
    val busy: Boolean = false,
    val error: Boolean = false,
    val result: EntryAction? = null,
)

@HiltViewModel
class DiaryEntryActionsViewModel @Inject constructor(
    private val repository: DiaryRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DiaryEntryActionState(
        entryId = savedState["actionEntryId"],
        action = savedState.get<String>("entryAction")?.let(EntryAction::valueOf),
        dayEpoch = savedState["actionDay"],
    ))
    val state = mutableState.asStateFlow()

    fun request(entry: DiaryEntry, action: EntryAction) {
        if (state.value.busy || state.value.result != null) return
        val day = if (action == EntryAction.DUPLICATE) {
            clock.instant().atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        } else entry.dayEpoch
        savedState["actionEntryId"] = entry.id
        savedState["entryAction"] = action.name
        savedState["actionDay"] = day
        mutableState.value = DiaryEntryActionState(entry.id, action, day)
    }

    fun dismiss() {
        if (state.value.busy) return
        clearPending()
        mutableState.value = DiaryEntryActionState()
    }

    fun confirm(day: Long? = state.value.dayEpoch) {
        val pending = state.value
        val id = pending.entryId ?: return
        val action = pending.action ?: return
        if (pending.busy || pending.result != null) return
        val target = requireNotNull(day)
        LocalDate.ofEpochDay(target)
        savedState["actionDay"] = target
        mutableState.value = pending.copy(dayEpoch = target, busy = true, error = false)
        viewModelScope.launch {
            try {
                when (action) {
                    EntryAction.DUPLICATE -> repository.duplicate(id, target, clock.millis())
                    EntryAction.CHANGE_DATE -> repository.changeDate(id, target)
                    EntryAction.DELETE -> repository.delete(id)
                }
                clearPending()
                mutableState.value = DiaryEntryActionState(result = action)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.value = mutableState.value.copy(busy = false, error = true)
            }
        }
    }

    fun acknowledgeResult() {
        mutableState.value = state.value.copy(result = null)
    }

    private fun clearPending() {
        savedState.remove<Long>("actionEntryId")
        savedState.remove<String>("entryAction")
        savedState.remove<Long>("actionDay")
    }
}
