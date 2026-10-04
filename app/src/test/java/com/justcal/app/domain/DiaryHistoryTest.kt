package com.justcal.app.domain

import org.junit.Assert.*
import org.junit.Test

class DiaryHistoryTest {
    private fun entry(id: Long, day: Long, energy: Long, amount: Long, timestamp: Long) =
        DiaryEntry(id, "Food", NutritionPer100g(energy, 1234, 567, 2001), amount, day, timestamp)

    @Test fun groupsByRecordedCalendarDayAndSortsNewestFirst() {
        val days = DiaryHistory.summarize(listOf(
            entry(1, 20, 20550, 3750, 100), entry(2, 19, 10000, 10000, 999999), entry(3, 20, 20550, 5000, 1)))
        assertEquals(listOf(20L, 19L), days.map { it.dayEpoch })
        assertEquals(2, days.first().entriesCount)
        assertEquals("179.812500", days.first().totals.energyKcal.toPlainString())
        assertEquals("10.797500", days.first().totals.proteinGrams.toPlainString())
        assertEquals("4.961250", days.first().totals.fatGrams.toPlainString())
        assertEquals("17.508750", days.first().totals.carbsGrams.toPlainString())
    }

    @Test fun deletingLastEntryRemovesDayAndEditingUpdatesOnlyItsTotals() {
        val past = entry(1, 19, 10000, 10000, 1)
        val recent = entry(2, 20, 20000, 10000, 2)
        val edited = DiaryHistory.summarize(listOf(past, recent.copy(eatenGramsHundredths = 5000)))
        assertEquals("100", edited.first().totals.energyKcal.display(0))
        assertEquals("100", edited.last().totals.energyKcal.display(0))
        assertEquals(listOf(19L), DiaryHistory.summarize(listOf(past)).map { it.dayEpoch })
        assertTrue(DiaryHistory.summarize(emptyList()).isEmpty())
    }
}
