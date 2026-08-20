package com.yizhidao.app.cases

import com.yizhidao.CaseStudy
import com.yizhidao.app.auth.AppHttp
import com.yizhidao.app.auth.AuthApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

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

    suspend fun fetchCases(ifNoneMatch: String?): FetchResult {
        val headers = buildMap {
            put("Accept", "application/json")
            if (!ifNoneMatch.isNullOrBlank()) {
                put("If-None-Match", "\"$ifNoneMatch\"")
            }
        }
        val (code, text) = AppHttp.request(
            url = "${AuthApi.baseUrl}/v1/cases",
            method = "GET",
            headers = headers,
        )
        return when (code) {
            304 -> FetchResult.NotModified
            in 200..299 -> {
                val decoded = json.decodeFromString<CasesResponse>(text)
                if (!decoded.ok) throw IOException("获取案例失败")
                FetchResult.Updated(decoded.version, decoded.cases)
            }
            else -> throw IOException("获取案例失败")
        }
    }
}
