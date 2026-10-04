package com.justcal.app.domain

enum class Appearance { SYSTEM, LIGHT, DARK }

/** Local settings. Macro goals are nullable integer hundredths of a gram, never zero sentinels. */
data class AppSettings(
    val displayName: String = "",
    val goalKcal: Int = 2000,
    val appearance: Appearance = Appearance.SYSTEM,
    val proteinGoalGramsHundredths: Long? = null,
    val fatGoalGramsHundredths: Long? = null,
    val carbsGoalGramsHundredths: Long? = null,
)

data class SettingsDraft(
    val displayName: String = "",
    val goal: String = "2000",
    val appearance: Appearance = Appearance.SYSTEM,
    val proteinGoal: String = "",
    val fatGoal: String = "",
    val carbsGoal: String = "",
) {
    val nameValid get() = displayName.trim().length <= 80
    val calorieGoalValid get() = goal.trim().toIntOrNull()?.let { it in 1..100000 } == true
    val proteinGoalValid get() = optionalGoalValid(proteinGoal)
    val fatGoalValid get() = optionalGoalValid(fatGoal)
    val carbsGoalValid get() = optionalGoalValid(carbsGoal)

    fun validated(): AppSettings? {
        if (!nameValid || !calorieGoalValid || !proteinGoalValid || !fatGoalValid || !carbsGoalValid) return null
        return AppSettings(displayName.trim(), goal.trim().toInt(), appearance,
            parseGoal(proteinGoal), parseGoal(fatGoal), parseGoal(carbsGoal))
    }

    companion object {
        fun from(settings: AppSettings) = SettingsDraft(
            settings.displayName, settings.goalKcal.toString(), settings.appearance,
            settings.proteinGoalGramsHundredths?.hundredthsText().orEmpty(),
            settings.fatGoalGramsHundredths?.hundredthsText().orEmpty(),
            settings.carbsGoalGramsHundredths?.hundredthsText().orEmpty(),
        )

        private fun optionalGoalValid(text: String) = text.isBlank() || parseGoal(text) != null

        private fun parseGoal(input: String): Long? {
            val text = input.trim().replace(',', '.')
            if (!text.matches(Regex("[0-9]+([.][0-9]{1,2})?"))) return null
            val value = text.toBigDecimalOrNull() ?: return null
            if (value.signum() <= 0 || value > 100000.toBigDecimal()) return null
            return value.movePointRight(2).longValueExact()
        }
    }
}
