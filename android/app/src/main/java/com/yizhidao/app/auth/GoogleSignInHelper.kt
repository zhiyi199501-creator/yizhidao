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
import com.yizhidao.app.lang.AppLanguage

object GoogleSignInHelper {
    val isConfigured: Boolean
        get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    suspend fun signIn(context: Context): String {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (clientId.isEmpty()) {
            throw LoginError.Network("Google 登录未配置")
        }
        val activity = context.findActivity()
            ?: throw LoginError.Network("Google 登录需要在前台页面发起")
        val credentialManager = CredentialManager.create(activity)

        // 先 bottomsheet（此前红米上更稳），没有凭证再走一次按钮式账号选择。
        return try {
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
        } catch (first: GetCredentialException) {
            if (isUserCancel(first)) {
                throw LoginError.Network("已取消 Google 登录")
            }
            if (first !is NoCredentialException && !isReauthFailure(first)) {
                throw LoginError.Network(mapCredentialError(first))
            }
            try {
                requestIdToken(
                    credentialManager,
                    activity,
                    GetCredentialRequest.Builder()
                        .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
                        .build(),
                )
            } catch (second: GetCredentialException) {
                if (isUserCancel(second)) {
                    throw LoginError.Network("已取消 Google 登录")
                }
                throw LoginError.Network(mapCredentialError(second))
            }
        }
    }

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

    private fun mapCredentialError(@Suppress("UNUSED_PARAMETER") error: GetCredentialException): String =
        // GMS / 本机连不上 Google（含 reauth、无凭证等）统一短提示。
        AppLanguage.current().ui(
            "连接 Google 失败，建议使用邮箱登录",
            "Couldn't connect to Google. Try email sign-in.",
        )

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
