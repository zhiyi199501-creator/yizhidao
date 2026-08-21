package com.yizhidao.app.lang

import android.icu.text.Transliterator
import android.os.LocaleList
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

enum class AppLanguage {
    Simplified,
    Traditional,
    ;

    fun convert(text: String): String {
        if (this != Traditional || text.isEmpty()) return text
        return translator?.transliterate(text) ?: text
    }

    companion object {
        fun from(locales: LocaleList): AppLanguage {
            for (i in 0 until locales.size()) {
                val locale = locales[i]
                if (isChinese(locale)) return from(locale)
            }
            return Simplified
        }

        fun from(locale: Locale): AppLanguage {
            if (!isChinese(locale)) return Simplified
            val script = locale.script
            if (script.equals("Hant", ignoreCase = true)) return Traditional
            if (script.equals("Hans", ignoreCase = true)) return Simplified
            val country = locale.country
            if (
                country.equals("TW", ignoreCase = true) ||
                country.equals("HK", ignoreCase = true) ||
                country.equals("MO", ignoreCase = true)
            ) {
                return Traditional
            }
            return if (locale.toLanguageTag().contains("Hant", ignoreCase = true)) {
                Traditional
            } else {
                Simplified
            }
        }

        private fun isChinese(locale: Locale): Boolean {
            val language = locale.language
            return language.equals("zh", ignoreCase = true) ||
                language.startsWith("zh-", ignoreCase = true)
        }
    }
}

private val translator: Transliterator? by lazy {
    runCatching { Transliterator.getInstance("Hans-Hant") }.getOrNull()
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.Simplified }
