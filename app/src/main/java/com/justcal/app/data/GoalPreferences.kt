package com.justcal.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.justcal.app.domain.valid
import com.justcal.app.domain.validEditValues
import com.justcal.app.domain.AppSettings
import com.justcal.app.domain.Appearance
import com.justcal.app.domain.ColorStyle
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
    private val colorStyleKey = stringPreferencesKey("color_style")
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
            colorStyle = ColorStyle.entries.firstOrNull { it.name == preferences[colorStyleKey] } ?: ColorStyle.JUST_CAL,
        )
    }
    val aiModels = context.goalStore.data.map { preferences ->
        mapOf("package" to preferences[stringPreferencesKey("ai_package_model")],
            "meal" to preferences[stringPreferencesKey("ai_meal_model")])
    }
    suspend fun selectAiModel(task: String, modelId: String?) {
        require(task == "package" || task == "meal")
        context.goalStore.edit { preferences ->
            val key = stringPreferencesKey("ai_${task}_model")
            if (modelId == null) preferences.remove(key) else preferences[key] = modelId
        }
    }
    suspend fun clearAiModel(modelId: String) {
        context.goalStore.edit { preferences ->
            listOf("package", "meal").forEach { task ->
                val key = stringPreferencesKey("ai_${task}_model")
                if (preferences[key] == modelId) preferences.remove(key)
            }
        }
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
            it[colorStyleKey] = settings.colorStyle.name
            settings.proteinGoalGramsHundredths?.let { value -> it[proteinKey] = value } ?: it.remove(proteinKey)
            settings.fatGoalGramsHundredths?.let { value -> it[fatKey] = value } ?: it.remove(fatKey)
            settings.carbsGoalGramsHundredths?.let { value -> it[carbsKey] = value } ?: it.remove(carbsKey)
        }
    }
    suspend fun setAppearance(value: Appearance) {
        context.goalStore.edit { it[appearanceKey] = value.name }
    }

    suspend fun setColorStyle(value: ColorStyle) {
        context.goalStore.edit { it[colorStyleKey] = value.name }
    }

    /** Each transaction touches only edited valid keys, preserving concurrent unrelated updates. */
    suspend fun saveEdits(draft: com.justcal.app.domain.SettingsDraft, fields: Set<com.justcal.app.domain.SettingsField>) {
        require(fields.all { draft.valid(it) })
        val values = draft.validEditValues()
        context.goalStore.edit { preferences ->
            fields.forEach { field ->
                when (field) {
                    com.justcal.app.domain.SettingsField.NAME -> preferences[nameKey] = values.displayName
                    com.justcal.app.domain.SettingsField.CALORIES -> preferences[goalKey] = values.goalKcal
                    com.justcal.app.domain.SettingsField.PROTEIN -> values.proteinGoalGramsHundredths?.let { preferences[proteinKey] = it } ?: preferences.remove(proteinKey)
                    com.justcal.app.domain.SettingsField.FAT -> values.fatGoalGramsHundredths?.let { preferences[fatKey] = it } ?: preferences.remove(fatKey)
                    com.justcal.app.domain.SettingsField.CARBS -> values.carbsGoalGramsHundredths?.let { preferences[carbsKey] = it } ?: preferences.remove(carbsKey)
                }
            }
        }
    }

    companion object { const val DEFAULT_GOAL = 2000 }
}
