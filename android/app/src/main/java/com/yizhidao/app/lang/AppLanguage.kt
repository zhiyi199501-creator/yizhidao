package com.yizhidao.app.lang

import android.icu.text.Transliterator
import android.os.LocaleList
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

data class AppLanguage(
    val ui: UI,
    val script: Script,
) {
    enum class UI { Chinese, English }

    enum class Script { Simplified, Traditional }

    val isEnglish: Boolean get() = ui == UI.English
    val isTraditional: Boolean get() = script == Script.Traditional

    fun convert(text: String): String {
        if (script != Script.Traditional || text.isEmpty()) return text
        return translator?.transliterate(text) ?: text
    }

    fun ui(zh: String, en: String): String = if (isEnglish) en else convert(zh)

    companion object {
        fun current(): AppLanguage = from(LocaleList.getDefault())

        fun from(locales: LocaleList): AppLanguage {
            val first = if (locales.size() > 0) locales[0] else Locale.getDefault()
            val preferred = buildList {
                for (i in 0 until locales.size()) add(locales[i])
            }
            return from(first, preferred)
        }

        fun from(locale: Locale, preferred: List<Locale> = emptyList()): AppLanguage {
            val primary = preferred.firstOrNull() ?: locale
            if (isChinese(primary)) {
                return AppLanguage(
                    ui = UI.Chinese,
                    script = if (isTraditional(primary)) Script.Traditional else Script.Simplified,
                )
            }
            var script = Script.Simplified
            for (item in preferred) {
                if (isChinese(item)) {
                    script = if (isTraditional(item)) Script.Traditional else Script.Simplified
                    break
                }
            }
            return AppLanguage(ui = UI.English, script = script)
        }

        private fun isChinese(locale: Locale): Boolean {
            val language = locale.language
            return language.equals("zh", ignoreCase = true) ||
                language.startsWith("zh-", ignoreCase = true)
        }

        private fun isTraditional(locale: Locale): Boolean {
            val script = locale.script
            if (script.equals("Hant", ignoreCase = true)) return true
            if (script.equals("Hans", ignoreCase = true)) return false
            val country = locale.country
            if (
                country.equals("TW", ignoreCase = true) ||
                country.equals("HK", ignoreCase = true) ||
                country.equals("MO", ignoreCase = true)
            ) {
                return true
            }
            return locale.toLanguageTag().contains("Hant", ignoreCase = true)
        }
    }
}

private val translator: Transliterator? by lazy {
    runCatching { Transliterator.getInstance("Hans-Hant") }.getOrNull()
}

val LocalAppLanguage = staticCompositionLocalOf {
    AppLanguage(AppLanguage.UI.Chinese, AppLanguage.Script.Simplified)
}
