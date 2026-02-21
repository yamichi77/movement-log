package com.yamichi77.movement_log.data.sync

import com.yamichi77.movement_log.data.auth.AuthErrorCode
import com.yamichi77.movement_log.data.auth.AuthSessionReauthNotifier
import com.yamichi77.movement_log.data.auth.AuthSessionRepository
import com.yamichi77.movement_log.data.auth.AuthSessionStatus
import com.yamichi77.movement_log.data.auth.AuthSessionStatusRepository
import com.yamichi77.movement_log.data.auth.ReauthRequiredException
import com.yamichi77.movement_log.data.auth.RefreshAccessTokenResult
import com.yamichi77.movement_log.data.auth.RefreshTemporaryFailureException
import com.yamichi77.movement_log.data.repository.ConnectionSettingsRepository
import com.yamichi77.movement_log.data.repository.ConnectivityTestResult
import com.yamichi77.movement_log.data.settings.ConnectionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthKeepAliveUseCaseTest {
    @Test
    fun run_skips_whenSessionIsNotManaged() = runTest {
        val settingsRepository = FakeConnectionSettingsRepository(
            ConnectionSettings(baseUrl = "https://portal.yamichi.com", uploadPath = "/api/movelog"),
        )
        val authSessionRepository = FakeAuthSessionRepository()
        val statusRepository = FakeAuthSessionStatusRepository(
            AuthSessionStatus(isSessionManaged = false),
        )
        val notifier = FakeAuthSessionReauthNotifier()
        val useCase = AuthKeepAliveUseCase(
            connectionSettingsRepository = settingsRepository,
            authSessionRepository = authSessionRepository,
            sessionStatusRepository = statusRepository,
            reauthNotifier = notifier,
        )

        val result = useCase.run()

        assertTrue(result is AuthKeepAliveResult.Success)
        assertEquals(0, authSessionRepository.refreshCalls)
        assertEquals(0, notifier.notifyCalls)
    }

    @Test
    fun run_refreshes_whenSessionIsManaged() = runTest {
        val settingsRepository = FakeConnectionSettingsRepository(
            ConnectionSettings(baseUrl = "https://portal.yamichi.com", uploadPath = "/api/movelog"),
        )
        val authSessionRepository = FakeAuthSessionRepository()
        val statusRepository = FakeAuthSessionStatusRepository(
            AuthSessionStatus(isSessionManaged = true),
        )
        val notifier = FakeAuthSessionReauthNotifier()
        val useCase = AuthKeepAliveUseCase(
            connectionSettingsRepository = settingsRepository,
            authSessionRepository = authSessionRepository,
            sessionStatusRepository = statusRepository,
            reauthNotifier = notifier,
        )

        val result = useCase.run()

        assertTrue(result is AuthKeepAliveResult.Success)
        assertEquals(1, authSessionRepository.refreshCalls)
        assertEquals(0, notifier.notifyCalls)
    }

    @Test
    fun run_notifies_whenReauthRequiredAndRateLimitAllows() = runTest {
        val now = 1_700_000_000_000L
        val settingsRepository = FakeConnectionSettingsRepository(
            ConnectionSettings(baseUrl = "https://portal.yamichi.com", uploadPath = "/api/movelog"),
        )
        val authSessionRepository = FakeAuthSessionRepository()
        val statusRepository = FakeAuthSessionStatusRepository(
            AuthSessionStatus(
                isSessionManaged = true,
                reauthRequired = true,
                reauthReason = AuthErrorCode.SESSION_EXPIRED,
                lastReauthNotifiedAtEpochMillis = now - 43_200_001L,
            ),
        )
        val notifier = FakeAuthSessionReauthNotifier()
        val useCase = AuthKeepAliveUseCase(
            connectionSettingsRepository = settingsRepository,
            authSessionRepository = authSessionRepository,
            sessionStatusRepository = statusRepository,
            reauthNotifier = notifier,
            nowEpochMillis = { now },
        )

        val result = useCase.run()

        assertTrue(result is AuthKeepAliveResult.Success)
        assertEquals(1, notifier.notifyCalls)
        assertEquals(AuthErrorCode.SESSION_EXPIRED, notifier.lastReason)
        assertEquals(now, statusRepository.lastNotificationSentAt)
    }

    @Test
    fun run_suppressesNotify_whenWithinRateLimit() = runTest {
        val now = 1_700_000_000_000L
        val settingsRepository = FakeConnectionSettingsRepository(
            ConnectionSettings(baseUrl = "https://portal.yamichi.com", uploadPath = "/api/movelog"),
        )
        val authSessionRepository = FakeAuthSessionRepository()
        val statusRepository = FakeAuthSessionStatusRepository(
            AuthSessionStatus(
                isSessionManaged = true,
                reauthRequired = true,
                reauthReason = AuthErrorCode.SESSION_EXPIRED,
                lastReauthNotifiedAtEpochMillis = now - 60_000L,
            ),
        )
        val notifier = FakeAuthSessionReauthNotifier()
        val useCase = AuthKeepAliveUseCase(
            connectionSettingsRepository = settingsRepository,
            authSessionRepository = authSessionRepository,
            sessionStatusRepository = statusRepository,
            reauthNotifier = notifier,
            nowEpochMillis = { now },
        )

        val result = useCase.run()

        assertTrue(result is AuthKeepAliveResult.Success)
        assertEquals(0, notifier.notifyCalls)
        assertEquals(null, statusRepository.lastNotificationSentAt)
    }

    @Test
    fun run_returnsRetry_whenRefreshTemporaryFailureOccurs() = runTest {
        val settingsRepository = FakeConnectionSettingsRepository(
            ConnectionSettings(baseUrl = "https://portal.yamichi.com", uploadPath = "/api/movelog"),
        )
        val authSessionRepository = FakeAuthSessionRepository().apply {
            refreshError = RefreshTemporaryFailureException("temporary")
        }
        val statusRepository = FakeAuthSessionStatusRepository(
            AuthSessionStatus(isSessionManaged = true),
        )
        val notifier = FakeAuthSessionReauthNotifier()
        val useCase = AuthKeepAliveUseCase(
            connectionSettingsRepository = settingsRepository,
            authSessionRepository = authSessionRepository,
            sessionStatusRepository = statusRepository,
            reauthNotifier = notifier,
        )

        val result = useCase.run()

        assertTrue(result is AuthKeepAliveResult.Retry)
    }

    @Test
    fun run_notifies_whenRefreshRequiresReauth() = runTest {
        val settingsRepository = FakeConnectionSettingsRepository(
            ConnectionSettings(baseUrl = "https://portal.yamichi.com", uploadPath = "/api/movelog"),
        )
        val authSessionRepository = FakeAuthSessionRepository().apply {
            refreshError = ReauthRequiredException(
                AuthErrorCode.SESSION_STEP_UP_REQUIRED,
                "step-up",
            )
        }
        val statusRepository = FakeAuthSessionStatusRepository(
            AuthSessionStatus(isSessionManaged = true, reauthRequired = false),
        )
        val notifier = FakeAuthSessionReauthNotifier()
        val useCase = AuthKeepAliveUseCase(
            connectionSettingsRepository = settingsRepository,
            authSessionRepository = authSessionRepository,
            sessionStatusRepository = statusRepository,
            reauthNotifier = notifier,
        )

        val result = useCase.run()

        assertTrue(result is AuthKeepAliveResult.Success)
        assertEquals(1, notifier.notifyCalls)
        assertEquals(AuthErrorCode.SESSION_STEP_UP_REQUIRED, notifier.lastReason)
    }

    private class FakeConnectionSettingsRepository(
        initialSettings: ConnectionSettings,
    ) : ConnectionSettingsRepository {
        private val settingsState = MutableStateFlow(initialSettings)
        private val sendStatusState = MutableStateFlow("")

        override val settings: Flow<ConnectionSettings> = settingsState.asStateFlow()
        override val sendStatusText: Flow<String> = sendStatusState.asStateFlow()

        override suspend fun save(settings: ConnectionSettings) {
            settingsState.value = settings
        }

        override suspend fun saveSendStatusText(text: String) {
            sendStatusState.value = text
        }

        override suspend fun testConnectivity(settings: ConnectionSettings): ConnectivityTestResult =
            ConnectivityTestResult(sessionRotated = false)
    }

    private class FakeAuthSessionRepository : AuthSessionRepository {
        private val tokenState = MutableStateFlow<String?>("token")
        override val accessToken = tokenState.asStateFlow()

        var refreshCalls: Int = 0
        var refreshError: Throwable? = null

        override suspend fun getOrRefreshAccessToken(baseUrl: String): String = "token"

        override suspend fun refreshAccessToken(baseUrl: String): RefreshAccessTokenResult {
            refreshCalls += 1
            refreshError?.let { throw it }
            return RefreshAccessTokenResult(
                accessToken = "token",
                sessionRotated = false,
            )
        }

        override fun setAccessToken(token: String?) {
            tokenState.value = token
        }
    }

    private class FakeAuthSessionStatusRepository(
        initialStatus: AuthSessionStatus,
    ) : AuthSessionStatusRepository {
        private val statusState = MutableStateFlow(initialStatus)
        override val status: Flow<AuthSessionStatus> = statusState.asStateFlow()

        var lastNotificationSentAt: Long? = null

        override suspend fun markSessionEstablished() {
            statusState.value = statusState.value.copy(
                isSessionManaged = true,
                reauthRequired = false,
                reauthReason = null,
                reauthDetectedAtEpochMillis = null,
            )
        }

        override suspend fun markRefreshSucceeded() {
            statusState.value = statusState.value.copy(
                isSessionManaged = true,
                reauthRequired = false,
                reauthReason = null,
                reauthDetectedAtEpochMillis = null,
            )
        }

        override suspend fun markReauthRequired(reason: AuthErrorCode, detectedAtEpochMillis: Long) {
            statusState.value = statusState.value.copy(
                isSessionManaged = true,
                reauthRequired = true,
                reauthReason = reason,
                reauthDetectedAtEpochMillis = detectedAtEpochMillis,
            )
        }

        override suspend fun markReauthNotificationSent(notifiedAtEpochMillis: Long) {
            lastNotificationSentAt = notifiedAtEpochMillis
            statusState.value = statusState.value.copy(
                lastReauthNotifiedAtEpochMillis = notifiedAtEpochMillis,
            )
        }
    }

    private class FakeAuthSessionReauthNotifier : AuthSessionReauthNotifier {
        var notifyCalls: Int = 0
        var lastReason: AuthErrorCode? = null

        override fun notifyReauthRequired(reason: AuthErrorCode): Boolean {
            notifyCalls += 1
            lastReason = reason
            return true
        }
    }
}
