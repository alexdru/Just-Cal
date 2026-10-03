package com.justcal.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Input units are hundredths of kcal or grams, always per 100 grams of food. */
data class NutritionPer100g(
    val energyKcalHundredths: Long,
    val proteinGramsHundredths: Long,
    val fatGramsHundredths: Long,
    val carbsGramsHundredths: Long,
) {
    init {
        require(listOf(energyKcalHundredths, proteinGramsHundredths, fatGramsHundredths, carbsGramsHundredths).all { it >= 0 })
    }
}

data class ConsumedNutrition(
    val energyKcal: BigDecimal = BigDecimal.ZERO,
    val proteinGrams: BigDecimal = BigDecimal.ZERO,
    val fatGrams: BigDecimal = BigDecimal.ZERO,
    val carbsGrams: BigDecimal = BigDecimal.ZERO,
) {
    operator fun plus(other: ConsumedNutrition) = ConsumedNutrition(
        energyKcal + other.energyKcal, proteinGrams + other.proteinGrams,
        fatGrams + other.fatGrams, carbsGrams + other.carbsGrams,
    )
}

object NutritionCalculator {
    fun remaining(total: ConsumedNutrition, goalKcal: Int): BigDecimal {
        require(goalKcal > 0)
        return BigDecimal.valueOf(goalKcal.toLong()) - total.energyKcal
    }

    fun consumed(per100g: NutritionPer100g, eatenGramsHundredths: Long): ConsumedNutrition {
        require(eatenGramsHundredths > 0)
        // Two hundredths scales and the per-100 denominator: divide by 1,000,000.
        fun value(input: Long) = BigDecimal.valueOf(input)
            .multiply(BigDecimal.valueOf(eatenGramsHundredths)).movePointLeft(6)
        return ConsumedNutrition(
            value(per100g.energyKcalHundredths), value(per100g.proteinGramsHundredths),
            value(per100g.fatGramsHundredths), value(per100g.carbsGramsHundredths),
        )
    }

    fun total(entries: List<DiaryEntry>): ConsumedNutrition =
        entries.fold(ConsumedNutrition()) { total, entry -> total + consumed(entry.per100g, entry.eatenGramsHundredths) }
}

fun BigDecimal.display(scale: Int): String = setScale(scale, RoundingMode.HALF_UP).toPlainString()
fun Long.hundredthsText(): String = BigDecimal.valueOf(this, 2).stripTrailingZeros().toPlainString()

enum class EntrySource { MANUAL, PACKAGE_SCAN, MEAL_SCAN }

data class DiaryEntry(
    val id: Long = 0,
    val name: String,
    val per100g: NutritionPer100g,
    val eatenGramsHundredths: Long,
    val dayEpoch: Long,
    val createdAtMillis: Long,
    val source: EntrySource = EntrySource.MANUAL,
) {
    init {
        require(name.isNotBlank())
        require(eatenGramsHundredths > 0)
    }
}
