package com.yamichi77.movement_log.data.repository

data class PendingUploadLog(
    val id: Long,
    val recordedAtEpochMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val activityStatus: String,
)

interface MovementLogUploadRepository {
    suspend fun getPendingLogs(limit: Int): List<PendingUploadLog>

    suspend fun markUploaded(ids: List<Long>)
}
