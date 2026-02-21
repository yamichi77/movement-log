package com.yamichi77.movement_log.data.repository

import com.yamichi77.movement_log.data.settings.TrackingFrequencySettings
import kotlinx.coroutines.flow.Flow

interface TrackingFrequencySettingsRepository {
    val settings: Flow<TrackingFrequencySettings>

    suspend fun save(settings: TrackingFrequencySettings)
}
