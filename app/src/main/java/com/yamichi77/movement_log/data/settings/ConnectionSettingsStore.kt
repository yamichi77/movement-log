package com.yamichi77.movement_log.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.connectionSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "connection_settings",
)

class ConnectionSettingsStore(
    private val appContext: Context,
) {
    private val data: Flow<Preferences> = appContext.connectionSettingsDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }

    val settings: Flow<ConnectionSettings> = data
        .map { preferences ->
            ConnectionSettings(
                baseUrl = preferences[BaseUrlKey] ?: ConnectionSettings.Default.baseUrl,
                uploadPath = preferences[UploadPathKey] ?: ConnectionSettings.Default.uploadPath,
            )
        }
        .distinctUntilChanged()

    val sendStatusText: Flow<String> = data
        .map { preferences -> preferences[SendStatusTextKey].orEmpty() }
        .distinctUntilChanged()

    suspend fun save(settings: ConnectionSettings) {
        appContext.connectionSettingsDataStore.edit { preferences ->
            preferences[BaseUrlKey] = settings.baseUrl
            preferences[UploadPathKey] = settings.uploadPath
        }
    }

    suspend fun saveSendStatusText(text: String) {
        appContext.connectionSettingsDataStore.edit { preferences ->
            preferences[SendStatusTextKey] = text
        }
    }

    private companion object {
        val BaseUrlKey = stringPreferencesKey("base_url")
        val UploadPathKey = stringPreferencesKey("upload_path")
        val SendStatusTextKey = stringPreferencesKey("send_status_text")
    }
}
