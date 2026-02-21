package com.yamichi77.movement_log.data.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TrackingSnapshot(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val activityStatus: String = TrackingActivityStatus.STILL,
    val updatedAtEpochMillis: Long? = null,
)

object TrackingStateStore {
    private val _isCollecting = MutableStateFlow(false)
    val isCollecting: StateFlow<Boolean> = _isCollecting.asStateFlow()

    private val _snapshot = MutableStateFlow(TrackingSnapshot())
    val snapshot: StateFlow<TrackingSnapshot> = _snapshot.asStateFlow()

    fun setCollecting(isCollecting: Boolean) {
        _isCollecting.value = isCollecting
    }

    fun updateActivity(activityStatus: String) {
        _snapshot.update { current ->
            current.copy(activityStatus = activityStatus)
        }
    }

    fun updateLocation(latitude: Double, longitude: Double, updatedAtEpochMillis: Long, activityStatus: String) {
        _snapshot.value = TrackingSnapshot(
            latitude = latitude,
            longitude = longitude,
            activityStatus = activityStatus,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }
}
