package com.justcal.app.domain

data class DiaryDay(val dayEpoch: Long, val entriesCount: Int, val totals: ConsumedNutrition)

object DiaryHistory {
    /** Calendar days are stored explicitly; grouping never reinterprets creation timestamps. */
    fun summarize(entries: List<DiaryEntry>): List<DiaryDay> =
        entries.groupBy { it.dayEpoch }.toSortedMap(compareByDescending { it })
            .map { (day, rows) -> DiaryDay(day, rows.size, NutritionCalculator.total(rows)) }
}
