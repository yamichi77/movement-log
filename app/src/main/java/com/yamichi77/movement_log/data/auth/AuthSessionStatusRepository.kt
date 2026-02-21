package com.yamichi77.movement_log.data.auth

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

data class AuthSessionStatus(
    val isSessionManaged: Boolean = false,
    val reauthRequired: Boolean = false,
    val reauthReason: AuthErrorCode? = null,
    val reauthDetectedAtEpochMillis: Long? = null,
    val lastReauthNotifiedAtEpochMillis: Long? = null,
)

interface AuthSessionStatusRepository {
    val status: Flow<AuthSessionStatus>

    suspend fun markSessionEstablished()

    suspend fun markRefreshSucceeded()

    suspend fun markReauthRequired(
        reason: AuthErrorCode,
        detectedAtEpochMillis: Long = System.currentTimeMillis(),
    )

    suspend fun markReauthNotificationSent(
        notifiedAtEpochMillis: Long = System.currentTimeMillis(),
    )
}

private val Context.authSessionStatusDataStore by preferencesDataStore(name = "auth_session_status")

class DataStoreAuthSessionStatusRepository(
    private val appContext: Context,
) : AuthSessionStatusRepository {
    private val data = appContext.authSessionStatusDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }

    override val status: Flow<AuthSessionStatus> = data
        .map { preferences -> preferences.toAuthSessionStatus() }
        .distinctUntilChanged()

    override suspend fun markSessionEstablished() {
        appContext.authSessionStatusDataStore.edit { preferences ->
            preferences[IsSessionManagedKey] = true
            preferences[ReauthRequiredKey] = false
            preferences.remove(ReauthReasonKey)
            preferences.remove(ReauthDetectedAtEpochMillisKey)
        }
    }

    override suspend fun markRefreshSucceeded() {
        appContext.authSessionStatusDataStore.edit { preferences ->
            preferences[IsSessionManagedKey] = true
            preferences[ReauthRequiredKey] = false
            preferences.remove(ReauthReasonKey)
            preferences.remove(ReauthDetectedAtEpochMillisKey)
        }
    }

    override suspend fun markReauthRequired(reason: AuthErrorCode, detectedAtEpochMillis: Long) {
        appContext.authSessionStatusDataStore.edit { preferences ->
            preferences[IsSessionManagedKey] = true
            preferences[ReauthRequiredKey] = true
            preferences[ReauthReasonKey] = reason.name
            preferences[ReauthDetectedAtEpochMillisKey] = detectedAtEpochMillis
        }
    }

    override suspend fun markReauthNotificationSent(notifiedAtEpochMillis: Long) {
        appContext.authSessionStatusDataStore.edit { preferences ->
            preferences[LastReauthNotifiedAtEpochMillisKey] = notifiedAtEpochMillis
        }
    }

    private fun Preferences.toAuthSessionStatus(): AuthSessionStatus = AuthSessionStatus(
        isSessionManaged = this[IsSessionManagedKey] ?: false,
        reauthRequired = this[ReauthRequiredKey] ?: false,
        reauthReason = this[ReauthReasonKey]?.let(::authErrorCodeOrNull),
        reauthDetectedAtEpochMillis = this[ReauthDetectedAtEpochMillisKey],
        lastReauthNotifiedAtEpochMillis = this[LastReauthNotifiedAtEpochMillisKey],
    )

    private companion object {
        val IsSessionManagedKey = booleanPreferencesKey("is_session_managed")
        val ReauthRequiredKey = booleanPreferencesKey("reauth_required")
        val ReauthReasonKey = stringPreferencesKey("reauth_reason")
        val ReauthDetectedAtEpochMillisKey = longPreferencesKey("reauth_detected_at_epoch_millis")
        val LastReauthNotifiedAtEpochMillisKey = longPreferencesKey("last_reauth_notified_at_epoch_millis")
    }
}

private fun authErrorCodeOrNull(value: String): AuthErrorCode? =
    runCatching { AuthErrorCode.valueOf(value) }.getOrNull()
