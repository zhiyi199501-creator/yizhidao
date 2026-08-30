package com.yizhidao

import org.junit.Assert.assertEquals
import org.junit.Test

class HexagramNamesTest {
    @Test
    fun tableCoversSixtyFour() {
        assertEquals("Qián", HexagramNames.pinyin(1))
        assertEquals("Heaven", HexagramNames.epithet(1))
        assertEquals("Kūn", HexagramNames.pinyin(2))
        assertEquals("Retreat", HexagramNames.epithet(33))
        assertEquals("Wèi Jì", HexagramNames.pinyin(64))
        assertEquals("Before Completion", HexagramNames.epithet(64))
        assertEquals("", HexagramNames.pinyin(0))
    }
}
