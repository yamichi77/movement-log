package com.yamichi77.movement_log.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class StoredAuthSession(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val idToken: String? = null,
    val tokenType: String? = null,
    val accessTokenExpiresAtEpochMillis: Long? = null,
)

data class PendingAuthRequest(
    val state: String,
    val codeVerifier: String,
)

class AuthSessionStore(
    appContext: Context? = null,
) {
    private val preferences = appContext?.applicationContext?.let(::createPreferences)
    private var storedSessionState: StoredAuthSession? = loadSessionFromPreferences()
    private var pendingAuthRequestState: PendingAuthRequest? = loadPendingAuthorizationFromPreferences()
    private val _accessToken = MutableStateFlow(storedSessionState?.accessToken)
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    fun setAccessToken(token: String?) {
        val normalizedToken = token?.trim()?.takeIf { it.isNotBlank() }
        val current = currentSession()
        replaceSession(
            if (
                normalizedToken == null &&
                current?.refreshToken == null &&
                current?.idToken == null &&
                current?.tokenType == null &&
                current?.accessTokenExpiresAtEpochMillis == null
            ) {
                null
            } else {
                current.orEmpty().copy(accessToken = normalizedToken)
            },
        )
    }

    fun replaceSession(session: StoredAuthSession?) {
        storedSessionState = session
        _accessToken.value = session?.accessToken
        preferences?.edit()?.apply {
            putNullableString(AccessTokenKey, session?.accessToken)
            putNullableString(RefreshTokenKey, session?.refreshToken)
            putNullableString(IdTokenKey, session?.idToken)
            putNullableString(TokenTypeKey, session?.tokenType)
            putNullableLong(AccessTokenExpiresAtEpochMillisKey, session?.accessTokenExpiresAtEpochMillis)
        }?.apply()
    }

    fun currentSession(): StoredAuthSession? {
        return storedSessionState
    }

    fun clearTokens() {
        replaceSession(null)
        clearPendingAuthorization()
    }

    fun savePendingAuthorization(request: PendingAuthRequest) {
        pendingAuthRequestState = request
        preferences?.edit()?.apply {
            putString(PendingStateKey, request.state)
            putString(PendingCodeVerifierKey, request.codeVerifier)
        }?.apply()
    }

    fun pendingAuthorization(): PendingAuthRequest? {
        return pendingAuthRequestState
    }

    fun clearPendingAuthorization() {
        pendingAuthRequestState = null
        preferences?.edit()?.apply {
            remove(PendingStateKey)
            remove(PendingCodeVerifierKey)
        }?.apply()
    }

    private fun createPreferences(appContext: Context): SharedPreferences {
        val safeContext = appContext.applicationContext
        return runCatching {
            val masterKey = MasterKey.Builder(safeContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                safeContext,
                PreferencesName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse { error ->
            Log.w(LogTag, "EncryptedSharedPreferences unavailable, fallback to regular storage", error)
            safeContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        }
    }

    private fun loadSessionFromPreferences(): StoredAuthSession? {
        val prefs = preferences ?: return null
        val session = StoredAuthSession(
            accessToken = prefs.getString(AccessTokenKey, null)?.takeIf { it.isNotBlank() },
            refreshToken = prefs.getString(RefreshTokenKey, null)?.takeIf { it.isNotBlank() },
            idToken = prefs.getString(IdTokenKey, null)?.takeIf { it.isNotBlank() },
            tokenType = prefs.getString(TokenTypeKey, null)?.takeIf { it.isNotBlank() },
            accessTokenExpiresAtEpochMillis =
                if (prefs.contains(AccessTokenExpiresAtEpochMillisKey)) {
                    prefs.getLong(AccessTokenExpiresAtEpochMillisKey, 0L)
                } else {
                    null
                },
        )
        return session.takeIf {
            it.accessToken != null ||
                it.refreshToken != null ||
                it.idToken != null ||
                it.tokenType != null ||
                it.accessTokenExpiresAtEpochMillis != null
        }
    }

    private fun loadPendingAuthorizationFromPreferences(): PendingAuthRequest? {
        val prefs = preferences ?: return null
        val state = prefs.getString(PendingStateKey, null)?.takeIf { it.isNotBlank() } ?: return null
        val codeVerifier = prefs.getString(PendingCodeVerifierKey, null)?.takeIf { it.isNotBlank() }
            ?: return null
        return PendingAuthRequest(
            state = state,
            codeVerifier = codeVerifier,
        )
    }

    private fun SharedPreferences.Editor.putNullableString(key: String, value: String?) {
        if (value == null) {
            remove(key)
        } else {
            putString(key, value)
        }
    }

    private fun SharedPreferences.Editor.putNullableLong(key: String, value: Long?) {
        if (value == null) {
            remove(key)
        } else {
            putLong(key, value)
        }
    }

    private fun StoredAuthSession?.orEmpty(): StoredAuthSession = this ?: StoredAuthSession()

    private companion object {
        const val LogTag = "AuthSessionStore"
        const val PreferencesName = "auth_session_store"
        const val AccessTokenKey = "access_token"
        const val RefreshTokenKey = "refresh_token"
        const val IdTokenKey = "id_token"
        const val TokenTypeKey = "token_type"
        const val AccessTokenExpiresAtEpochMillisKey = "access_token_expires_at_epoch_millis"
        const val PendingStateKey = "pending_state"
        const val PendingCodeVerifierKey = "pending_code_verifier"
    }
}
