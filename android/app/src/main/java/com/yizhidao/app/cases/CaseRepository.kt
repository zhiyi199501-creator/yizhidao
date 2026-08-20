package com.yizhidao.app.cases

import android.app.Application
import android.content.Context
import com.yizhidao.CaseStudy
import com.yizhidao.CaseStudyCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 包内底稿 + 本地缓存；打开案例页时向服务端拉取最新（与 iOS CaseStore 一致）。 */
class CaseRepository(app: Application) {
    private val context = app.applicationContext
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cacheFile = context.filesDir.resolve(CACHE_NAME)
    private val _cases = MutableStateFlow(loadLocal())
    val cases: StateFlow<List<CaseStudy>> = _cases.asStateFlow()

    suspend fun refresh() {
        try {
            when (val result = CasesApi.fetchCases(prefs.getString(VERSION_KEY, null))) {
                CasesApi.FetchResult.NotModified -> Unit
                is CasesApi.FetchResult.Updated -> {
                    _cases.value = result.cases
                    prefs.edit().putString(VERSION_KEY, result.version).apply()
                    cacheFile.writeText(CaseStudyCodec.encodeList(result.cases))
                }
            }
        } catch (_: Exception) {
            // 离线或服务不可用时沿用包内 / 缓存
        }
    }

    private fun loadLocal(): List<CaseStudy> {
        if (cacheFile.exists()) {
            runCatching { return CaseStudyCodec.decodeList(cacheFile.readText()) }
        }
        return CaseStudyCodec.decodeList(
            context.assets.open("cases.json").bufferedReader().use { it.readText() },
        )
    }

    companion object {
        private const val PREFS = "cases.sync.v1"
        private const val VERSION_KEY = "version"
        private const val CACHE_NAME = "cases-cache.json"
    }
}
