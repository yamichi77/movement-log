package com.yamichi77.movement_log.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class WorkManagerLogSyncScheduler(
    appContext: Context,
) : LogSyncScheduler {
    private val workManager = WorkManager.getInstance(appContext.applicationContext)

    override fun start() {
        val request = PeriodicWorkRequestBuilder<SyncMovementLogsWorker>(
            RepeatIntervalMinutes,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(WorkerTag)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UniqueWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun stop() {
        workManager.cancelUniqueWork(UniqueWorkName)
    }

    companion object {
        const val UniqueWorkName = "movement-log-upload-worker"
        const val WorkerTag = "movement-log-upload-tag"

        private const val RepeatIntervalMinutes = 15L
    }
}
