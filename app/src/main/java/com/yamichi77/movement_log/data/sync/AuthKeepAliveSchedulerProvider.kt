package com.yamichi77.movement_log.data.sync

import android.content.Context

object AuthKeepAliveSchedulerProvider {
    @Volatile
    private var instance: AuthKeepAliveScheduler? = null

    fun get(context: Context): AuthKeepAliveScheduler =
        instance ?: synchronized(this) {
            instance ?: WorkManagerAuthKeepAliveScheduler(
                appContext = context.applicationContext,
            ).also { instance = it }
        }

    fun setForTesting(scheduler: AuthKeepAliveScheduler?) {
        instance = scheduler
    }
}
