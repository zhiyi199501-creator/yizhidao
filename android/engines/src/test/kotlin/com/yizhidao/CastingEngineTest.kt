package com.yizhidao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class DigitalCastingEngineTest {
    @Test
    fun modRules() {
        assertEquals(8, DigitalCastingEngine.mod8(8))
        assertEquals(8, DigitalCastingEngine.mod8(16))
        assertEquals(6, DigitalCastingEngine.mod8(22))
        assertEquals(2, DigitalCastingEngine.mod8(42))
        assertEquals(6, DigitalCastingEngine.mod6(42))
        assertEquals(6, DigitalCastingEngine.mod6(6))
        assertEquals(1, DigitalCastingEngine.mod6(7))
    }

    /** 壬寅年十二月初七 戌时(11)：上坎下乾、三爻动 → 需卦 */
    @Test
    fun timeCastWithShichen() {
        val result = DigitalCastingEngine.castTime(
            yearBranch = 3,
            month = 12,
            day = 7,
            hour = 11,
        )
        assertEquals(5, result.primaryNumber)
        assertEquals(listOf(3), result.movingPositions)
        assertEquals(60, result.resultingNumber)
    }

    @Test
    fun threeNumbersUpperLowerMoving() {
        val result = DigitalCastingEngine.cast(number1 = 6, number2 = 2, number3 = 6)
        assertEquals(60, result.primaryNumber)
        assertEquals(listOf(6), result.movingPositions)
    }
}

class LunarCalendarHelperTest {
    @Test
    fun shichenFromHour() {
        assertEquals(1, LunarCalendarHelper.shichen(23))
        assertEquals(1, LunarCalendarHelper.shichen(0))
        assertEquals(2, LunarCalendarHelper.shichen(1))
        assertEquals(5, LunarCalendarHelper.shichen(8))
        assertEquals(11, LunarCalendarHelper.shichen(20))
        assertEquals(12, LunarCalendarHelper.shichen(22))
    }

    @Test
    fun solarComponentsUsesGregorianMonthDayAndHour24() {
        val zone = ZoneOffset.ofHours(8)
        val chinese = ChineseDateSource { _, _ -> ChineseDateSource.CyclicYmd(1, 1, 1) }

        val date = ZonedDateTime.of(2026, 8, 10, 16, 0, 0, 0, zone)
        val solar = LunarCalendarHelper.solarComponents(date, chinese)
        assertEquals(8, solar.month)
        assertEquals(10, solar.day)
        assertEquals(16, solar.hourBranch)

        val midnight = ZonedDateTime.of(2026, 8, 10, 0, 0, 0, 0, zone)
        assertEquals(
            24,
            LunarCalendarHelper.solarComponents(midnight, chinese).hourBranch,
        )
    }
}

class CoinCastingEngineTest {
    @Test
    fun lineMapping() {
        assertEquals(LineValue.OLD_YANG, CoinCastingEngine.line(3))
        assertEquals(LineValue.OLD_YIN, CoinCastingEngine.line(0))
        assertEquals(LineValue.YOUNG_YANG, CoinCastingEngine.line(1))
        assertEquals(LineValue.YOUNG_YIN, CoinCastingEngine.line(2))
    }

    @Test
    fun tossFacesMapToLine() {
        val allYang = CoinToss(listOf(true, true, true))
        assertEquals(3, allYang.yangCount)
        assertEquals(LineValue.OLD_YANG, allYang.line)
        val twoYang = CoinToss(listOf(true, false, true))
        assertEquals(LineValue.YOUNG_YIN, twoYang.line)
    }

    @Test
    fun allYangHexagramIsQian() {
        val lines = List(6) { LineValue.YOUNG_YANG }
        val result = CoinCastingEngine.cast(lines)
        assertEquals(1, result.primaryNumber)
        assertTrue(result.movingPositions.isEmpty())
        assertEquals(null, result.resultingNumber)
    }

    @Test
    fun changingLineProducesResulting() {
        val lines = MutableList(6) { LineValue.YOUNG_YANG }
        lines[0] = LineValue.OLD_YANG
        val result = CoinCastingEngine.cast(lines)
        assertEquals(1, result.primaryNumber)
        assertEquals(listOf(1), result.movingPositions)
        assertEquals(44, result.resultingNumber)
    }
}

class KingWenTableTest {
    @Test
    fun sixtyFourUnique() {
        val seen = mutableSetOf<String>()
        for (n in 1..64) {
            val b = KingWenTable.binary(n)
            assertEquals(6, b.length)
            assertTrue(b !in seen)
            seen += b
            assertEquals(n, KingWenTable.number(b.map { it.digitToInt() }))
        }
    }
}

class ReadingGuideTest {
    @Test
    fun zeroMovingUsesPrimaryGuaci() {
        val f = ReadingGuide.focus(movingPositions = emptyList())
        assertEquals(ReadingFocus.Kind.PrimaryGuaci, f.kind)
    }

    @Test
    fun oneMovingUsesThatLine() {
        val f = ReadingGuide.focus(movingPositions = listOf(3))
        assertEquals(ReadingFocus.Kind.PrimaryLines(positions = listOf(3), lead = 3), f.kind)
    }

    @Test
    fun twoMovingUpperIsLead() {
        val f = ReadingGuide.focus(movingPositions = listOf(2, 5))
        assertEquals(ReadingFocus.Kind.PrimaryLines(positions = listOf(2, 5), lead = 5), f.kind)
    }

    @Test
    fun threeMovingBothGuaci() {
        val f = ReadingGuide.focus(movingPositions = listOf(1, 3, 6))
        assertEquals(ReadingFocus.Kind.BothGuaci, f.kind)
    }

    @Test
    fun fourMovingResultingStaticLowerIsLead() {
        val f = ReadingGuide.focus(movingPositions = listOf(1, 2, 4, 6))
        assertEquals(ReadingFocus.Kind.ResultingLines(positions = listOf(3, 5), lead = 3), f.kind)
    }

    @Test
    fun fiveMovingResultingStatic() {
        val f = ReadingGuide.focus(movingPositions = listOf(1, 2, 3, 4, 6))
        assertEquals(ReadingFocus.Kind.ResultingLines(positions = listOf(5), lead = 5), f.kind)
    }

    @Test
    fun sixMovingResultingGuaci() {
        val f = ReadingGuide.focus(movingPositions = listOf(1, 2, 3, 4, 5, 6))
        assertEquals(ReadingFocus.Kind.ResultingGuaci, f.kind)
    }
}

class HexagramStoreTest {
    @Test
    fun allHexagramsHaveXiangTexts() {
        val stream = javaClass.classLoader!!.getResourceAsStream("Hexagrams.json")
            ?: error("Hexagrams.json missing from test resources")
        val store = HexagramStore.fromStream(stream)
        assertEquals(64, store.hexagrams.size)
        for (h in store.hexagrams) {
            assertTrue("missing tuanci #${h.number}", h.tuanci.isNotEmpty())
            assertTrue("missing daxiang #${h.number}", h.daxiang.isNotEmpty())
            assertEquals("xiaoxiang count #${h.number}", 6, h.xiaoxiang.size)
            assertEquals("yaoci count #${h.number}", 6, h.yaoci.size)
        }
        val qian = store.hexagram(1) ?: error("missing hexagram 1")
        assertEquals(qian.guaci.trim(), ReadingGuide.leadJingwen(emptyList(), qian, null))
        assertEquals(qian.yaoCi(3).trim(), ReadingGuide.leadJingwen(listOf(3), qian, null))
        assertTrue(qian.tuanci.contains("大哉乾元"))
        assertTrue(qian.daxiang.contains("自强不息"))
        assertTrue(qian.xiaoXiang(1).contains("阳在下"))
        assertTrue(qian.guaci.contains("元"))
        assertTrue(qian.yong?.ci?.contains("用九") == true)
        assertTrue(qian.wenyan.isNotEmpty())
        assertTrue(store.hexagram(2)?.yong?.ci?.contains("用六") == true)
        assertTrue(store.hexagram(24)?.yaoci?.last()?.contains("十年") == true)
    }
}
