package com.yizhidao.app.lang

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageTest {
    @Test
    fun englishSystemUsesEnglishUI() {
        val language = AppLanguage.from(Locale.US, listOf(Locale.US))
        assertEquals(AppLanguage.UI.English, language.ui)
        assertEquals(AppLanguage.Script.Simplified, language.script)
        assertEquals("Cast", language.ui("起卦", "Cast"))
    }

    @Test
    fun englishWithTaiwanPreferredKeepsTraditionalScript() {
        val language = AppLanguage.from(
            Locale.US,
            listOf(Locale.US, Locale.Builder().setLanguage("zh").setScript("Hant").setRegion("TW").build()),
        )
        assertEquals(AppLanguage.UI.English, language.ui)
        assertEquals(AppLanguage.Script.Traditional, language.script)
    }

    @Test
    fun taiwanUsesChineseUIAndTraditionalScript() {
        val language = AppLanguage.from(Locale.TAIWAN, listOf(Locale.TAIWAN))
        assertEquals(AppLanguage.UI.Chinese, language.ui)
        assertTrue(language.isTraditional)
        assertFalse(language.isEnglish)
    }

    @Test
    fun preferredEnglishWinsWhenLocaleCurrentStaysChinese() {
        val language = AppLanguage.from(Locale.SIMPLIFIED_CHINESE, listOf(Locale.US))
        assertEquals(AppLanguage.UI.English, language.ui)
        assertEquals("Cast", language.ui("起卦", "Cast"))
    }

    @Test
    fun mainlandUsesChineseUIAndSimplifiedScript() {
        val language = AppLanguage.from(Locale.SIMPLIFIED_CHINESE, listOf(Locale.SIMPLIFIED_CHINESE))
        assertEquals(AppLanguage.UI.Chinese, language.ui)
        assertEquals(AppLanguage.Script.Simplified, language.script)
        assertEquals("起卦", language.ui("起卦", "Cast"))
    }
}
