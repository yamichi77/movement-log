package com.yamichi77.movement_log.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yamichi77.movement_log.data.auth.AuthSessionReauthNotifierProvider
import com.yamichi77.movement_log.data.auth.AuthSessionRepositoryProvider
import com.yamichi77.movement_log.data.auth.AuthSessionStatusRepositoryProvider
import com.yamichi77.movement_log.data.repository.ConnectionSettingsRepositoryProvider

class AuthKeepAliveWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val useCase = AuthKeepAliveUseCase(
            connectionSettingsRepository = ConnectionSettingsRepositoryProvider.get(applicationContext),
            authSessionRepository = AuthSessionRepositoryProvider.get(applicationContext),
            sessionStatusRepository = AuthSessionStatusRepositoryProvider.get(applicationContext),
            reauthNotifier = AuthSessionReauthNotifierProvider.get(applicationContext),
        )
        return when (useCase.run()) {
            AuthKeepAliveResult.Success -> Result.success()
            is AuthKeepAliveResult.Retry -> Result.retry()
        }
    }
}
