package com.yamichi77.movement_log.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yamichi77.movement_log.data.auth.AuthSessionRepositoryProvider
import com.yamichi77.movement_log.data.network.MovementApiGatewayProvider
import com.yamichi77.movement_log.data.repository.ConnectionSettingsRepositoryProvider
import com.yamichi77.movement_log.data.repository.MovementLogUploadRepositoryProvider

class SyncMovementLogsWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val useCase = SyncMovementLogsUseCase(
            connectionSettingsRepository = ConnectionSettingsRepositoryProvider.get(applicationContext),
            movementLogUploadRepository = MovementLogUploadRepositoryProvider.get(applicationContext),
            movementApiGateway = MovementApiGatewayProvider.get(applicationContext),
            authSessionRepository = AuthSessionRepositoryProvider.get(applicationContext),
        )
        return when (useCase.sync()) {
            is SyncMovementLogsResult.Success,
            is SyncMovementLogsResult.Skipped,
            -> Result.success()
            is SyncMovementLogsResult.Retry -> Result.retry()
            is SyncMovementLogsResult.Failure -> Result.failure()
        }
    }
}
