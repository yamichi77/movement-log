package com.yamichi77.movement_log.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yamichi77.movement_log.data.auth.AuthSessionRepositoryProvider
import com.yamichi77.movement_log.data.auth.AuthSessionStatusRepositoryProvider
import com.yamichi77.movement_log.data.network.MovementApiGatewayProvider
import com.yamichi77.movement_log.data.repository.ConnectionSettingsRepositoryProvider
import com.yamichi77.movement_log.data.repository.MovementLogUploadRepositoryProvider

class SyncMovementLogsWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        Log.d(LogTag, "doWork: start")
        val useCase = SyncMovementLogsUseCase(
            connectionSettingsRepository = ConnectionSettingsRepositoryProvider.get(applicationContext),
            movementLogUploadRepository = MovementLogUploadRepositoryProvider.get(applicationContext),
            movementApiGateway = MovementApiGatewayProvider.get(applicationContext),
            authSessionRepository = AuthSessionRepositoryProvider.get(applicationContext),
            sessionStatusRepository = AuthSessionStatusRepositoryProvider.get(applicationContext),
        )
        return when (val result = useCase.sync()) {
            is SyncMovementLogsResult.Success -> {
                Log.d(LogTag, "doWork: success result=$result")
                if (result.uploadedCount >= SyncMovementLogsUseCase.DefaultUploadLimit) {
                    Log.d(LogTag, "doWork: scheduling immediate continuation uploadedCount=${result.uploadedCount}")
                    WorkManagerLogSyncScheduler.enqueueImmediate(applicationContext)
                }
                Result.success()
            }
            is SyncMovementLogsResult.Skipped -> {
                Log.d(LogTag, "doWork: success result=$result")
                Result.success()
            }
            is SyncMovementLogsResult.Retry -> {
                Log.w(LogTag, "doWork: retry reason=${result.reason}")
                Result.retry()
            }
            is SyncMovementLogsResult.Failure -> {
                Log.e(LogTag, "doWork: failure reason=${result.reason}")
                Result.failure()
            }
        }
    }

    private companion object {
        const val LogTag = "SyncMovementLogsWorker"
    }
}
