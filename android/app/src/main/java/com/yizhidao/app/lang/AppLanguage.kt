package com.yizhidao.app.lang

import android.content.Context
import android.icu.text.Transliterator
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(val id: String, val title: String) {
    Simplified("zh-Hans", "简体中文"),
    Traditional("zh-Hant", "繁体中文"),
    ;

    fun convert(text: String): String {
        if (this != Traditional || text.isEmpty()) return text
        return translator?.transliterate(text) ?: text
    }

    companion object {
        fun fromId(id: String?): AppLanguage =
            entries.firstOrNull { it.id == id } ?: Simplified
    }
}

private val translator: Transliterator? by lazy {
    runCatching { Transliterator.getInstance("Hans-Hant") }.getOrNull()
}

object AppLanguageStore {
    private const val PREF = "app_language"
    private const val KEY = "kind"

    private val _language = MutableStateFlow(AppLanguage.Simplified)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun init(context: Context) {
        val id = context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, AppLanguage.Simplified.id)
        _language.value = AppLanguage.fromId(id)
    }

    fun current(): AppLanguage = _language.value

    fun set(context: Context, value: AppLanguage) {
        _language.value = value
        context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, value.id)
            .apply()
    }
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.Simplified }
