package com.yizhidao.app.auth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LocalUserSession(
    val isLoggedIn: Boolean = false,
    val displayName: String = "游客",
    val phone: String? = null,
    val avatarSymbol: String = "person.crop.circle.fill",
    val accessToken: String? = null,
) {
    companion object {
        val Guest = LocalUserSession()
    }
}

class LocalAuthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _session = MutableStateFlow(load())
    val session: StateFlow<LocalUserSession> = _session.asStateFlow()

    fun load(): LocalUserSession {
        val raw = prefs.getString(KEY, null) ?: return LocalUserSession.Guest
        return runCatching { json.decodeFromString<LocalUserSession>(raw) }
            .getOrDefault(LocalUserSession.Guest)
    }

    fun save(session: LocalUserSession) {
        prefs.edit().putString(KEY, json.encodeToString(session)).apply()
        _session.value = session
    }

    fun logout() {
        save(LocalUserSession.Guest)
    }

    companion object {
        private const val PREFS = "auth.local.session.v1"
        private const val KEY = "session"
    }
}
