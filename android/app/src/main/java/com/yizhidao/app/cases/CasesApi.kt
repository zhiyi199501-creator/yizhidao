package com.yizhidao.app.cases

import com.yizhidao.CaseStudy
import com.yizhidao.app.auth.AuthApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object CasesApi {
    private val json = Json { ignoreUnknownKeys = true }

    sealed class FetchResult {
        data object NotModified : FetchResult()
        data class Updated(val version: String, val cases: List<CaseStudy>) : FetchResult()
    }

    @Serializable
    private data class CasesResponse(
        val ok: Boolean,
        val version: String,
        val cases: List<CaseStudy>,
    )

    suspend fun fetchCases(ifNoneMatch: String?): FetchResult = withContext(Dispatchers.IO) {
        val conn = URL("${AuthApi.baseUrl}/v1/cases").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 20_000
            conn.readTimeout = 20_000
            conn.useCaches = false
            if (!ifNoneMatch.isNullOrBlank()) {
                conn.setRequestProperty("If-None-Match", "\"$ifNoneMatch\"")
            }
            when (conn.responseCode) {
                304 -> FetchResult.NotModified
                in 200..299 -> {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val decoded = json.decodeFromString<CasesResponse>(text)
                    if (!decoded.ok) throw IOException("获取案例失败")
                    FetchResult.Updated(decoded.version, decoded.cases)
                }
                else -> throw IOException("获取案例失败")
            }
        } finally {
            conn.disconnect()
        }
    }
}
