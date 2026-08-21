package com.yizhidao.app.ima

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImaAnswerFormatterTest {
    @Test
    fun parsesTabTableAndHidesMarker() {
        val raw = """
            所以才能「亨」
            六、占断参考
            表格
            占问	结果
            占婚姻	小康之象。不会太富裕，但也不会饿死
            占失物	往西边去找 2
            七、核心启示
        """.trimIndent()
        val blocks = ImaAnswerFormatter.blocks(raw)
        assertEquals(3, blocks.size)
        val before = blocks[0] as ImaAnswerBlock.Text
        assertTrue(before.text.contains("占断参考"))
        assertFalse(before.text.contains("表格"))
        val table = blocks[1] as ImaAnswerBlock.Table
        assertEquals(3, table.rows.size)
        assertEquals(listOf("占问", "结果"), table.rows[0])
        assertEquals("占婚姻", table.rows[1][0])
        assertEquals("往西边去找", table.rows[2][1])
        val after = blocks[2] as ImaAnswerBlock.Text
        assertTrue(after.text.startsWith("七、核心启示"))
    }

    @Test
    fun parsesMarkdownTable() {
        val raw = """
            对照如下
            | 占问 | 启示 |
            |:----:|:----:|
            | 占人事 | 要虚怀若谷 |
            | 女占男 | 非常好 |
            下文
        """.trimIndent()
        val blocks = ImaAnswerFormatter.blocks(raw)
        assertEquals(3, blocks.size)
        val table = blocks[1] as ImaAnswerBlock.Table
        assertEquals(listOf("占问", "启示"), table.rows[0])
        assertEquals(listOf("占人事", "要虚怀若谷"), table.rows[1])
        assertEquals("女占男", table.rows[2][0])
    }

    @Test
    fun stripsCitationFootnotes() {
        val text = """
            永远不行动 1
            这就是勇气 1。
            称为「初九」。1
            下卦为乾（天）1。
            凡提到「大」几乎都指阳2。
            1. 小畜不是小气
            第12卦仍是正文
        """.trimIndent()
        assertEquals(
            """
                永远不行动
                这就是勇气。
                称为「初九」。
                下卦为乾（天）。
                凡提到「大」几乎都指阳。
                1. 小畜不是小气
                第12卦仍是正文
            """.trimIndent(),
            ImaAnswerFormatter.stripped(text),
        )
    }

    @Test
    fun stripsTableCellCitation() {
        assertEquals("祸起于萧墙之内", ImaAnswerFormatter.stripped("祸起于萧墙之内 2"))
    }

    @Test
    fun stripsThinkingProcessLabel() {
        val raw = """
            思考过程
            思考过程
            已浏览张庆祥讲易经_364.docx
            思考过程
            大壮卦九二爻详解
        """.trimIndent()
        val blocks = ImaAnswerFormatter.blocks(raw)
        assertEquals(1, blocks.size)
        val text = (blocks[0] as ImaAnswerBlock.Text).text
        assertFalse(text.contains("思考过程"))
        assertTrue(text.startsWith("已浏览"))
        assertTrue(text.contains("大壮卦九二爻详解"))
    }
}
