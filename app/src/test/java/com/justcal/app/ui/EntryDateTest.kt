package com.justcal.app.ui

import java.time.LocalDate
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class EntryDateTest {
    @Test fun calendarDatesRoundTripWithoutLocalTimezoneOrDstShift() {
        val previous = TimeZone.getDefault()
        try {
            listOf("Pacific/Kiritimati", "America/Los_Angeles", "UTC", "Asia/Yekaterinburg").forEach { zone ->
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                listOf("2026-03-08", "2026-11-01", "2026-10-19", "0001-01-01", "9999-12-31").forEach {
                    val day = LocalDate.parse(it).toEpochDay()
                    assertEquals(day, pickerDay(pickerMillis(day)))
                }
            }
        } finally { TimeZone.setDefault(previous) }
    }
}
