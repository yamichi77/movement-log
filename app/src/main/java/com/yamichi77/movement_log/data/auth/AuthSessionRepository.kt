package com.yamichi77.movement_log.data.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AuthSessionRepository {
    val accessToken: StateFlow<String?>

    suspend fun getOrRefreshAccessToken(baseUrl: String): String

    suspend fun refreshAccessToken(baseUrl: String): RefreshAccessTokenResult

    suspend fun logout(baseUrl: String)

    fun setAccessToken(token: String?)

    fun clearTokens()
}

class DefaultAuthSessionRepository(
    private val authClient: OidcAuthClient,
    private val sessionStore: AuthSessionStore,
    private val sessionStatusRepository: AuthSessionStatusRepository,
) : AuthSessionRepository {
    override val accessToken: StateFlow<String?> = sessionStore.accessToken

    private val refreshMutex = Mutex()

    override suspend fun getOrRefreshAccessToken(baseUrl: String): String {
        accessToken.value?.let { return it }
        return refreshAccessToken(baseUrl).accessToken
    }

    override suspend fun refreshAccessToken(baseUrl: String): RefreshAccessTokenResult =
        refreshMutex.withLock {
            refreshWithPolicies(baseUrl)
        }

    override fun setAccessToken(token: String?) {
        sessionStore.setAccessToken(token)
    }

    override fun clearTokens() {
        sessionStore.clearTokens()
    }

    override suspend fun logout(baseUrl: String) {
        refreshMutex.withLock {
            runCatching { authClient.logout() }
            sessionStore.clearTokens()
            runCatching { sessionStatusRepository.clearSession() }
            AuthNavigationEventBus.clear()
        }
    }

    private suspend fun refreshWithPolicies(baseUrl: String): RefreshAccessTokenResult {
        var retriedSessionInvalid = false
        var temporaryFailureRetryIndex = 0

        while (true) {
            try {
                val result = authClient.refreshAccessToken(
                    currentAccessToken = accessToken.value,
                )
                sessionStore.setAccessToken(result.accessToken)
                runCatching { sessionStatusRepository.markRefreshSucceeded() }
                return result
            } catch (error: SessionInvalidException) {
                if (!retriedSessionInvalid) {
                    retriedSessionInvalid = true
                    continue
                }
                requireLogin(
                    reason = AuthErrorCode.SESSION_INVALID,
                    baseUrl = baseUrl,
                )
                throw error
            } catch (error: ReauthRequiredException) {
                requireLogin(
                    reason = error.errorCode,
                    baseUrl = baseUrl,
                )
                throw error
            } catch (error: RefreshTemporaryFailureException) {
                val delayMillis = TemporaryFailureRetryDelaysMs.getOrNull(temporaryFailureRetryIndex)
                if (delayMillis == null) {
                    throw error
                }
                temporaryFailureRetryIndex += 1
                if (delayMillis > 0) {
                    delay(delayMillis)
                }
            }
        }
    }

    private suspend fun requireLogin(reason: AuthErrorCode, baseUrl: String) {
        sessionStore.clearTokens()
        runCatching { sessionStatusRepository.markReauthRequired(reason = reason) }
        AuthNavigationEventBus.requireLogin(
            reason = reason,
            baseUrl = baseUrl.trim().takeIf { it.isNotBlank() },
        )
    }

    private companion object {
        val TemporaryFailureRetryDelaysMs = listOf(0L, 1000L, 2000L)
    }
}
