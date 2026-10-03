package com.justcal.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import androidx.datastore.preferences.core.emptyPreferences

private val Context.goalStore by preferencesDataStore("nutrition_preferences")

@Singleton
class GoalPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val goalKey = intPreferencesKey("daily_calorie_goal")
    val goal = context.goalStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { it[goalKey] ?: DEFAULT_GOAL }
    suspend fun setGoal(kcal: Int) {
        require(kcal in 1..100000)
        context.goalStore.edit { it[goalKey] = kcal }
    }
    companion object { const val DEFAULT_GOAL = 2000 }
}
