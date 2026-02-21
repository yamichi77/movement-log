package com.yamichi77.movement_log.data.repository

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.yamichi77.movement_log.data.local.MoveLogDao
import com.yamichi77.movement_log.data.sync.LogSyncScheduler
import com.yamichi77.movement_log.data.tracking.TrackingStateStore
import com.yamichi77.movement_log.permission.PermissionUtils
import com.yamichi77.movement_log.service.GpsLoggerService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class AndroidMovementLogRepository(
    private val appContext: Context,
    private val moveLogDao: MoveLogDao,
    private val logSyncScheduler: LogSyncScheduler,
) : MovementLogRepository {

    override val homeTrackingSnapshot: Flow<HomeTrackingSnapshot> = combine(
        TrackingStateStore.isCollecting,
        TrackingStateStore.snapshot,
        moveLogDao.observeCount(),
    ) { isCollecting, snapshot, logCount ->
        HomeTrackingSnapshot(
            isCollecting = isCollecting,
            activityStatus = snapshot.activityStatus,
            latitude = snapshot.latitude,
            longitude = snapshot.longitude,
            updatedAtEpochMillis = snapshot.updatedAtEpochMillis,
            logCount = logCount,
        )
    }

    override val historyMapSnapshot: Flow<HistoryMapSnapshot> =
        moveLogDao.observeAllByRecordedAtAsc().combine(moveLogDao.observeCount()) { logs, logCount ->
            val lastLog = logs.lastOrNull()
            HistoryMapSnapshot(
                points = logs.map { log ->
                    HistoryMapPoint(
                        latitude = log.latitude,
                        longitude = log.longitude,
                    )
                },
                logCount = logCount,
                lastLatitude = lastLog?.latitude,
                lastLongitude = lastLog?.longitude,
                lastUpdatedAtEpochMillis = lastLog?.recordedAtEpochMillis,
            )
        }

    override val logTableSnapshot: Flow<LogTableSnapshot> =
        moveLogDao.observeLatestFifty().combine(moveLogDao.observeCount()) { logs, totalCount ->
            val items = logs.map { log ->
                LogTableItem(
                    id = log.id,
                    recordedAtEpochMillis = log.recordedAtEpochMillis,
                    latitude = log.latitude,
                    longitude = log.longitude,
                    activityStatus = log.activityStatus,
                )
            }
            LogTableSnapshot(
                items = items,
                displayedCount = items.size,
                totalCount = totalCount,
            )
        }

    override suspend fun startCollecting() {
        if (!PermissionUtils.hasRequiredPermissions(appContext)) return
        if (TrackingStateStore.isCollecting.value) return
        TrackingStateStore.setCollecting(true)
        logSyncScheduler.start()
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, GpsLoggerService::class.java),
        )
    }

    override suspend fun stopCollecting() {
        appContext.stopService(Intent(appContext, GpsLoggerService::class.java))
        logSyncScheduler.stop()
        TrackingStateStore.setCollecting(false)
    }
}
