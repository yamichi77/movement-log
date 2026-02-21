package com.yamichi77.movement_log.data.repository

import kotlinx.coroutines.flow.Flow

data class HomeTrackingSnapshot(
    val isCollecting: Boolean,
    val activityStatus: String,
    val latitude: Double?,
    val longitude: Double?,
    val updatedAtEpochMillis: Long?,
    val logCount: Int,
)

data class HistoryMapPoint(
    val latitude: Double,
    val longitude: Double,
)

data class HistoryMapSnapshot(
    val points: List<HistoryMapPoint>,
    val logCount: Int,
    val lastLatitude: Double?,
    val lastLongitude: Double?,
    val lastUpdatedAtEpochMillis: Long?,
)

data class LogTableItem(
    val id: Long,
    val recordedAtEpochMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val activityStatus: String,
)

data class LogTableSnapshot(
    val items: List<LogTableItem>,
    val displayedCount: Int,
    val totalCount: Int,
)

interface MovementLogRepository {
    val homeTrackingSnapshot: Flow<HomeTrackingSnapshot>
    val historyMapSnapshot: Flow<HistoryMapSnapshot>
    val logTableSnapshot: Flow<LogTableSnapshot>

    suspend fun startCollecting()

    suspend fun stopCollecting()
}
