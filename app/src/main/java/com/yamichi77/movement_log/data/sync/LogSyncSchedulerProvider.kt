package com.yamichi77.movement_log.data.sync

import android.content.Context

object LogSyncSchedulerProvider {
    @Volatile
    private var instance: LogSyncScheduler? = null

    fun get(context: Context): LogSyncScheduler =
        instance ?: synchronized(this) {
            instance ?: WorkManagerLogSyncScheduler(
                appContext = context.applicationContext,
            ).also { instance = it }
        }
}
