package com.yamichi77.movement_log.data.repository

import android.content.Context
import com.yamichi77.movement_log.data.local.MovementLogDatabase

object MovementLogUploadRepositoryProvider {
    @Volatile
    private var instance: MovementLogUploadRepository? = null

    fun get(context: Context): MovementLogUploadRepository =
        instance ?: synchronized(this) {
            instance ?: AndroidMovementLogUploadRepository(
                moveLogDao = MovementLogDatabase.getInstance(context).moveLogDao(),
            ).also { instance = it }
        }
}
