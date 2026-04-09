package com.yamichi77.movement_log.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class WorkManagerLogSyncScheduler(
    appContext: Context,
) : LogSyncScheduler {
    private val appContext = appContext.applicationContext
    private val workManager = WorkManager.getInstance(this.appContext)

    override fun start() {
        val periodicRequest = PeriodicWorkRequestBuilder<SyncMovementLogsWorker>(
            RepeatIntervalMinutes,
            TimeUnit.MINUTES,
        )
            .setConstraints(syncConstraints())
            .addTag(WorkerTag)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UniqueWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest,
        )
        enqueueImmediate(appContext)
    }

    override fun stop() {
        workManager.cancelUniqueWork(UniqueWorkName)
        workManager.cancelUniqueWork(ImmediateWorkName)
    }

    companion object {
        const val UniqueWorkName = "movement-log-upload-worker"
        const val WorkerTag = "movement-log-upload-tag"
        const val ImmediateWorkName = "movement-log-upload-immediate-worker"
        const val ImmediateWorkerTag = "movement-log-upload-immediate-tag"

        private const val RepeatIntervalMinutes = 15L

        internal fun enqueueImmediate(appContext: Context) {
            val request = OneTimeWorkRequestBuilder<SyncMovementLogsWorker>()
                .setConstraints(syncConstraints())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(WorkerTag)
                .addTag(ImmediateWorkerTag)
                .build()
            WorkManager.getInstance(appContext.applicationContext).enqueueUniqueWork(
                ImmediateWorkName,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        private fun syncConstraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
