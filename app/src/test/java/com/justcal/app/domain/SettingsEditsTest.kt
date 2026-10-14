package com.justcal.app.domain

import org.junit.Assert.*
import org.junit.Test

class SettingsEditsTest {
    @Test fun incompleteRequiredAndDecimalValuesAreNotEligibleToPersist() {
        for (input in listOf("", "0", "-", "1.", "100001")) {
            assertFalse(SettingsDraft(goal = input).valid(SettingsField.CALORIES))
        }
        for (input in listOf("0", "-", "1,", "1.001")) {
            assertFalse(SettingsDraft(proteinGoal = input).valid(SettingsField.PROTEIN))
        }
    }

    @Test fun validIndependentEditsRemainAvailableWhileAnotherInputIsInvalid() {
        val draft = SettingsDraft(displayName = " Alex ", goal = "", proteinGoal = "1,", fatGoal = "65,25")
        assertTrue(draft.valid(SettingsField.NAME))
        assertTrue(draft.valid(SettingsField.FAT))
        assertFalse(draft.valid(SettingsField.CALORIES))
        assertFalse(draft.valid(SettingsField.PROTEIN))
        assertEquals("Alex", draft.validEditValues().displayName)
        assertEquals(6525L, draft.validEditValues().fatGoalGramsHundredths)
    }

    @Test fun optionalGoalsCanBeClearedWithoutZeroTargets() {
        val draft = SettingsDraft(proteinGoal = " ", carbsGoal = "230.01")
        assertTrue(draft.valid(SettingsField.PROTEIN))
        assertNull(draft.validEditValues().proteinGoalGramsHundredths)
        assertEquals(23001L, draft.validEditValues().carbsGoalGramsHundredths)
    }
}
