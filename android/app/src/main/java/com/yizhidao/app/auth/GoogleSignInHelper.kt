package com.yizhidao.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.yizhidao.app.BuildConfig

object GoogleSignInHelper {
    val isConfigured: Boolean
        get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    suspend fun signIn(context: Context): String {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (clientId.isEmpty()) {
            throw LoginError.Network("Google 登录未配置")
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val credentialManager = CredentialManager.create(context)
        return try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else {
                throw LoginError.Network("Google 登录失败")
            }
        } catch (_: GetCredentialCancellationException) {
            throw LoginError.Network("已取消 Google 登录")
        }
    }
}
