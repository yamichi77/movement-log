package com.yamichi77.movement_log.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yamichi77.movement_log.data.auth.AuthSessionReauthNotifierProvider
import com.yamichi77.movement_log.data.auth.AuthSessionRepositoryProvider
import com.yamichi77.movement_log.data.auth.AuthSessionStatusRepositoryProvider
import com.yamichi77.movement_log.data.network.MovementApiGatewayProvider
import com.yamichi77.movement_log.data.repository.ConnectionSettingsRepositoryProvider

class AuthKeepAliveWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        Log.d(LogTag, "doWork: start")
        val useCase = AuthKeepAliveUseCase(
            connectionSettingsRepository = ConnectionSettingsRepositoryProvider.get(applicationContext),
            movementApiGateway = MovementApiGatewayProvider.get(applicationContext),
            authSessionRepository = AuthSessionRepositoryProvider.get(applicationContext),
            sessionStatusRepository = AuthSessionStatusRepositoryProvider.get(applicationContext),
            reauthNotifier = AuthSessionReauthNotifierProvider.get(applicationContext),
        )
        return when (val result = useCase.run()) {
            AuthKeepAliveResult.Success -> {
                Log.d(LogTag, "doWork: success")
                Result.success()
            }
            is AuthKeepAliveResult.Retry -> {
                Log.w(LogTag, "doWork: retry reason=${result.reason}")
                Result.retry()
            }
        }
    }

    private companion object {
        const val LogTag = "AuthKeepAliveWorker"
    }
}
