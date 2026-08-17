package com.yizhidao

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class LunarTimeComponents(
    /** 地支数 1...12（子=1 ... 亥=12） */
    val yearBranch: Int,
    /** 农历月 1...12（闰月按所在月序） */
    val month: Int,
    /** 农历日 1...30 */
    val day: Int,
    /** 时辰地支数 1...12（子时=1 ... 亥时=12）；公历取数时为 1...24 */
    val hourBranch: Int,
)

/**
 * Supplies the Chinese civil date for an instant.
 * [cycleYear] is 1...60 in the current sexagenary cycle (甲子=1), matching
 * Foundation `Calendar(identifier: .chinese).year`.
 */
fun interface ChineseDateSource {
    data class CyclicYmd(val cycleYear: Int, val month: Int, val day: Int)

    fun cyclicYmd(instant: Instant, zone: ZoneId): CyclicYmd
}

object LunarCalendarHelper {
    private val branchNames = listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")

    fun branchName(n: Int): String =
        if (n in 1..12) branchNames[n - 1] else "?"

    fun yearBranchFromCycle(cycleYear: Int): Int = ((cycleYear - 1) % 12) + 1

    /**
     * 将公历小时 0...23 转为十二时辰序号（子=1 ... 亥=12）。
     * 子时跨日：23:00–00:59。
     */
    fun shichen(fromHour: Int): Int {
        val h = ((fromHour % 24) + 24) % 24
        if (h == 23 || h == 0) return 1
        return (h + 1) / 2 + 1
    }

    fun components(date: ZonedDateTime, chinese: ChineseDateSource): LunarTimeComponents {
        val ymd = chinese.cyclicYmd(date.toInstant(), date.zone)
        return LunarTimeComponents(
            yearBranch = yearBranchFromCycle(ymd.cycleYear),
            month = ymd.month,
            day = ymd.day,
            hourBranch = shichen(date.hour),
        )
    }

    /**
     * 公历取数：年支仍取干支年，月/日用公历，时用 1...24（0 点记为 24）。
     */
    fun solarComponents(date: ZonedDateTime, chinese: ChineseDateSource): LunarTimeComponents {
        val yearBranch = yearBranchFromCycle(
            chinese.cyclicYmd(date.toInstant(), date.zone).cycleYear,
        )
        val hour24 = date.hour
        return LunarTimeComponents(
            yearBranch = yearBranch,
            month = date.monthValue,
            day = date.dayOfMonth,
            hourBranch = if (hour24 == 0) 24 else hour24,
        )
    }

    fun summary(date: ZonedDateTime, chinese: ChineseDateSource): String {
        val c = components(date, chinese)
        val local = date.toLocalDateTime()
        val shi = "${branchName(c.hourBranch)}时(${c.hourBranch})"
        return "${local.year}/${local.monthValue}/${local.dayOfMonth} " +
            "%02d:%02d".format(local.hour, local.minute) +
            " · 农历${branchName(c.yearBranch)}年${c.month}月${c.day}日 $shi"
    }
}
