package com.yizhidao.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AIAnswerFormatterTest {
    @Test
    fun keepsShortTextAsOneParagraph() {
        val raw = "壮马象征可靠而有力的援助。"
        assertEquals(listOf(raw), AIAnswerFormatter.paragraphs(raw))
    }

    @Test
    fun splitsLongTextAtSentenceEnd() {
        val raw = "判断哪一位中医师最适合你，关键不在名声或价格，而在你是否能借到那匹「壮马」。" +
            "二爻说「用拯马壮吉」，壮马象征可靠而有力的援助，因此适合的医师应具备三个特质。" +
            "你可以先约一两位医师做初诊，观察对方是否顺守医道、不炫技。" +
            "若初诊后你感到安心、被理解，那就是适合你的人选。"
        val paragraphs = AIAnswerFormatter.paragraphs(raw)
        assertTrue(paragraphs.size > 1)
        assertEquals(raw, paragraphs.joinToString(""))
        paragraphs.forEach {
            assertFalse(it.startsWith("。"))
            assertFalse(it.startsWith("」"))
        }
    }

    @Test
    fun honorsExistingLineBreaks() {
        assertEquals(
            listOf("第一段。", "第二段。"),
            AIAnswerFormatter.paragraphs("第一段。\n\n第二段。"),
        )
    }

    @Test
    fun keepsClosingQuoteWithPreviousSentence() {
        val raw = "他说「顺以则也。」".repeat(6) + "尾。"
        val paragraphs = AIAnswerFormatter.paragraphs(raw)
        assertEquals(raw, paragraphs.joinToString(""))
        paragraphs.forEach { assertFalse(it.startsWith("」")) }
    }

    @Test
    fun mergesShortTailIntoPreviousParagraph() {
        val raw = "这是一句足够长的话用来占满一段。".repeat(4) + "短尾。"
        val paragraphs = AIAnswerFormatter.paragraphs(raw)
        assertEquals(raw, paragraphs.joinToString(""))
        assertFalse(paragraphs.contains("短尾。"))
    }
}
