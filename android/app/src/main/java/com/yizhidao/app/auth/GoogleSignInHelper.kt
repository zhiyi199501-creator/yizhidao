package com.yizhidao.app.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.yizhidao.app.BuildConfig
import kotlinx.coroutines.delay

object GoogleSignInHelper {
    val isConfigured: Boolean
        get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** 刚装包 / 换签名后 Play 服务偶发 [16] reauth，短间隔多试几次。 */
    private val retryDelaysMs = longArrayOf(2_500L, 5_000L, 8_000L)

    suspend fun signIn(context: Context): String {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (clientId.isEmpty()) {
            throw LoginError.Network("Google 登录未配置")
        }
        val activity = context.findActivity()
            ?: throw LoginError.Network("Google 登录需要在前台页面发起")
        val credentialManager = CredentialManager.create(activity)

        var lastError: GetCredentialException? = null
        val attempts = retryDelaysMs.size + 1
        for (attempt in 0 until attempts) {
            try {
                return signInOnce(credentialManager, activity, clientId)
            } catch (e: GetCredentialException) {
                if (isUserCancel(e)) {
                    throw LoginError.Network("已取消 Google 登录")
                }
                lastError = e
                val canRetry = attempt < attempts - 1 && isTransientGoogleFailure(e)
                if (!canRetry) {
                    throw LoginError.Network(mapCredentialError(e))
                }
                delay(retryDelaysMs[attempt])
            }
        }
        throw LoginError.Network(mapCredentialError(lastError))
    }

    private suspend fun signInOnce(
        credentialManager: CredentialManager,
        activity: Activity,
        clientId: String,
    ): String {
        // 用户点了登录按钮：优先账号选择；失败再试 bottomsheet。
        return try {
            requestIdToken(
                credentialManager,
                activity,
                GetCredentialRequest.Builder()
                    .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
                    .build(),
            )
        } catch (first: GetCredentialException) {
            if (isUserCancel(first)) throw first
            if (!isTransientGoogleFailure(first)) throw first
            try {
                requestIdToken(
                    credentialManager,
                    activity,
                    GetCredentialRequest.Builder()
                        .addCredentialOption(
                            GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(clientId)
                                .build(),
                        )
                        .build(),
                )
            } catch (second: GetCredentialException) {
                // 保留更有信息的一次错误给上层重试 / 文案。
                throw if (isReauthFailure(first)) first else second
            }
        }
    }

    private fun isTransientGoogleFailure(error: GetCredentialException): Boolean =
        error is NoCredentialException || isReauthFailure(error)

    private fun isReauthFailure(error: GetCredentialException): Boolean {
        val detail = credentialDetail(error).lowercase()
        return "reauth" in detail || ("[16]" in detail && "cancel" !in detail)
    }

    private fun isUserCancel(error: GetCredentialException): Boolean {
        if (isReauthFailure(error)) return false
        val detail = credentialDetail(error).lowercase()
        return error is GetCredentialCancellationException ||
            "cancelled by user" in detail ||
            "canceled by user" in detail
    }

    private fun mapCredentialError(error: GetCredentialException?): String {
        if (error == null) return "Google 登录失败"
        val detail = credentialDetail(error)
        return when {
            isReauthFailure(error) ->
                "刚安装或更新后，Google 登录有时要等一两分钟。请保持 VPN，稍后再试；仍不行再到「设置 → 账号与同步」重新添加 Google 账号。"
            isUserCancel(error) ->
                "已取消 Google 登录"
            error is NoCredentialException ->
                "暂时拿不到 Google 账号。请保持 VPN，稍等再试；并确认设置里已登录 Google。"
            detail.isNotEmpty() ->
                "Google 登录失败：$detail"
            else ->
                "Google 登录失败"
        }
    }

    private fun credentialDetail(error: GetCredentialException): String =
        sequenceOf(error.message, error.type)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString(" ")

    private suspend fun requestIdToken(
        credentialManager: CredentialManager,
        activity: Activity,
        request: GetCredentialRequest,
    ): String {
        val credential = credentialManager.getCredential(activity, request).credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw LoginError.Network("Google 登录失败")
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
