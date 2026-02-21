package com.yamichi77.movement_log.data.repository

import com.yamichi77.movement_log.data.local.MoveLogDao

class AndroidMovementLogUploadRepository(
    private val moveLogDao: MoveLogDao,
) : MovementLogUploadRepository {
    override suspend fun getPendingLogs(limit: Int): List<PendingUploadLog> =
        moveLogDao.getPendingUploads(limit).map { entity ->
            PendingUploadLog(
                id = entity.id,
                recordedAtEpochMillis = entity.recordedAtEpochMillis,
                latitude = entity.latitude,
                longitude = entity.longitude,
                accuracy = entity.accuracy,
                activityStatus = entity.activityStatus,
            )
        }

    override suspend fun markUploaded(ids: List<Long>) {
        if (ids.isEmpty()) return
        moveLogDao.markUploaded(ids)
    }
}
