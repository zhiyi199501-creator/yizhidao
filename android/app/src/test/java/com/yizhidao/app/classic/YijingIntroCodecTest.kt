package com.yizhidao.app.classic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class YijingIntroCodecTest {
    @Test
    fun decodesBookletBlocks() {
        val file = listOf(
            File("../../ios/Yizhidao/Resources/YijingIntro.json"),
            File("../ios/Yizhidao/Resources/YijingIntro.json"),
            File("ios/Yizhidao/Resources/YijingIntro.json"),
        ).first { it.exists() }
        val book = YijingIntroCodec.decode(file.readText())
        assertEquals(9, book.chapters.size)
        assertEquals(
            listOf(
                "what", "purpose", "yin-yang-bagua", "hexagrams-lines",
                "how-to-read", "play-the-text", "how-to-cast", "changing-lines", "path",
            ),
            book.chapters.map { it.id },
        )
        assertTrue(book.chapters[5].plainText.contains("观其象"))
        assertTrue(book.chapters[6].plainText.contains("数字起卦"))
        assertTrue(book.chapters[6].plainText.contains("输入三数"))
        assertTrue(book.chapters[6].plainText.contains("时间起卦"))
        assertTrue(book.chapters[6].plainText.contains("金钱起卦"))
        assertTrue(book.chapters[7].plainText.contains("主看"))
        assertTrue(book.chapters[2].blocks.any { it.type == "figure" && it.kind == "bagua" })
        assertTrue(book.chapters[7].blocks.any { it.type == "table" })
        assertTrue(book.chapters[8].blocks.any { it.type == "links" })
    }
}
