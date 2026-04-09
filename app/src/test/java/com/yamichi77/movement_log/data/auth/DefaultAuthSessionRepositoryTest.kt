package com.yamichi77.movement_log.data.auth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAuthSessionRepositoryTest {
    @Before
    fun setUp() {
        AuthNavigationEventBus.clear()
    }

    @Test
    fun refreshAccessToken_marksRefreshSucceeded_whenApiSucceeds() = runTest {
        val authClient = FakeOidcAuthClient(
            refreshResult = RefreshAccessTokenResult(
                accessToken = "refreshed-token",
                sessionRotated = false,
            ),
        )
        val statusRepository = FakeAuthSessionStatusRepository()
        val repository = DefaultAuthSessionRepository(
            authClient = authClient,
            sessionStore = AuthSessionStore(),
            sessionStatusRepository = statusRepository,
        )

        repository.refreshAccessToken("https://portal.yamichi.com")

        assertEquals(1, statusRepository.markRefreshSucceededCalls)
    }

    @Test
    fun refreshAccessToken_marksReauthRequired_whenApiRequiresReauth() = runTest {
        val baseUrl = "https://portal.yamichi.com"
        val authClient = FakeOidcAuthClient(
            refreshError = ReauthRequiredException(
                AuthErrorCode.SESSION_EXPIRED,
                "expired",
            ),
        )
        val statusRepository = FakeAuthSessionStatusRepository()
        val repository = DefaultAuthSessionRepository(
            authClient = authClient,
            sessionStore = AuthSessionStore(),
            sessionStatusRepository = statusRepository,
        )

        runCatching { repository.refreshAccessToken(baseUrl) }

        assertEquals(listOf(AuthErrorCode.SESSION_EXPIRED), statusRepository.markReauthRequiredReasons)
        val event = AuthNavigationEventBus.event.value as? AuthNavigationEvent.RequireLogin
        assertEquals(AuthErrorCode.SESSION_EXPIRED, event?.reason)
        assertEquals(baseUrl, event?.baseUrl)
    }

    @Test
    fun refreshAccessToken_retriesThenMarksSessionInvalid_whenSessionInvalidContinues() = runTest {
        val baseUrl = "https://portal.yamichi.com"
        val authClient = FakeOidcAuthClient(
            refreshError = SessionInvalidException("invalid"),
        )
        val statusRepository = FakeAuthSessionStatusRepository()
        val repository = DefaultAuthSessionRepository(
            authClient = authClient,
            sessionStore = AuthSessionStore(),
            sessionStatusRepository = statusRepository,
        )

        runCatching { repository.refreshAccessToken(baseUrl) }

        assertEquals(2, authClient.refreshCalls)
        assertEquals(listOf(AuthErrorCode.SESSION_INVALID), statusRepository.markReauthRequiredReasons)
        val event = AuthNavigationEventBus.event.value as? AuthNavigationEvent.RequireLogin
        assertTrue(event?.reason == AuthErrorCode.SESSION_INVALID)
        assertEquals(baseUrl, event?.baseUrl)
    }

    @Test
    fun refreshAccessToken_sendsBearerToken_whenTokenExists() = runTest {
        val authClient = FakeOidcAuthClient(
            refreshResult = RefreshAccessTokenResult(
                accessToken = "new-token",
                sessionRotated = false,
            ),
        )
        val statusRepository = FakeAuthSessionStatusRepository()
        val repository = DefaultAuthSessionRepository(
            authClient = authClient,
            sessionStore = AuthSessionStore(),
            sessionStatusRepository = statusRepository,
        )
        repository.setAccessToken("active-token")

        repository.refreshAccessToken("https://portal.yamichi.com")

        assertEquals("active-token", authClient.lastRefreshAccessToken)
    }

    @Test
    fun logout_clearsLocalSessionState() = runTest {
        val authClient = FakeOidcAuthClient()
        val statusRepository = FakeAuthSessionStatusRepository()
        val repository = DefaultAuthSessionRepository(
            authClient = authClient,
            sessionStore = AuthSessionStore(),
            sessionStatusRepository = statusRepository,
        )
        repository.setAccessToken("active-token")

        repository.logout("https://portal.yamichi.com")

        assertEquals(1, authClient.logoutCalls)
        assertEquals(1, statusRepository.clearSessionCalls)
        assertEquals(null, repository.accessToken.value)
    }

    private class FakeOidcAuthClient(
        private val refreshResult: RefreshAccessTokenResult? = null,
        private val refreshError: Throwable? = null,
    ) : OidcAuthClient {
        var refreshCalls: Int = 0
        var logoutCalls: Int = 0
        var lastRefreshAccessToken: String? = null

        override suspend fun createLoginUri() = android.net.Uri.parse("movementlog://auth/callback")

        override suspend fun completeAuthorization(callback: OidcAuthorizationCallback): RefreshAccessTokenResult =
            refreshResult ?: RefreshAccessTokenResult(
                accessToken = "token",
                sessionRotated = false,
            )

        override suspend fun refreshAccessToken(currentAccessToken: String?): RefreshAccessTokenResult {
            refreshCalls += 1
            lastRefreshAccessToken = currentAccessToken
            refreshError?.let { throw it }
            return refreshResult ?: RefreshAccessTokenResult(
                accessToken = "token",
                sessionRotated = false,
            )
        }

        override suspend fun logout() {
            logoutCalls += 1
        }
    }

    private class FakeAuthSessionStatusRepository : AuthSessionStatusRepository {
        private val statusState = MutableStateFlow(AuthSessionStatus())
        override val status: Flow<AuthSessionStatus> = statusState.asStateFlow()

        var markRefreshSucceededCalls: Int = 0
        var clearSessionCalls: Int = 0
        val markReauthRequiredReasons = mutableListOf<AuthErrorCode>()

        override suspend fun markSessionEstablished() {
            statusState.value = statusState.value.copy(isSessionManaged = true)
        }

        override suspend fun markRefreshSucceeded() {
            markRefreshSucceededCalls += 1
            statusState.value = statusState.value.copy(
                isSessionManaged = true,
                reauthRequired = false,
                reauthReason = null,
                reauthDetectedAtEpochMillis = null,
            )
        }

        override suspend fun clearSession() {
            clearSessionCalls += 1
            statusState.value = AuthSessionStatus()
        }

        override suspend fun markReauthRequired(reason: AuthErrorCode, detectedAtEpochMillis: Long) {
            markReauthRequiredReasons += reason
            statusState.value = statusState.value.copy(
                isSessionManaged = true,
                reauthRequired = true,
                reauthReason = reason,
                reauthDetectedAtEpochMillis = detectedAtEpochMillis,
            )
        }

        override suspend fun markReauthNotificationSent(notifiedAtEpochMillis: Long) {
            statusState.value = statusState.value.copy(
                lastReauthNotifiedAtEpochMillis = notifiedAtEpochMillis,
            )
        }
    }
}
