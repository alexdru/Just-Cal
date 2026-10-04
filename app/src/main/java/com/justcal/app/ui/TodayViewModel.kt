package com.justcal.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justcal.app.data.GoalPreferences
import com.justcal.app.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class TodayState(
    val date: LocalDate = LocalDate.now(),
    val entries: List<DiaryEntry> = emptyList(),
    val totals: ConsumedNutrition = ConsumedNutrition(),
    val settings: AppSettings = AppSettings(),
    val portions: Map<Long, ConsumedNutrition> = emptyMap(),
    val remainingKcal: BigDecimal = BigDecimal.valueOf(GoalPreferences.DEFAULT_GOAL.toLong()),
    val progress: Float = 0f,
    val loading: Boolean = true,
    val error: Boolean = false,
) {
    val goalKcal get() = settings.goalKcal
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: DiaryRepository,
    private val preferences: GoalPreferences,
    private val clock: Clock,
) : ViewModel() {
    private val day = MutableStateFlow(currentDate())
    private val revision = MutableStateFlow(0)
    private val goalError = MutableStateFlow(false)
    private val dates = merge(day, flow {
        while (currentCoroutineContext().isActive) {
            emit(currentDate())
            delay(30_000)
        }
    }).distinctUntilChanged()
    private val entries = combine(dates, revision) { date, _ -> date }.flatMapLatest { date ->
        repository.observeDay(date.toEpochDay())
            .map { rows ->
                TodayState(date = date, entries = rows, totals = NutritionCalculator.total(rows),
                    portions = rows.associate { it.id to NutritionCalculator.consumed(it.per100g, it.eatenGramsHundredths) },
                    loading = false)
            }
            .onStart { emit(TodayState(date = date)) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(TodayState(date = date, loading = false, error = true))
            }
    }
    val state = combine(entries, preferences.settings, goalError) { diary, settings, error ->
        val goal = settings.goalKcal
        diary.copy(settings = settings, error = diary.error || error,
            remainingKcal = NutritionCalculator.remaining(diary.totals, goal),
            progress = diary.totals.energyKcal.divide(BigDecimal.valueOf(goal.toLong()), 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayState(date = day.value))

    private fun currentDate() = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()
    fun refreshDate() { day.value = currentDate() }
    fun retry() { goalError.value = false; revision.value += 1 }
    fun setGoal(kcal: Int) {
        viewModelScope.launch {
            try { preferences.setGoal(kcal); goalError.value = false }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                goalError.value = true
            }
        }
    }
}
