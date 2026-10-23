package com.justcal.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justcal.app.domain.*
import com.justcal.app.ocr.PackageScanPrefill
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorState(
    val draft: FoodDraft = FoodDraft(),
    val preview: ConsumedNutrition? = null,
    val errors: Map<FoodField, InputError> = emptyMap(),
    val loading: Boolean = false,
    val busy: Boolean = false,
    val completed: Boolean = false,
    val missing: Boolean = false,
    val storageError: Boolean = false,
    val dayEpoch: Long? = null,
    val duplicated: Boolean = false,
    val dateChanged: Boolean = false,
    val recognition: RecognitionDraft? = null,
)

@HiltViewModel(assistedFactory = EditorViewModel.Factory::class)
class EditorViewModel @AssistedInject constructor(
    private val repository: DiaryRepository,
    private val savedState: SavedStateHandle,
    private val clock: Clock,
    @Assisted("entryId") private val entryId: Long?,
    @Assisted("dayEpoch") private val dayEpoch: Long? = null,
    @Assisted("scan") private val scan: PackageScanPrefill? = null,
    private val diagnostics: com.justcal.app.diagnostics.OcrDiagnosticsRepository? = null,
    @Assisted("recognition") private val recognition: RecognitionDraft? = null,
) : ViewModel() {
    private var original: DiaryEntry? = null
    private fun restoredDraft(base: FoodDraft = recognition?.draft() ?: scan?.recognition?.draft() ?: scan?.label?.draft() ?: FoodDraft()) = FoodField.entries.fold(base) { draft, field ->
        draft.with(field, savedState[field.name] ?: draft.value(field))
    }
    private val mutableState = MutableStateFlow(EditorState(draft = restoredDraft(), loading = entryId != null, dayEpoch = dayEpoch,
        recognition = recognition ?: scan?.recognition))
    val state: StateFlow<EditorState> = mutableState.asStateFlow()

    init {
        updatePreview()
        if (entryId != null) load()
    }

    private fun load() {
        mutableState.update { it.copy(loading = true, storageError = false) }
        viewModelScope.launch {
            try {
                original = repository.get(requireNotNull(entryId))
                val draft = if (savedState.get<Boolean>("hasDraft") == true) restoredDraft(original?.let(FoodDraft::from) ?: FoodDraft())
                    else original?.let(FoodDraft::from) ?: FoodDraft()
                mutableState.update { it.copy(draft = draft, loading = false, missing = original == null, dayEpoch = savedState.get<Long>("entryDay") ?: original?.dayEpoch) }
                updatePreview()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(loading = false, storageError = true) }
            }
        }
    }

    fun retryLoad() { if (entryId != null && original == null) load() }

    /** Persist the one-shot event independently of composition, navigation and Activity recreation. */
    fun claimInitialFocus(): Boolean {
        if (savedState.get<Boolean>("initialFocusHandled") == true) return false
        savedState["initialFocusHandled"] = true
        return entryId == null && scan == null && savedState.get<Boolean>("hasDraft") != true && state.value.draft.name.isEmpty()
    }

    fun change(field: FoodField, value: String) {
        if (state.value.busy || state.value.loading) return
        savedState[field.name] = value
        savedState["hasDraft"] = true
        mutableState.update { it.copy(draft = it.draft.with(field, value), errors = it.errors - field, storageError = false) }
        updatePreview()
    }

    private fun updatePreview() {
        val food = FoodValidator.validate(state.value.draft).food
        mutableState.update { it.copy(preview = food?.let { input -> NutritionCalculator.consumed(input.per100g, input.eatenGramsHundredths) }) }
    }

    fun changeDate(day: Long) {
        if (original == null || state.value.busy || state.value.completed) return
        java.time.LocalDate.ofEpochDay(day)
        savedState["entryDay"] = day
        mutableState.update { it.copy(dayEpoch = day) }
    }

    fun duplicate(day: Long) {
        val id = original?.id ?: return
        if (state.value.busy || state.value.completed || state.value.duplicated) return
        java.time.LocalDate.ofEpochDay(day)
        mutableState.update { it.copy(busy = true, storageError = false) }
        viewModelScope.launch {
            try {
                repository.duplicate(id, day, clock.millis())
                mutableState.update { it.copy(busy = false, duplicated = true) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(busy = false, storageError = true) }
            }
        }
    }

    fun acknowledgeDuplicate() { mutableState.update { it.copy(duplicated = false) } }

    fun save() {
        if (state.value.busy || state.value.loading || state.value.completed || state.value.missing) return
        if (entryId != null && original == null) return
        val validated = FoodValidator.validate(state.value.draft)
        mutableState.update { it.copy(errors = validated.errors) }
        val food = validated.food ?: return
        mutableState.update { it.copy(busy = true, storageError = false) }
        viewModelScope.launch {
            try {
                val entry = DiaryEntry(
                    id = original?.id ?: 0,
                    name = food.name,
                    per100g = food.per100g,
                    eatenGramsHundredths = food.eatenGramsHundredths,
                    dayEpoch = state.value.dayEpoch ?: dayEpoch ?: clock.instant().atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay(),
                    createdAtMillis = original?.createdAtMillis ?: clock.millis(),
                    source = original?.source ?: if (scan != null) EntrySource.PACKAGE_SCAN else EntrySource.MANUAL,
                )
                repository.save(entry)
                scan?.let { diagnostics?.corrections(it, state.value.draft) }
                mutableState.update { it.copy(busy = false, completed = true,
                    dateChanged = original != null && entry.dayEpoch != original?.dayEpoch) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(busy = false, storageError = true) }
            }
        }
    }

    fun delete() {
        val id = original?.id ?: return
        if (state.value.busy || state.value.completed) return
        mutableState.update { it.copy(busy = true, storageError = false) }
        viewModelScope.launch {
            try {
                repository.delete(id)
                mutableState.update { it.copy(busy = false, completed = true) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(busy = false, storageError = true) }
            }
        }
    }

    @AssistedFactory interface Factory { fun create(@Assisted("entryId") entryId: Long?, @Assisted("dayEpoch") dayEpoch: Long?, @Assisted("scan") scan: PackageScanPrefill?, @Assisted("recognition") recognition: RecognitionDraft? = null): EditorViewModel }
}
