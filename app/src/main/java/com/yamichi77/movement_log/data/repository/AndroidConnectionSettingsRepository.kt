package com.yamichi77.movement_log.data.repository

import android.content.Context
import com.yamichi77.movement_log.data.auth.AuthSessionRepository
import com.yamichi77.movement_log.data.auth.AuthSessionRepositoryProvider
import com.yamichi77.movement_log.data.auth.AuthSessionStatusRepository
import com.yamichi77.movement_log.data.auth.AuthSessionStatusRepositoryProvider
import com.yamichi77.movement_log.data.auth.UnauthorizedApiException
import com.yamichi77.movement_log.data.auth.establishManagedSession
import com.yamichi77.movement_log.data.auth.stopManagedSessionSchedulers
import com.yamichi77.movement_log.data.network.MovementApiGateway
import com.yamichi77.movement_log.data.network.MovementApiGatewayProvider
import com.yamichi77.movement_log.data.settings.ConnectionSettings
import com.yamichi77.movement_log.data.settings.ConnectionSettingsStore
import com.yamichi77.movement_log.data.sync.AuthKeepAliveScheduler
import com.yamichi77.movement_log.data.sync.AuthKeepAliveSchedulerProvider
import com.yamichi77.movement_log.data.sync.LogSyncScheduler
import com.yamichi77.movement_log.data.sync.LogSyncSchedulerProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AndroidConnectionSettingsRepository(
    appContext: Context,
    private val apiGateway: MovementApiGateway =
        MovementApiGatewayProvider.get(appContext.applicationContext),
    private val authSessionRepository: AuthSessionRepository =
        AuthSessionRepositoryProvider.get(appContext.applicationContext),
    private val authSessionStatusRepository: AuthSessionStatusRepository =
        AuthSessionStatusRepositoryProvider.get(appContext.applicationContext),
    private val authKeepAliveScheduler: AuthKeepAliveScheduler =
        AuthKeepAliveSchedulerProvider.get(appContext.applicationContext),
    private val logSyncScheduler: LogSyncScheduler =
        LogSyncSchedulerProvider.get(appContext.applicationContext),
) : ConnectionSettingsRepository {
    private val store = ConnectionSettingsStore(appContext.applicationContext)

    override val settings: Flow<ConnectionSettings> = store.settings
    override val sendStatusText: Flow<String> = store.sendStatusText

    override suspend fun save(settings: ConnectionSettings) {
        store.save(settings)
    }

    override suspend fun saveSendStatusText(text: String) {
        store.saveSendStatusText(text)
    }

    override suspend fun testConnectivity(settings: ConnectionSettings): ConnectivityTestResult {
        val initialToken = resolveConnectivityToken(
            authSessionRepository = authSessionRepository,
            baseUrl = settings.baseUrl,
        )
        return try {
            apiGateway.verifyToken(
                baseUrl = settings.baseUrl,
                token = initialToken,
            )
            save(settings)
            establishManagedSession(
                authSessionStatusRepository = authSessionStatusRepository,
                authKeepAliveScheduler = authKeepAliveScheduler,
                logSyncScheduler = logSyncScheduler,
            )
            ConnectivityTestResult(sessionRotated = false)
        } catch (_: UnauthorizedApiException) {
            val refreshResult = authSessionRepository.refreshAccessToken(settings.baseUrl)
            apiGateway.verifyToken(
                baseUrl = settings.baseUrl,
                token = refreshResult.accessToken,
            )
            save(settings)
            establishManagedSession(
                authSessionStatusRepository = authSessionStatusRepository,
                authKeepAliveScheduler = authKeepAliveScheduler,
                logSyncScheduler = logSyncScheduler,
            )
            ConnectivityTestResult(sessionRotated = refreshResult.sessionRotated)
        }
    }

    override suspend fun logout() {
        val baseUrl = settings.first().baseUrl
        authSessionRepository.logout(baseUrl)
        stopManagedSessionSchedulers(
            authKeepAliveScheduler = authKeepAliveScheduler,
            logSyncScheduler = logSyncScheduler,
        )
    }
}

internal suspend fun resolveConnectivityToken(
    authSessionRepository: AuthSessionRepository,
    baseUrl: String,
): String = authSessionRepository.accessToken.value
    ?.takeIf { it.isNotBlank() }
    ?: authSessionRepository.getOrRefreshAccessToken(baseUrl)
