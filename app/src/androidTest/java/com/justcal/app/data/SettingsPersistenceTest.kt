package com.justcal.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPersistenceTest {
    @Test fun profileAppearanceAndGoalAreSavedTogetherAndGoalUpdatesPreserveProfile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = GoalPreferences(context)
        val original = preferences.settings.first()
        try {
            val saved = AppSettings("Local profile", 2345, Appearance.DARK,
                proteinGoalGramsHundredths = 16000, fatGoalGramsHundredths = 6525, carbsGoalGramsHundredths = null)
            preferences.save(saved)
            val reopened = GoalPreferences(context)
            assertEquals(saved, reopened.settings.first())
            reopened.setGoal(3100)
            assertEquals(saved.copy(goalKcal = 3100), preferences.settings.first())
            val cleared = saved.copy(goalKcal = 3100, proteinGoalGramsHundredths = null, carbsGoalGramsHundredths = 23001)
            reopened.save(cleared)
            assertEquals(cleared, GoalPreferences(context).settings.first())
            reopened.save(cleared.copy(fatGoalGramsHundredths = null, carbsGoalGramsHundredths = null))
            assertEquals(AppSettings("Local profile", 3100, Appearance.DARK), preferences.settings.first())
        } finally {
            preferences.save(original)
        }
    }
}
