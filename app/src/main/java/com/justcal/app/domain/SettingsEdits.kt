package com.justcal.app.domain

enum class SettingsField { NAME, CALORIES, PROTEIN, FAT, CARBS }

fun SettingsDraft.text(field: SettingsField): String = when (field) {
    SettingsField.NAME -> displayName
    SettingsField.CALORIES -> goal
    SettingsField.PROTEIN -> proteinGoal
    SettingsField.FAT -> fatGoal
    SettingsField.CARBS -> carbsGoal
}

fun SettingsDraft.valid(field: SettingsField): Boolean = when (field) {
    SettingsField.NAME -> nameValid
    SettingsField.CALORIES -> calorieGoalValid
    SettingsField.PROTEIN -> proteinGoalValid
    SettingsField.FAT -> fatGoalValid
    SettingsField.CARBS -> carbsGoalValid
}

/** Parse valid fields independently; an unfinished calorie input must not block a name edit. */
fun SettingsDraft.validEditValues(): AppSettings = copy(
    displayName = displayName.takeIf { nameValid }.orEmpty(),
    goal = goal.takeIf { calorieGoalValid } ?: "2000",
    proteinGoal = proteinGoal.takeIf { proteinGoalValid }.orEmpty(),
    fatGoal = fatGoal.takeIf { fatGoalValid }.orEmpty(),
    carbsGoal = carbsGoal.takeIf { carbsGoalValid }.orEmpty(),
).validated()!!
