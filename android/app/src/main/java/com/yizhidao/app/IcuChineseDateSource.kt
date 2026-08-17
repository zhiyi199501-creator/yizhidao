package com.yizhidao.app

import android.icu.util.Calendar
import android.icu.util.ChineseCalendar
import android.icu.util.TimeZone
import com.yizhidao.ChineseDateSource
import java.time.Instant
import java.time.ZoneId

/** Uses system ICU, matching iOS `Calendar(identifier: .chinese)` for civil dates. */
class IcuChineseDateSource : ChineseDateSource {
    override fun cyclicYmd(instant: Instant, zone: ZoneId): ChineseDateSource.CyclicYmd {
        val cal = ChineseCalendar()
        cal.timeZone = TimeZone.getTimeZone(zone.id)
        cal.timeInMillis = instant.toEpochMilli()
        val cycleYear = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return ChineseDateSource.CyclicYmd(
            cycleYear = cycleYear,
            month = month,
            day = day,
        )
    }
}
