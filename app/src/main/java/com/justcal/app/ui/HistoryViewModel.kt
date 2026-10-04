package com.justcal.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justcal.app.domain.*
import com.justcal.app.data.GoalPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class HistoryState(
    val days: List<DiaryDay> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val loading: Boolean = true,
    val error: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(repository: DiaryRepository, preferences: GoalPreferences) : ViewModel() {
    private val revision = MutableStateFlow(0)
    private val entries = revision.flatMapLatest {
        repository.observeAll()
            .map { HistoryState(days = DiaryHistory.summarize(it), loading = false) }
            .onStart { emit(HistoryState()) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(HistoryState(loading = false, error = true))
            }
    }
    val state = combine(entries, preferences.settings) { diary, settings -> diary.copy(settings = settings) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryState())
    fun retry() { revision.value += 1 }
}
