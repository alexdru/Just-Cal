package com.justcal.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.justcal.app.domain.AppSettings
import com.justcal.app.domain.Appearance
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.goalStore by preferencesDataStore("nutrition_preferences")

@Singleton
class GoalPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val goalKey = intPreferencesKey("daily_calorie_goal")
    private val nameKey = stringPreferencesKey("display_name")
    private val appearanceKey = stringPreferencesKey("appearance")
    private val proteinKey = longPreferencesKey("protein_goal_grams_hundredths")
    private val fatKey = longPreferencesKey("fat_goal_grams_hundredths")
    private val carbsKey = longPreferencesKey("carbs_goal_grams_hundredths")
    val settings = context.goalStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { preferences ->
        AppSettings(
            displayName = preferences[nameKey].orEmpty(),
            goalKcal = preferences[goalKey] ?: DEFAULT_GOAL,
            appearance = Appearance.entries.firstOrNull { it.name == preferences[appearanceKey] } ?: Appearance.SYSTEM,
            proteinGoalGramsHundredths = preferences[proteinKey],
            fatGoalGramsHundredths = preferences[fatKey],
            carbsGoalGramsHundredths = preferences[carbsKey],
        )
    }
    val goal = settings.map { (_, goalKcal) -> goalKcal }
    suspend fun setGoal(kcal: Int) {
        require(kcal in (1..100000))
        context.goalStore.edit { it[goalKey] = kcal }
    }
    suspend fun save(settings: AppSettings) {
        require(com.justcal.app.domain.SettingsDraft.from(settings).validated() == settings)
        context.goalStore.edit {
            it[goalKey] = settings.goalKcal
            it[nameKey] = settings.displayName
            it[appearanceKey] = settings.appearance.name
            settings.proteinGoalGramsHundredths?.let { value -> it[proteinKey] = value } ?: it.remove(proteinKey)
            settings.fatGoalGramsHundredths?.let { value -> it[fatKey] = value } ?: it.remove(fatKey)
            settings.carbsGoalGramsHundredths?.let { value -> it[carbsKey] = value } ?: it.remove(carbsKey)
        }
    }
    companion object { const val DEFAULT_GOAL = 2000 }
}
