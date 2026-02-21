package com.yamichi77.movement_log.data.repository

import android.content.Context

object ConnectionSettingsRepositoryProvider {
    @Volatile
    private var instance: ConnectionSettingsRepository? = null

    fun get(context: Context): ConnectionSettingsRepository =
        instance ?: synchronized(this) {
            instance ?: AndroidConnectionSettingsRepository(
                appContext = context.applicationContext,
            ).also { instance = it }
        }

    fun setForTesting(repository: ConnectionSettingsRepository?) {
        instance = repository
    }
}
