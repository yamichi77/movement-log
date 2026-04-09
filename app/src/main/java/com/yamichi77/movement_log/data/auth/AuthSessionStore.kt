package com.yamichi77.movement_log.data.auth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthSessionStore(
    appContext: Context? = null,
) {
    private val preferences = appContext?.applicationContext?.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )
    private val _accessToken = MutableStateFlow(
        preferences?.getString(AccessTokenKey, null)?.takeIf { it.isNotBlank() },
    )
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    fun setAccessToken(token: String?) {
        val normalizedToken = token?.trim()?.takeIf { it.isNotBlank() }
        _accessToken.value = normalizedToken
        preferences?.edit()?.apply {
            if (normalizedToken == null) {
                remove(AccessTokenKey)
            } else {
                putString(AccessTokenKey, normalizedToken)
            }
        }?.apply()
    }

    private companion object {
        const val PreferencesName = "auth_session_store"
        const val AccessTokenKey = "access_token"
    }
}
