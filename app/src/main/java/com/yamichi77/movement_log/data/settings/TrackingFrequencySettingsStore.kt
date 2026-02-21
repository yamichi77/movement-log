package com.yamichi77.movement_log.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.trackingFrequencySettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tracking_frequency_settings",
)

class TrackingFrequencySettingsStore(
    private val appContext: Context,
) {
    private val data: Flow<Preferences> = appContext.trackingFrequencySettingsDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }

    val settings: Flow<TrackingFrequencySettings> = data
        .map { preferences ->
            TrackingFrequencySettings(
                walkingSec = preferences[WalkingSecKey] ?: TrackingFrequencySettings.Default.walkingSec,
                runningSec = preferences[RunningSecKey] ?: TrackingFrequencySettings.Default.runningSec,
                bicycleSec = preferences[BicycleSecKey] ?: TrackingFrequencySettings.Default.bicycleSec,
                vehicleSec = preferences[VehicleSecKey] ?: TrackingFrequencySettings.Default.vehicleSec,
                stillSec = preferences[StillSecKey] ?: TrackingFrequencySettings.Default.stillSec,
            )
        }
        .distinctUntilChanged()

    suspend fun save(settings: TrackingFrequencySettings) {
        appContext.trackingFrequencySettingsDataStore.edit { preferences ->
            preferences[WalkingSecKey] = settings.walkingSec
            preferences[RunningSecKey] = settings.runningSec
            preferences[BicycleSecKey] = settings.bicycleSec
            preferences[VehicleSecKey] = settings.vehicleSec
            preferences[StillSecKey] = settings.stillSec
        }
    }

    private companion object {
        val WalkingSecKey = intPreferencesKey("walking_sec")
        val RunningSecKey = intPreferencesKey("running_sec")
        val BicycleSecKey = intPreferencesKey("bicycle_sec")
        val VehicleSecKey = intPreferencesKey("vehicle_sec")
        val StillSecKey = intPreferencesKey("still_sec")
    }
}
