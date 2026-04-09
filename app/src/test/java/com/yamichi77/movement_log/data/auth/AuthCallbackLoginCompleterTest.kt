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
        val authClient = FakeOidcAuthClient()
        val sessionStore = AuthSessionStore()
        val statusRepository = FakeAuthSessionStatusRepository()
        val completer = AuthCallbackLoginCompleter(
            authClient = authClient,
            sessionStore = sessionStore,
            sessionStatusRepository = statusRepository,
        )

        val result = completer.complete(
            baseUrl = "https://portal.yamichi.com",
            payload = AuthCallbackPayload(accessToken = "direct-token"),
        )

        assertEquals("direct-token", result.accessToken)
        assertEquals("direct-token", sessionStore.accessToken.value)
        assertEquals(0, authClient.completeAuthorizationCalls)
        assertEquals(1, statusRepository.markSessionEstablishedCalls)
    }

    @Test
    fun complete_exchangesCodeAndStoresTokens_whenCallbackHasCode() = runTest {
        val authClient = FakeOidcAuthClient(
            completionResult = RefreshAccessTokenResult(
                accessToken = "refreshed-token",
                sessionRotated = true,
            ),
        )
        val sessionStore = AuthSessionStore()
        val statusRepository = FakeAuthSessionStatusRepository()
        val completer = AuthCallbackLoginCompleter(
            authClient = authClient,
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

        assertEquals(1, authClient.completeAuthorizationCalls)
        assertEquals("state-123", authClient.lastState)
        assertEquals("code-123", authClient.lastCode)
        assertEquals("refreshed-token", result.accessToken)
        assertEquals("refreshed-token", sessionStore.accessToken.value)
        assertEquals(1, statusRepository.markSessionEstablishedCalls)
    }

    @Test
    fun complete_throws_whenCallbackHasErrorWithoutCode() = runTest {
        val authClient = FakeOidcAuthClient()
        val sessionStore = AuthSessionStore()
        val statusRepository = FakeAuthSessionStatusRepository()
        val completer = AuthCallbackLoginCompleter(
            authClient = authClient,
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
        assertEquals(0, authClient.completeAuthorizationCalls)
        assertEquals(0, statusRepository.markSessionEstablishedCalls)
    }

    private class FakeOidcAuthClient(
        private val completionResult: RefreshAccessTokenResult = RefreshAccessTokenResult(
            accessToken = "fallback-token",
            sessionRotated = false,
        ),
    ) : OidcAuthClient {
        var completeAuthorizationCalls: Int = 0
        var lastState: String? = null
        var lastCode: String? = null

        override suspend fun createLoginUri() = android.net.Uri.parse("movementlog://auth/callback")

        override suspend fun completeAuthorization(callback: OidcAuthorizationCallback): RefreshAccessTokenResult {
            completeAuthorizationCalls += 1
            lastState = callback.state
            lastCode = callback.code
            return completionResult
        }

        override suspend fun refreshAccessToken(currentAccessToken: String?) = completionResult

        override suspend fun logout() = Unit
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
