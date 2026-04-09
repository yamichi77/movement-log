package com.yamichi77.movement_log.data.repository

import com.yamichi77.movement_log.data.auth.AuthSessionRepository
import com.yamichi77.movement_log.data.auth.RefreshAccessTokenResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidConnectionSettingsRepositoryTest {
    @Test
    fun resolveConnectivityToken_usesCurrentToken_whenInMemoryTokenExists() = runTest {
        val repository = FakeAuthSessionRepository(
            accessTokenValue = "active-token",
        )

        val token = resolveConnectivityToken(
            authSessionRepository = repository,
            baseUrl = "https://portal.yamichi.com",
        )

        assertEquals("active-token", token)
        assertEquals(0, repository.getOrRefreshAccessTokenCalls)
    }

    @Test
    fun resolveConnectivityToken_refreshesToken_whenInMemoryTokenMissing() = runTest {
        val repository = FakeAuthSessionRepository(
            accessTokenValue = null,
            refreshedToken = "refreshed-token",
        )

        val token = resolveConnectivityToken(
            authSessionRepository = repository,
            baseUrl = "https://portal.yamichi.com",
        )

        assertEquals("refreshed-token", token)
        assertEquals(1, repository.getOrRefreshAccessTokenCalls)
    }

    private class FakeAuthSessionRepository(
        accessTokenValue: String?,
        private val refreshedToken: String = "fallback-token",
    ) : AuthSessionRepository {
        private val tokenState = MutableStateFlow(accessTokenValue)
        override val accessToken: StateFlow<String?> = tokenState.asStateFlow()
        var getOrRefreshAccessTokenCalls: Int = 0

        override suspend fun getOrRefreshAccessToken(baseUrl: String): String {
            getOrRefreshAccessTokenCalls += 1
            tokenState.value = refreshedToken
            return refreshedToken
        }

        override suspend fun refreshAccessToken(baseUrl: String): RefreshAccessTokenResult =
            RefreshAccessTokenResult(
                accessToken = refreshedToken,
                sessionRotated = false,
            )

        override suspend fun logout(baseUrl: String) = Unit

        override fun setAccessToken(token: String?) {
            tokenState.value = token
        }
    }
}
