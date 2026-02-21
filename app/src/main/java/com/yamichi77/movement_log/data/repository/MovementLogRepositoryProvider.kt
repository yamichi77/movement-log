package com.yamichi77.movement_log.data.repository

import android.content.Context
import com.yamichi77.movement_log.data.local.MovementLogDatabase
import com.yamichi77.movement_log.data.sync.LogSyncSchedulerProvider

object MovementLogRepositoryProvider {
    @Volatile
    private var instance: MovementLogRepository? = null

    fun get(context: Context): MovementLogRepository =
        instance ?: synchronized(this) {
            instance ?: AndroidMovementLogRepository(
                appContext = context.applicationContext,
                moveLogDao = MovementLogDatabase.getInstance(context).moveLogDao(),
                logSyncScheduler = LogSyncSchedulerProvider.get(context),
            ).also { instance = it }
        }
}
