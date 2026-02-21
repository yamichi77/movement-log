package com.yamichi77.movement_log.data.repository

import android.content.Context

object TrackingFrequencySettingsRepositoryProvider {
    @Volatile
    private var instance: TrackingFrequencySettingsRepository? = null

    fun get(context: Context): TrackingFrequencySettingsRepository =
        instance ?: synchronized(this) {
            instance ?: AndroidTrackingFrequencySettingsRepository(
                appContext = context.applicationContext,
            ).also { instance = it }
        }

    fun setForTesting(repository: TrackingFrequencySettingsRepository?) {
        instance = repository
    }
}
