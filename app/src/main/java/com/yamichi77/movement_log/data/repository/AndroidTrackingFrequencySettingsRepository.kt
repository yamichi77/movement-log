package com.yamichi77.movement_log.data.repository

import android.content.Context
import com.yamichi77.movement_log.data.settings.TrackingFrequencySettings
import com.yamichi77.movement_log.data.settings.TrackingFrequencySettingsStore
import kotlinx.coroutines.flow.Flow

class AndroidTrackingFrequencySettingsRepository(
    appContext: Context,
) : TrackingFrequencySettingsRepository {
    private val store = TrackingFrequencySettingsStore(appContext.applicationContext)

    override val settings: Flow<TrackingFrequencySettings> = store.settings

    override suspend fun save(settings: TrackingFrequencySettings) {
        store.save(settings)
    }
}
