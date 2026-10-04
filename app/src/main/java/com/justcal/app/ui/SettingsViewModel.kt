package com.justcal.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justcal.app.data.GoalPreferences
import com.justcal.app.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsState(
    val settings: AppSettings = AppSettings(),
    val draft: SettingsDraft = SettingsDraft(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val submitted: Boolean = false,
    val saved: Boolean = false,
    val error: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: GoalPreferences,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private var dirty = savedState.get<Boolean>("settings_dirty") ?: false
    private val mutableState = MutableStateFlow(SettingsState(draft = SettingsDraft(
        savedState["profile_name"] ?: "", savedState["profile_goal"] ?: "2000",
        Appearance.entries.firstOrNull { it.name == savedState.get<String>("profile_appearance") } ?: Appearance.SYSTEM,
        savedState["profile_protein"] ?: "", savedState["profile_fat"] ?: "", savedState["profile_carbs"] ?: "",
        ColorStyle.entries.firstOrNull { it.name == savedState.get<String>("profile_color_style") } ?: ColorStyle.JUST_CAL,
    )))
    val state = mutableState.asStateFlow()
    val appearance = state.map { it.settings.appearance }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Appearance.SYSTEM)
    val colorStyle = state.map { it.settings.colorStyle }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ColorStyle.JUST_CAL)
    private var loadJob: Job? = null
    init { retry() }

    fun retry() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                preferences.settings.collect { settings ->
                    mutableState.update {
                        it.copy(settings = settings, draft = if (dirty) it.draft else SettingsDraft.from(settings),
                            loading = false, error = false)
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(loading = false, error = true) }
            }
        }
    }

    fun change(draft: SettingsDraft) {
        if (state.value.loading || state.value.busy) return
        dirty = true
        savedState["settings_dirty"] = true
        savedState["profile_name"] = draft.displayName
        savedState["profile_goal"] = draft.goal
        savedState["profile_appearance"] = draft.appearance.name
        savedState["profile_color_style"] = draft.colorStyle.name
        savedState["profile_protein"] = draft.proteinGoal
        savedState["profile_fat"] = draft.fatGoal
        savedState["profile_carbs"] = draft.carbsGoal
        mutableState.update { it.copy(draft = draft, saved = false, error = false) }
    }

    fun save() {
        if (state.value.loading || state.value.busy) return
        mutableState.update { it.copy(submitted = true, saved = false) }
        val settings = state.value.draft.validated() ?: return
        mutableState.update { it.copy(busy = true, error = false) }
        viewModelScope.launch {
            try {
                preferences.save(settings)
                dirty = false
                savedState["settings_dirty"] = false
                mutableState.update { it.copy(settings = settings, draft = SettingsDraft.from(settings),
                    busy = false, submitted = false, saved = true) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(busy = false, error = true) }
            }
        }
    }
}
