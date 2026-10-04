package com.justcal.app.domain

import org.junit.Assert.*
import org.junit.Test

class SettingsDraftTest {
    @Test fun optionalLocalNameAndWholeCalorieGoalAreValidated() {
        assertEquals(AppSettings("", 1, Appearance.DARK), SettingsDraft("  ", " 1 ", Appearance.DARK).validated())
        assertEquals("Alex", SettingsDraft(" Alex ", "100000").validated()!!.displayName)
        assertNull(SettingsDraft(goal = "2000.5").validated())
        assertNull(SettingsDraft(goal = "0").validated())
        assertNull(SettingsDraft(goal = "100001").validated())
        assertNull(SettingsDraft(goal = "99999999999999999999").validated())
        assertNull(SettingsDraft(displayName = "x".repeat(81)).validated())
    }

    @Test fun goalsAreIndependentlyOptionalAndClearingOnePreservesTheOthers() {
        val onlyProtein = SettingsDraft(goal = "2200", proteinGoal = "160").validated()!!
        assertEquals(16000L, onlyProtein.proteinGoalGramsHundredths)
        assertNull(onlyProtein.fatGoalGramsHundredths)
        assertNull(onlyProtein.carbsGoalGramsHundredths)
        assertEquals(AppSettings(goalKcal = 2200), SettingsDraft(goal = "2200").validated())

        val all = SettingsDraft(proteinGoal = "160", fatGoal = "65,25", carbsGoal = "230.01").validated()!!
        assertEquals(6525L, all.fatGoalGramsHundredths)
        assertEquals(23001L, all.carbsGoalGramsHundredths)
        assertEquals(all.copy(fatGoalGramsHundredths = null), SettingsDraft.from(all).copy(fatGoal = " ").validated())
        assertEquals(all.copy(proteinGoalGramsHundredths = null), SettingsDraft.from(all).copy(proteinGoal = "").validated())
        assertEquals(all.copy(carbsGoalGramsHundredths = null), SettingsDraft.from(all).copy(carbsGoal = "").validated())
        assertEquals(onlyProtein, SettingsDraft.from(onlyProtein).validated())
        val onlyFat = SettingsDraft(fatGoal = "65,25").validated()!!
        assertEquals(6525L, onlyFat.fatGoalGramsHundredths)
        assertNull(onlyFat.proteinGoalGramsHundredths)
        assertNull(onlyFat.carbsGoalGramsHundredths)
        val onlyCarbs = SettingsDraft(carbsGoal = "230.01").validated()!!
        assertEquals(23001L, onlyCarbs.carbsGoalGramsHundredths)
        assertNull(onlyCarbs.proteinGoalGramsHundredths)
        assertNull(onlyCarbs.fatGoalGramsHundredths)
    }

    @Test fun eachMacroRejectsInvalidValuesWithoutTreatingZeroAsAbsent() {
        for (invalid in listOf("-1", "0", "1.001", "100001", "1e2", "NaN", "9999999999999999999999999")) {
            assertNull(SettingsDraft(proteinGoal = invalid).validated())
            assertNull(SettingsDraft(fatGoal = invalid).validated())
            assertNull(SettingsDraft(carbsGoal = invalid).validated())
        }
        assertEquals(1L, SettingsDraft(proteinGoal = " 0,01 ").validated()!!.proteinGoalGramsHundredths)
    }

    @Test fun brightnessAndPaletteRoundTripIndependentlyWithoutChangingGoals() {
        val original = AppSettings("Alex", 2300, Appearance.DARK,
            proteinGoalGramsHundredths = 12345, colorStyle = ColorStyle.MATERIAL_YOU)
        val draft = SettingsDraft.from(original)
        assertEquals(original, draft.validated())
        assertEquals(original.copy(appearance = Appearance.LIGHT), draft.copy(appearance = Appearance.LIGHT).validated())
        assertEquals(original.copy(colorStyle = ColorStyle.JUST_CAL), draft.copy(colorStyle = ColorStyle.JUST_CAL).validated())
        assertEquals(ColorStyle.JUST_CAL, AppSettings().colorStyle)
    }

    @Test fun loadingExistingPreferencesDoesNotChangeTheirValues() {
        val original = AppSettings("Алекс", 2300, Appearance.LIGHT)
        assertEquals(original, SettingsDraft.from(original).validated())
    }
}
