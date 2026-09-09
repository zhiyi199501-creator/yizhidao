package com.yizhidao

import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

object MonthCastStats {
    data class Snapshot(
        val totalCount: Int,
        val elapsedDays: Int,
        val dailyAverage: Double,
        val countsByDay: Map<Int, Int>,
    ) {
        val formattedAverage: String get() = formatAverage(dailyAverage)
    }

    fun elapsedDays(yearMonth: YearMonth, now: Instant, zone: ZoneId): Int {
        val today = now.atZone(zone).toLocalDate()
        val current = YearMonth.from(today)
        return when {
            yearMonth == current -> minOf(today.dayOfMonth, yearMonth.lengthOfMonth())
            yearMonth < current -> yearMonth.lengthOfMonth()
            else -> 0
        }
    }

    fun snapshot(
        epochMs: List<Long>,
        yearMonth: YearMonth,
        zone: ZoneId,
        now: Instant = Instant.now(),
    ): Snapshot {
        val counts = linkedMapOf<Int, Int>()
        for (ms in epochMs) {
            val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
            if (date.year != yearMonth.year || date.monthValue != yearMonth.monthValue) continue
            counts[date.dayOfMonth] = (counts[date.dayOfMonth] ?: 0) + 1
        }
        val total = counts.values.sum()
        val days = elapsedDays(yearMonth, now, zone)
        val average = if (days == 0) 0.0 else total.toDouble() / days
        return Snapshot(
            totalCount = total,
            elapsedDays = days,
            dailyAverage = average,
            countsByDay = counts,
        )
    }

    /** Sunday-first month cells. `null` is a leading blank before day 1. */
    fun calendarDays(yearMonth: YearMonth): List<Int?> {
        val leading = yearMonth.atDay(1).dayOfWeek.value % 7
        return List(leading) { null } + (1..yearMonth.lengthOfMonth()).toList()
    }

    fun defaultSelectedDay(
        yearMonth: YearMonth,
        now: Instant,
        countsByDay: Map<Int, Int>,
        zone: ZoneId,
    ): Int {
        val today = now.atZone(zone).toLocalDate()
        val current = YearMonth.from(today)
        if (yearMonth == current) {
            return minOf(today.dayOfMonth, yearMonth.lengthOfMonth())
        }
        return countsByDay.keys.maxOrNull() ?: 1
    }

    fun formatAverage(value: Double): String {
        if (value == 0.0) return "0"
        val rounded = (value * 10).roundToInt() / 10.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", rounded)
        }
    }

    fun sundayOffset(dayOfWeek: DayOfWeek): Int = dayOfWeek.value % 7
}
