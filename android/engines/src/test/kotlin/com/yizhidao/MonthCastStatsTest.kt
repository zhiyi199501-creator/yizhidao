package com.yizhidao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class MonthCastStatsTest {
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun snapshotUsesElapsedCalendarDaysForAverage() {
        val ym = YearMonth.of(2026, 9)
        val now = ZonedDateTime.of(LocalDate.of(2026, 9, 7), LocalTime.of(15, 0), zone).toInstant()
        val dates = listOf(
            ms(2026, 8, 31, 23, 50),
            ms(2026, 9, 6, 10, 34),
            ms(2026, 9, 6, 10, 36),
            ms(2026, 9, 6, 10, 40),
            ms(2026, 9, 6, 11, 0),
            ms(2026, 9, 6, 12, 0),
            ms(2026, 9, 6, 13, 0),
            ms(2026, 9, 7, 10, 34),
        )
        val snap = MonthCastStats.snapshot(dates, ym, zone, now)
        assertEquals(7, snap.totalCount)
        assertEquals(7, snap.elapsedDays)
        assertEquals(6, snap.countsByDay[6])
        assertEquals(1, snap.countsByDay[7])
        assertEquals("1", snap.formattedAverage)
    }

    @Test
    fun pastMonthUsesFullMonthLength() {
        val now = ZonedDateTime.of(LocalDate.of(2026, 9, 7), LocalTime.of(15, 0), zone).toInstant()
        val snap = MonthCastStats.snapshot(
            listOf(ms(2026, 8, 3, 10, 0)),
            YearMonth.of(2026, 8),
            zone,
            now,
        )
        assertEquals(31, snap.elapsedDays)
        assertEquals("0", snap.formattedAverage)
    }

    @Test
    fun formatAverageKeepsOneDecimalWhenNeeded() {
        assertEquals("12.5", MonthCastStats.formatAverage(12.5))
        assertEquals("3", MonthCastStats.formatAverage(3.0))
        assertEquals("0", MonthCastStats.formatAverage(0.0))
    }

    @Test
    fun september2026CalendarStartsTuesday() {
        val days = MonthCastStats.calendarDays(YearMonth.of(2026, 9))
        assertEquals(listOf(null, null, 1, 2, 3), days.take(5))
        assertEquals(30, days.last())
        assertEquals(30, days.count { it != null })
        assertEquals(2, MonthCastStats.sundayOffset(DayOfWeek.TUESDAY))
    }

    @Test
    fun defaultSelectedDayPrefersTodayInCurrentMonth() {
        val now = ZonedDateTime.of(LocalDate.of(2026, 9, 7), LocalTime.of(15, 0), zone).toInstant()
        val day = MonthCastStats.defaultSelectedDay(
            YearMonth.of(2026, 9),
            now,
            mapOf(6 to 6),
            zone,
        )
        assertEquals(7, day)
    }

    @Test
    fun defaultSelectedDayFallsBackToLastActiveDay() {
        val now = ZonedDateTime.of(LocalDate.of(2026, 9, 7), LocalTime.of(15, 0), zone).toInstant()
        val day = MonthCastStats.defaultSelectedDay(
            YearMonth.of(2026, 8),
            now,
            mapOf(3 to 1, 18 to 2),
            zone,
        )
        assertEquals(18, day)
    }

    @Test
    fun yearMonthCanShift() {
        assertTrue(YearMonth.of(2026, 8) < YearMonth.of(2026, 9))
        assertEquals(YearMonth.of(2026, 9), YearMonth.of(2026, 8).plusMonths(1))
    }

    private fun ms(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hour, minute), zone)
            .toInstant()
            .toEpochMilli()
}
