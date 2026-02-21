package com.yamichi77.movement_log.data.repository

import com.yamichi77.movement_log.data.settings.ConnectionSettings
import kotlinx.coroutines.flow.Flow

data class ConnectivityTestResult(
    val sessionRotated: Boolean,
)

interface ConnectionSettingsRepository {
    val settings: Flow<ConnectionSettings>
    val sendStatusText: Flow<String>

    suspend fun save(settings: ConnectionSettings)

    suspend fun saveSendStatusText(text: String)

    suspend fun testConnectivity(settings: ConnectionSettings): ConnectivityTestResult
}
