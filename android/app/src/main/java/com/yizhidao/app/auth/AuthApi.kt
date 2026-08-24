package com.yizhidao.app.auth

import com.yizhidao.app.BuildConfig
import kotlinx.serialization.Serializable
import com.yizhidao.CastResult
import com.yizhidao.app.ai.SavedAIFollowUp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

sealed class LoginError(message: String) : Exception(message) {
    class Network(message: String) : LoginError(message)
    data object Unauthorized : LoginError("登录已过期，请重新登录")
}

object AuthApi {
    val baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/')
    private const val HEX_TEXT_VERSION = "yi-zhengshi-2026-08"
    private const val AI_TIMEOUT_MS = 180_000

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class SMSCodeResponse(val ok: Boolean, val cooldownSec: Int)

    @Serializable
    data class LoginResponse(
        val ok: Boolean,
        val accessToken: String,
        val user: User,
    ) {
        @Serializable
        data class User(
            val id: String,
            val nickname: String,
            val phone: String? = null,
            val email: String? = null,
        )
    }

    typealias SMSLoginResponse = LoginResponse

    @Serializable
    data class MeResponse(val ok: Boolean, val user: LoginResponse.User)

    @Serializable
    data class AIAnalyzeResponse(val ok: Boolean, val analysis: Analysis) {
        @Serializable
        data class Analysis(
            val summary: String,
            val focus: String,
            val advice: List<String>,
        )
    }

    @Serializable
    data class AIFollowupResponse(val ok: Boolean, val reply: String)

    @Serializable
    private data class ErrorEnvelope(val message: String? = null)

    suspend fun sendSMSCode(phone: String): SMSCodeResponse {
        val decoded = post(
            path = "/v1/auth/sms/send",
            body = buildJsonObject { put("phone", phone) }.toString(),
            fallback = "发送验证码失败",
        ) { json.decodeFromString<SMSCodeResponse>(it) }
        if (!decoded.ok) throw LoginError.Network("发送验证码失败")
        return decoded
    }

    suspend fun loginBySMS(phone: String, code: String): LoginResponse {
        val decoded = post(
            path = "/v1/auth/sms/login",
            body = buildJsonObject {
                put("phone", phone)
                put("code", code)
            }.toString(),
            fallback = "登录失败",
        ) { json.decodeFromString<LoginResponse>(it) }
        if (!decoded.ok) throw LoginError.Network("登录失败")
        return decoded
    }

    suspend fun sendEmailCode(email: String): SMSCodeResponse {
        val decoded = post(
            path = "/v1/auth/email/send",
            body = buildJsonObject { put("email", email) }.toString(),
            fallback = "发送验证码失败",
        ) { json.decodeFromString<SMSCodeResponse>(it) }
        if (!decoded.ok) throw LoginError.Network("发送验证码失败")
        return decoded
    }

    suspend fun loginByEmail(email: String, code: String): LoginResponse {
        val decoded = post(
            path = "/v1/auth/email/login",
            body = buildJsonObject {
                put("email", email)
                put("code", code)
            }.toString(),
            fallback = "登录失败",
        ) { json.decodeFromString<LoginResponse>(it) }
        if (!decoded.ok) throw LoginError.Network("登录失败")
        return decoded
    }

    suspend fun loginWithApple(identityToken: String, fullName: String? = null): LoginResponse {
        val decoded = post(
            path = "/v1/auth/apple",
            body = buildJsonObject {
                put("identityToken", identityToken)
                if (!fullName.isNullOrBlank()) put("fullName", fullName)
            }.toString(),
            fallback = "Apple 登录失败",
        ) { json.decodeFromString<LoginResponse>(it) }
        if (!decoded.ok) throw LoginError.Network("Apple 登录失败")
        return decoded
    }

    suspend fun loginWithGoogle(idToken: String): LoginResponse {
        val decoded = post(
            path = "/v1/auth/google",
            body = buildJsonObject { put("idToken", idToken) }.toString(),
            fallback = "Google 登录失败",
        ) { json.decodeFromString<LoginResponse>(it) }
        if (!decoded.ok) throw LoginError.Network("Google 登录失败")
        return decoded
    }

    suspend fun fetchMe(accessToken: String): MeResponse {
        val decoded = get(
            path = "/v1/me",
            accessToken = accessToken,
            fallback = "获取用户信息失败",
        ) { json.decodeFromString<MeResponse>(it) }
        if (!decoded.ok) throw LoginError.Network("获取用户信息失败")
        return decoded
    }

    suspend fun analyzeReading(result: CastResult, accessToken: String): AIAnalyzeResponse {
        val decoded = post(
            path = "/v1/ai/analyze",
            body = readingPayload(result).toString(),
            fallback = "解读失败",
            accessToken = accessToken,
            timeoutMs = AI_TIMEOUT_MS,
        ) { json.decodeFromString<AIAnalyzeResponse>(it) }
        if (!decoded.ok) throw LoginError.Network("解读失败")
        return decoded
    }

    suspend fun followupReading(
        result: CastResult,
        analysis: AIAnalyzeResponse.Analysis,
        conversation: List<SavedAIFollowUp>,
        message: String,
        accessToken: String,
    ): AIFollowupResponse {
        val decoded = post(
            path = "/v1/ai/followup",
            body = readingPayload(result) {
                put("message", message)
                putJsonObject("previousAnalysis") {
                    put("summary", analysis.summary)
                    put("focus", analysis.focus)
                    putJsonArray("advice") { analysis.advice.forEach { add(it) } }
                }
                putJsonArray("conversation") {
                    conversation.forEach { turn ->
                        addJsonObject {
                            put("user", turn.user)
                            put("assistant", turn.assistant)
                        }
                    }
                }
            }.toString(),
            fallback = "追问失败",
            accessToken = accessToken,
            timeoutMs = AI_TIMEOUT_MS,
        ) { json.decodeFromString<AIFollowupResponse>(it) }
        if (!decoded.ok || decoded.reply.isBlank()) throw LoginError.Network("追问失败")
        return decoded
    }

    private fun readingPayload(
        result: CastResult,
        extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {},
    ) = buildJsonObject {
        put("method", result.method.raw)
        put("primaryNumber", result.primaryNumber)
        putJsonArray("movingPositions") { result.movingPositions.forEach { add(it) } }
        putJsonArray("lines") { result.lines.forEach { add(it.rawValue) } }
        put("hexTextVersion", HEX_TEXT_VERSION)
        result.question?.takeIf { it.isNotBlank() }?.let { put("question", it) }
        result.resultingNumber?.let { put("resultingNumber", it) }
        extra()
    }

    fun describe(error: Throwable): String {
        if (error is LoginError) return error.message ?: "未知错误"
        val root = generateSequence(error) { it.cause }.last()
        val detail = (root.message ?: error.message)?.take(160)
        return when {
            error is SocketTimeoutException || root is SocketTimeoutException ->
                "连接超时：$baseUrl"
            error is UnknownHostException || root is UnknownHostException ->
                "解析不到主机：$baseUrl"
            error is ConnectException || root is ConnectException ->
                "连不上 $baseUrl${detail?.let { "（$it）" } ?: ""}"
            error is SSLException || root is SSLException ||
                error is CertificateException || root is CertificateException ->
                "证书校验失败：$baseUrl${detail?.let { "（$it）" } ?: ""}"
            error is IOException ->
                "连不上 $baseUrl${detail?.let { "（$it）" } ?: ""}"
            else -> error.message ?: "未知错误"
        }
    }

    private suspend inline fun <T> post(
        path: String,
        body: String,
        fallback: String,
        accessToken: String? = null,
        timeoutMs: Int = 20_000,
        crossinline decode: (String) -> T,
    ): T {
        val headers = buildMap {
            put("Content-Type", "application/json; charset=utf-8")
            put("Accept", "application/json")
            if (accessToken != null) put("Authorization", "Bearer $accessToken")
        }
        val (code, text) = AppHttp.request(
            url = "$baseUrl$path",
            method = "POST",
            body = body,
            headers = headers,
            timeoutMs = timeoutMs,
        )
        return interpret(code, text, fallback, decode)
    }

    private suspend inline fun <T> get(
        path: String,
        accessToken: String,
        fallback: String,
        timeoutMs: Int = 20_000,
        crossinline decode: (String) -> T,
    ): T {
        val (code, text) = AppHttp.request(
            url = "$baseUrl$path",
            method = "GET",
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer $accessToken",
            ),
            timeoutMs = timeoutMs,
        )
        return interpret(code, text, fallback, decode)
    }

    private inline fun <T> interpret(
        code: Int,
        text: String,
        fallback: String,
        decode: (String) -> T,
    ): T {
        if (code == 401) throw LoginError.Unauthorized
        if (code !in 200..299) throw decodeError(text, fallback)
        return decode(text)
    }

    private fun decodeError(text: String, fallback: String): LoginError.Network {
        val message = runCatching { json.decodeFromString<ErrorEnvelope>(text).message }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: fallback
        return LoginError.Network(message)
    }
}
