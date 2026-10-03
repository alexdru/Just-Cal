package com.justcal.app.domain

enum class FoodField { NAME, ENERGY, PROTEIN, FAT, CARBS, AMOUNT }
enum class InputError { REQUIRED, NUMBER, PRECISION, NON_NEGATIVE, POSITIVE, TOO_LARGE }

data class FoodDraft(
    val name: String = "",
    val energy: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = "",
    val amount: String = "100",
) {
    fun value(field: FoodField): String = when (field) {
        FoodField.NAME -> name; FoodField.ENERGY -> energy; FoodField.PROTEIN -> protein
        FoodField.FAT -> fat; FoodField.CARBS -> carbs; FoodField.AMOUNT -> amount
    }
    fun with(field: FoodField, text: String) = when (field) {
        FoodField.NAME -> copy(name = text); FoodField.ENERGY -> copy(energy = text)
        FoodField.PROTEIN -> copy(protein = text); FoodField.FAT -> copy(fat = text)
        FoodField.CARBS -> copy(carbs = text); FoodField.AMOUNT -> copy(amount = text)
    }

    companion object {
        fun from(entry: DiaryEntry) = FoodDraft(
            entry.name, entry.per100g.energyKcalHundredths.hundredthsText(),
            entry.per100g.proteinGramsHundredths.hundredthsText(),
            entry.per100g.fatGramsHundredths.hundredthsText(),
            entry.per100g.carbsGramsHundredths.hundredthsText(),
            entry.eatenGramsHundredths.hundredthsText(),
        )
    }
}

data class ValidFood(val name: String, val per100g: NutritionPer100g, val eatenGramsHundredths: Long)
data class FoodValidation(val food: ValidFood?, val errors: Map<FoodField, InputError>)

object FoodValidator {
    fun validate(draft: FoodDraft): FoodValidation {
        val errors = mutableMapOf<FoodField, InputError>()
        if (draft.name.isBlank()) errors[FoodField.NAME] = InputError.REQUIRED
        fun number(field: FoodField): Long? {
            val text = draft.value(field).trim().replace(',', '.')
            val value = text.toBigDecimalOrNull()
            val error = when {
                text.isEmpty() -> InputError.REQUIRED
                !text.matches(Regex("-?[0-9]+([.][0-9]+)?")) || value == null -> InputError.NUMBER
                value.scale() > 2 -> InputError.PRECISION
                value.signum() < 0 -> InputError.NON_NEGATIVE
                field == FoodField.AMOUNT && value.signum() == 0 -> InputError.POSITIVE
                else -> null
            }
            if (error != null) { errors[field] = error; return null }
            return try { requireNotNull(value).movePointRight(2).longValueExact() }
            catch (_: ArithmeticException) { errors[field] = InputError.TOO_LARGE; null }
        }
        val energy = number(FoodField.ENERGY)
        val protein = number(FoodField.PROTEIN)
        val fat = number(FoodField.FAT)
        val carbs = number(FoodField.CARBS)
        val amount = number(FoodField.AMOUNT)
        return FoodValidation(
            if (errors.isEmpty()) ValidFood(draft.name.trim(), NutritionPer100g(energy!!, protein!!, fat!!, carbs!!), amount!!) else null,
            errors,
        )
    }
}
