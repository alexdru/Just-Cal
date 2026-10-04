package com.justcal.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justcal.app.data.GoalPreferences
import com.justcal.app.domain.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = DayViewModel.Factory::class)
class DayViewModel @AssistedInject constructor(
    repository: DiaryRepository,
    private val preferences: GoalPreferences,
    @Assisted private val dayEpoch: Long,
) : ViewModel() {
    private val date = LocalDate.ofEpochDay(dayEpoch)
    private val revision = MutableStateFlow(0)
    private val goalError = MutableStateFlow(false)
    private val entries = revision.flatMapLatest {
        repository.observeDay(dayEpoch).map { rows ->
            TodayState(date = date, entries = rows, totals = NutritionCalculator.total(rows),
                portions = rows.associate { it.id to NutritionCalculator.consumed(it.per100g, it.eatenGramsHundredths) },
                loading = false)
        }.onStart { emit(TodayState(date = date)) }.catch { error ->
            if (error is CancellationException) throw error
            emit(TodayState(date = date, loading = false, error = true))
        }
    }
    val state = combine(entries, preferences.settings, goalError) { diary, settings, error ->
        val goal = settings.goalKcal
        diary.copy(settings = settings, error = diary.error || error,
            remainingKcal = NutritionCalculator.remaining(diary.totals, goal),
            progress = diary.totals.energyKcal.divide(BigDecimal.valueOf(goal.toLong()), 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayState(date = date))
    fun retry() { revision.value += 1; goalError.value = false }
    fun setGoal(kcal: Int) {
        viewModelScope.launch {
            try { preferences.setGoal(kcal); goalError.value = false }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                goalError.value = true
            }
        }
    }
    @AssistedFactory interface Factory {
        fun create(dayEpoch: Long): DayViewModel
    }
}
