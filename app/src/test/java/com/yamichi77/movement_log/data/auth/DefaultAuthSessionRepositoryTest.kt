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
        val authApi = FakeBffAuthApi(
            refreshResult = RefreshAccessTokenResult(
                accessToken = "refreshed-token",
                sessionRotated = false,
            ),
        )
        val statusRepository = FakeAuthSessionStatusRepository()
        val repository = DefaultAuthSessionRepository(
            authApi = authApi,
            sessionStore = AuthSessionStore(),
            sessionStatusRepository = statusRepository,
        )

        repository.refreshAccessToken("https://portal.yamichi.com")

        assertEquals(1, statusRepository.markRefreshSucceededCalls)
    }

    @Test
    fun refreshAccessToken_marksReauthRequired_whenApiRequiresReauth() = runTest {
        val authApi = FakeBffAuthApi(
            refreshError = ReauthRequiredException(
                AuthErrorCode.SESSION_EXPIRED,
                "expired",
            ),
        )
        val statusRepository = FakeAuthSessionStatusRepository()
        val repository = DefaultAuthSessionRepository(
            authApi = authApi,
            sessionStore = AuthSessionStore(),
            sessionStatusRepository = statusRepository,
        )

        runCatching { repository.refreshAccessToken("https://portal.yamichi.com") }

        assertEquals(listOf(AuthErrorCode.SESSION_EXPIRED), statusRepository.markReauthRequiredReasons)
    }

    @Test
    fun refreshAccessToken_retriesThenMarksSessionInvalid_whenSessionInvalidContinues() = runTest {
        val authApi = FakeBffAuthApi(
            refreshError = SessionInvalidException("invalid"),
        )
        val statusRepository = FakeAuthSessionStatusRepository()
        val repository = DefaultAuthSessionRepository(
            authApi = authApi,
            sessionStore = AuthSessionStore(),
            sessionStatusRepository = statusRepository,
        )

        runCatching { repository.refreshAccessToken("https://portal.yamichi.com") }

        assertEquals(2, authApi.refreshCalls)
        assertEquals(listOf(AuthErrorCode.SESSION_INVALID), statusRepository.markReauthRequiredReasons)
        val event = AuthNavigationEventBus.event.value as? AuthNavigationEvent.RequireLogin
        assertTrue(event?.reason == AuthErrorCode.SESSION_INVALID)
    }

    private class FakeBffAuthApi(
        private val refreshResult: RefreshAccessTokenResult? = null,
        private val refreshError: Throwable? = null,
    ) : BffAuthApi {
        var refreshCalls: Int = 0

        override suspend fun refreshAccessToken(baseUrl: String): RefreshAccessTokenResult {
            refreshCalls += 1
            refreshError?.let { throw it }
            return refreshResult ?: RefreshAccessTokenResult(
                accessToken = "token",
                sessionRotated = false,
            )
        }

        override suspend fun logout(baseUrl: String) = Unit
    }

    private class FakeAuthSessionStatusRepository : AuthSessionStatusRepository {
        private val statusState = MutableStateFlow(AuthSessionStatus())
        override val status: Flow<AuthSessionStatus> = statusState.asStateFlow()

        var markRefreshSucceededCalls: Int = 0
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
