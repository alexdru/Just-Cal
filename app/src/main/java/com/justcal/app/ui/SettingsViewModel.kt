package com.justcal.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justcal.app.data.GoalPreferences
import com.justcal.app.domain.*
import com.justcal.app.ui.theme.CalMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    private val restored = savedState.get<Boolean>("settings_dirty") == true
    private val pending = mutableSetOf<SettingsField>().apply {
        if (restored) {
            val names = savedState.get<ArrayList<String>>("settings_fields")
            addAll(names?.mapNotNull { name -> SettingsField.entries.find { it.name == name } }
                ?: SettingsField.entries)
        }
    }
    private val mutableState = MutableStateFlow(SettingsState(draft = SettingsDraft(
        savedState["profile_name"] ?: "", savedState["profile_goal"] ?: "2000",
        proteinGoal = savedState["profile_protein"] ?: "",
        fatGoal = savedState["profile_fat"] ?: "", carbsGoal = savedState["profile_carbs"] ?: "",
    )))
    val state = mutableState.asStateFlow()

    private sealed interface Write {
        data class Brightness(val value: Appearance) : Write
        data class Palette(val value: ColorStyle) : Write
        data class Edits(val draft: SettingsDraft, val fields: Set<SettingsField>) : Write
    }
    private val writes = Channel<Write>(Channel.UNLIMITED)
    private var debounce: Job? = null
    private var loadJob: Job? = null
    private var pendingAppearance: Appearance? = null
    private var pendingStyle: ColorStyle? = null

    init {
        // A single writer preserves selection order; editable writes never touch appearance keys.
        viewModelScope.launch {
            for (write in writes) {
                // Replace obsolete queued selections; an in-flight DataStore edit finishes first.
                if (write is Write.Brightness && write.value != state.value.draft.appearance) continue
                if (write is Write.Palette && write.value != state.value.draft.colorStyle) continue
                mutableState.update { it.copy(busy = true) }
                try {
                    when (write) {
                        is Write.Brightness -> {
                            preferences.setAppearance(write.value)
                            if (pendingAppearance == write.value && state.value.draft.appearance == write.value) pendingAppearance = null
                        }
                        is Write.Palette -> {
                            preferences.setColorStyle(write.value)
                            if (pendingStyle == write.value && state.value.draft.colorStyle == write.value) pendingStyle = null
                        }
                        is Write.Edits -> {
                            preferences.saveEdits(write.draft, write.fields)
                            pending.removeAll { it in write.fields && state.value.draft.text(it) == write.draft.text(it) }
                            savedState["settings_dirty"] = pending.isNotEmpty()
                            savedState["settings_fields"] = ArrayList(pending.map { it.name })
                        }
                    }
                    mutableState.update { it.copy(busy = false, error = false, saved = pending.isEmpty()) }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    mutableState.update { it.copy(busy = false, error = true, saved = false) }
                }
            }
        }
        retry()
    }

    fun retry() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                preferences.settings.collect { settings ->
                    mutableState.update { previous ->
                        val stored = SettingsDraft.from(settings)
                        val normalized = SettingsDraft.from(previous.draft.validEditValues())
                        val previousStored = SettingsDraft.from(previous.settings)
                        fun text(field: SettingsField): String {
                            // Unrelated-key emissions may arrive after a write completes but before
                            // its own echo. They must not roll back optimistic text or normalize it.
                            val unchanged = !previous.loading && previousStored.text(field) == stored.text(field)
                            val ownValue = previous.draft.valid(field) && normalized.text(field) == stored.text(field)
                            return if (field in pending || unchanged || ownValue) previous.draft.text(field) else stored.text(field)
                        }
                        val hasDraft = savedState.contains("profile_goal") || savedState.contains("profile_name")
                        val draft = if (previous.loading && !hasDraft) stored else previous.draft.copy(
                            displayName = text(SettingsField.NAME),
                            goal = text(SettingsField.CALORIES),
                            proteinGoal = text(SettingsField.PROTEIN),
                            fatGoal = text(SettingsField.FAT),
                            carbsGoal = text(SettingsField.CARBS),
                            appearance = if (previous.loading) settings.appearance else previous.draft.appearance,
                            colorStyle = if (previous.loading) settings.colorStyle else previous.draft.colorStyle,
                        )
                        previous.copy(settings = settings, draft = draft, loading = false)
                    }
                    if (restored && debounce == null && pending.isNotEmpty()) scheduleEdits()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(loading = false, error = true) }
            }
        }
        if (pendingAppearance != null) writes.trySend(Write.Brightness(pendingAppearance!!))
        if (pendingStyle != null) writes.trySend(Write.Palette(pendingStyle!!))
        if (pending.isNotEmpty()) scheduleEdits()
    }

    fun change(draft: SettingsDraft) {
        val previous = state.value
        if (previous.loading) return
        SettingsField.entries.filterTo(pending) { previous.draft.text(it) != draft.text(it) }
        savedState["settings_dirty"] = pending.isNotEmpty()
        savedState["settings_fields"] = ArrayList(pending.map { it.name })
        savedState["profile_name"] = draft.displayName
        savedState["profile_goal"] = draft.goal
        savedState["profile_protein"] = draft.proteinGoal
        savedState["profile_fat"] = draft.fatGoal
        savedState["profile_carbs"] = draft.carbsGoal
        // Main.immediate may resume the writer inside trySend: publish the intent first.
        mutableState.update { it.copy(draft = draft, saved = false, error = false, submitted = true) }
        if (draft.appearance != previous.draft.appearance) {
            pendingAppearance = draft.appearance
            writes.trySend(Write.Brightness(draft.appearance))
        }
        if (draft.colorStyle != previous.draft.colorStyle) {
            pendingStyle = draft.colorStyle
            writes.trySend(Write.Palette(draft.colorStyle))
        }
        scheduleEdits()
    }

    fun flushEdits() {
        if (state.value.loading) return
        debounce?.cancel()
        val draft = state.value.draft
        val fields = pending.filterTo(mutableSetOf()) { draft.valid(it) }
        if (fields.isNotEmpty()) writes.trySend(Write.Edits(draft, fields))
    }

    private fun scheduleEdits() {
        debounce?.cancel()
        debounce = viewModelScope.launch {
            delay(CalMotion.debounceMillis)
            val draft = state.value.draft
            val fields = pending.filterTo(mutableSetOf()) { draft.valid(it) }
            if (fields.isNotEmpty()) writes.send(Write.Edits(draft, fields))
        }
    }
}
