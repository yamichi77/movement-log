package com.yamichi77.movement_log.data.sync

import com.yamichi77.movement_log.data.auth.AuthSessionRepository
import com.yamichi77.movement_log.data.auth.RefreshAccessTokenResult
import com.yamichi77.movement_log.data.auth.UnauthorizedApiException
import com.yamichi77.movement_log.data.network.MovementApiException
import com.yamichi77.movement_log.data.network.MovementApiGateway
import com.yamichi77.movement_log.data.network.MovementLogUploadRequest
import com.yamichi77.movement_log.data.repository.ConnectionSettingsRepository
import com.yamichi77.movement_log.data.repository.ConnectivityTestResult
import com.yamichi77.movement_log.data.repository.MovementLogUploadRepository
import com.yamichi77.movement_log.data.repository.PendingUploadLog
import com.yamichi77.movement_log.data.settings.ConnectionSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncMovementLogsUseCaseTest {
    @Test
    fun sync_returnsSuccess_whenNoPendingLogs() = runTest {
        val settingsRepository = FakeConnectionSettingsRepository(
            ConnectionSettings(
                baseUrl = "https://portal.yamichi.com",
                uploadPath = "/api/movelog",
            ),
        )
        val uploadRepository = FakeMovementLogUploadRepository(emptyList())
        val gateway = FakeMovementApiGateway()
        val authSessionRepository = FakeAuthSessionRepository(initialToken = "issued-token")
        val useCase = SyncMovementLogsUseCase(
            connectionSettingsRepository = settingsRepository,
            movementLogUploadRepository = uploadRepository,
            movementApiGateway = gateway,
            authSessionRepository = authSessionRepository,
            nowEpochMillis = { 1_700_000_000_000L },
        )

        val result = useCase.sync()

        assertTrue(result is SyncMovementLogsResult.Success)
        assertEquals(0, (result as SyncMovementLogsResult.Success).uploadedCount)
        assertTrue(settingsRepository.lastSendStatus.orEmpty().contains("未送信ログはありません"))
    }

    @Test
    fun sync_refreshesTokenAndRetries_whenUnauthorizedOnUpload() = runTest {
        val settingsRepository = FakeConnectionSettingsRepository(
            ConnectionSettings(
                baseUrl = "https://portal.yamichi.com",
                uploadPath = "/api/movelog",
            ),
        )
        val uploadRepository = FakeMovementLogUploadRepository(
            listOf(
                PendingUploadLog(
                    id = 10,
                    recordedAtEpochMillis = 1_700_000_000_000L,
                    latitude = 35.0,
                    longitude = 139.0,
                    accuracy = 3.0f,
                    activityStatus = "WALKING",
                ),
            ),
        )
        val gateway = FakeMovementApiGateway().apply {
            unauthorizedOnce = true
        }
        val authSessionRepository = FakeAuthSessionRepository(initialToken = "expired-token").apply {
            refreshedToken = "refreshed-token"
        }
        val useCase = SyncMovementLogsUseCase(
            connectionSettingsRepository = settingsRepository,
            movementLogUploadRepository = uploadRepository,
            movementApiGateway = gateway,
            authSessionRepository = authSessionRepository,
            nowEpochMillis = { 1_700_000_000_000L },
        )

        val result = useCase.sync()

        assertTrue(result is SyncMovementLogsResult.Success)
        assertEquals(listOf(10L), uploadRepository.markedUploadedIds)
        assertEquals(1, authSessionRepository.refreshCalls)
        assertEquals(2, gateway.uploadAttemptCount)
        assertEquals(1, gateway.uploadCalls.size)
    }

    @Test
    fun sync_returnsSkipped_whenSettingsAreInvalid() = runTest {
        val settingsRepository = FakeConnectionSettingsRepository(
            ConnectionSettings(
                baseUrl = "",
                uploadPath = "/api/movelog",
            ),
        )
        val uploadRepository = FakeMovementLogUploadRepository(emptyList())
        val gateway = FakeMovementApiGateway()
        val authSessionRepository = FakeAuthSessionRepository(initialToken = "token")
        val useCase = SyncMovementLogsUseCase(
            connectionSettingsRepository = settingsRepository,
            movementLogUploadRepository = uploadRepository,
            movementApiGateway = gateway,
            authSessionRepository = authSessionRepository,
        )

        val result = useCase.sync()

        assertTrue(result is SyncMovementLogsResult.Skipped)
        assertTrue(settingsRepository.lastSendStatus.orEmpty().contains("Base URLが未設定"))
    }

    @Test
    fun sync_returnsRetry_whenUploadFailsWithMovementApiException() = runTest {
        val settingsRepository = FakeConnectionSettingsRepository(
            ConnectionSettings(
                baseUrl = "https://portal.yamichi.com",
                uploadPath = "/api/movelog",
            ),
        )
        val uploadRepository = FakeMovementLogUploadRepository(
            listOf(
                PendingUploadLog(
                    id = 11,
                    recordedAtEpochMillis = 1_700_000_000_000L,
                    latitude = 35.0,
                    longitude = 139.0,
                    accuracy = 3.0f,
                    activityStatus = "WALKING",
                ),
            ),
        )
        val gateway = FakeMovementApiGateway().apply {
            uploadError = MovementApiException("network error")
        }
        val authSessionRepository = FakeAuthSessionRepository(initialToken = "valid-token")
        val useCase = SyncMovementLogsUseCase(
            connectionSettingsRepository = settingsRepository,
            movementLogUploadRepository = uploadRepository,
            movementApiGateway = gateway,
            authSessionRepository = authSessionRepository,
        )

        val result = useCase.sync()

        assertTrue(result is SyncMovementLogsResult.Retry)
        assertTrue(uploadRepository.markedUploadedIds.isEmpty())
    }

    private class FakeConnectionSettingsRepository(
        initialSettings: ConnectionSettings,
    ) : ConnectionSettingsRepository {
        private val settingsState = MutableStateFlow(initialSettings)
        private val sendStatusState = MutableStateFlow("")

        var lastSendStatus: String? = null

        override val settings: Flow<ConnectionSettings> = settingsState.asStateFlow()
        override val sendStatusText: Flow<String> = sendStatusState.asStateFlow()

        override suspend fun save(settings: ConnectionSettings) {
            settingsState.value = settings
        }

        override suspend fun saveSendStatusText(text: String) {
            lastSendStatus = text
            sendStatusState.value = text
        }

        override suspend fun testConnectivity(settings: ConnectionSettings): ConnectivityTestResult {
            settingsState.value = settings
            return ConnectivityTestResult(sessionRotated = false)
        }

        override suspend fun logout() = Unit
    }

    private class FakeMovementLogUploadRepository(
        private val pendingLogs: List<PendingUploadLog>,
    ) : MovementLogUploadRepository {
        val markedUploadedIds = mutableListOf<Long>()

        override suspend fun getPendingLogs(limit: Int): List<PendingUploadLog> = pendingLogs

        override suspend fun markUploaded(ids: List<Long>) {
            markedUploadedIds += ids
        }
    }

    private class FakeMovementApiGateway : MovementApiGateway {
        var unauthorizedOnce: Boolean = false
        var uploadError: Throwable? = null
        var uploadAttemptCount: Int = 0
        val uploadCalls = mutableListOf<MovementLogUploadRequest>()

        override suspend fun verifyToken(baseUrl: String, token: String) = Unit

        override suspend fun uploadMovementLog(
            baseUrl: String,
            uploadPath: String,
            token: String,
            request: MovementLogUploadRequest,
        ) {
            uploadAttemptCount += 1
            if (unauthorizedOnce) {
                unauthorizedOnce = false
                throw UnauthorizedApiException("unauthorized")
            }
            uploadError?.let { throw it }
            uploadCalls += request
        }
    }

    private class FakeAuthSessionRepository(
        initialToken: String?,
    ) : AuthSessionRepository {
        private val tokenState = MutableStateFlow(initialToken)
        override val accessToken = tokenState.asStateFlow()

        var refreshedToken: String = initialToken ?: "new-token"
        var refreshCalls: Int = 0

        override suspend fun getOrRefreshAccessToken(baseUrl: String): String =
            tokenState.value ?: refreshedToken.also { tokenState.value = it }

        override suspend fun refreshAccessToken(baseUrl: String): RefreshAccessTokenResult {
            refreshCalls += 1
            tokenState.value = refreshedToken
            return RefreshAccessTokenResult(
                accessToken = refreshedToken,
                sessionRotated = false,
            )
        }

        override fun setAccessToken(token: String?) {
            tokenState.value = token
        }

        override suspend fun logout(baseUrl: String) {
            tokenState.value = null
        }
    }
}
