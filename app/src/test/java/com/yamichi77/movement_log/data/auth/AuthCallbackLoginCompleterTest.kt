package com.yamichi77.movement_log.data.auth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthCallbackLoginCompleterTest {
    @Test
    fun complete_usesDirectAccessToken_whenPresent() = runTest {
        val authApi = FakeBffAuthApi()
        val sessionStore = AuthSessionStore()
        val statusRepository = FakeAuthSessionStatusRepository()
        val completer = AuthCallbackLoginCompleter(
            authApi = authApi,
            sessionStore = sessionStore,
            sessionStatusRepository = statusRepository,
        )

        val result = completer.complete(
            baseUrl = "https://portal.yamichi.com",
            payload = AuthCallbackPayload(accessToken = "direct-token"),
        )

        assertEquals("direct-token", result.accessToken)
        assertEquals("direct-token", sessionStore.accessToken.value)
        assertEquals(0, authApi.completeLoginCalls)
        assertEquals(0, authApi.refreshCalls)
        assertEquals(1, statusRepository.markSessionEstablishedCalls)
    }

    @Test
    fun complete_exchangesCodeAndRefreshes_whenCallbackBodyHasNoToken() = runTest {
        val authApi = FakeBffAuthApi(
            completeLoginResult = CompleteLoginResult(),
            refreshResult = RefreshAccessTokenResult(
                accessToken = "refreshed-token",
                sessionRotated = true,
            ),
        )
        val sessionStore = AuthSessionStore()
        val statusRepository = FakeAuthSessionStatusRepository()
        val completer = AuthCallbackLoginCompleter(
            authApi = authApi,
            sessionStore = sessionStore,
            sessionStatusRepository = statusRepository,
        )

        val result = completer.complete(
            baseUrl = "https://portal.yamichi.com",
            payload = AuthCallbackPayload(
                state = "state-123",
                code = "code-123",
            ),
        )

        assertEquals(1, authApi.completeLoginCalls)
        assertEquals(1, authApi.refreshCalls)
        assertEquals("state-123", authApi.lastState)
        assertEquals("code-123", authApi.lastCode)
        assertEquals("refreshed-token", result.accessToken)
        assertEquals("refreshed-token", sessionStore.accessToken.value)
        assertEquals(1, statusRepository.markSessionEstablishedCalls)
    }

    @Test
    fun complete_throws_whenCallbackHasErrorWithoutCode() = runTest {
        val authApi = FakeBffAuthApi()
        val sessionStore = AuthSessionStore()
        val statusRepository = FakeAuthSessionStatusRepository()
        val completer = AuthCallbackLoginCompleter(
            authApi = authApi,
            sessionStore = sessionStore,
            sessionStatusRepository = statusRepository,
        )

        val error = runCatching {
            completer.complete(
                baseUrl = "https://portal.yamichi.com",
                payload = AuthCallbackPayload(
                    state = "state-123",
                    error = "access_denied",
                    errorDescription = "user cancelled",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is AuthApiException)
        assertNull(sessionStore.accessToken.value)
        assertEquals(0, authApi.completeLoginCalls)
        assertEquals(0, statusRepository.markSessionEstablishedCalls)
    }

    private class FakeBffAuthApi(
        private val completeLoginResult: CompleteLoginResult = CompleteLoginResult(),
        private val refreshResult: RefreshAccessTokenResult = RefreshAccessTokenResult(
            accessToken = "fallback-token",
            sessionRotated = false,
        ),
    ) : BffAuthApi {
        var completeLoginCalls: Int = 0
        var refreshCalls: Int = 0
        var lastState: String? = null
        var lastCode: String? = null

        override suspend fun completeLogin(
            baseUrl: String,
            state: String,
            code: String?,
            error: String?,
            errorDescription: String?,
        ): CompleteLoginResult {
            completeLoginCalls += 1
            lastState = state
            lastCode = code
            return completeLoginResult
        }

        override suspend fun refreshAccessToken(
            baseUrl: String,
            accessToken: String?,
        ): RefreshAccessTokenResult {
            refreshCalls += 1
            return refreshResult
        }

        override suspend fun logout(baseUrl: String, accessToken: String?) = Unit
    }

    private class FakeAuthSessionStatusRepository : AuthSessionStatusRepository {
        private val statusState = MutableStateFlow(AuthSessionStatus())
        override val status: Flow<AuthSessionStatus> = statusState.asStateFlow()

        var markSessionEstablishedCalls: Int = 0

        override suspend fun markSessionEstablished() {
            markSessionEstablishedCalls += 1
            statusState.value = AuthSessionStatus(isSessionManaged = true)
        }

        override suspend fun markRefreshSucceeded() = Unit

        override suspend fun clearSession() {
            statusState.value = AuthSessionStatus()
        }

        override suspend fun markReauthRequired(reason: AuthErrorCode, detectedAtEpochMillis: Long) {
            statusState.value = statusState.value.copy(
                reauthRequired = true,
                reauthReason = reason,
                reauthDetectedAtEpochMillis = detectedAtEpochMillis,
            )
        }

        override suspend fun markReauthNotificationSent(notifiedAtEpochMillis: Long) = Unit
    }
}
