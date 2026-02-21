package com.yamichi77.movement_log.data.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AuthSessionRepository {
    val accessToken: StateFlow<String?>

    suspend fun getOrRefreshAccessToken(baseUrl: String): String

    suspend fun refreshAccessToken(baseUrl: String): RefreshAccessTokenResult

    fun setAccessToken(token: String?)
}

class DefaultAuthSessionRepository(
    private val authApi: BffAuthApi,
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

    private suspend fun refreshWithPolicies(baseUrl: String): RefreshAccessTokenResult {
        var retriedSessionInvalid = false
        var temporaryFailureRetryIndex = 0

        while (true) {
            try {
                val result = authApi.refreshAccessToken(baseUrl)
                sessionStore.setAccessToken(result.accessToken)
                runCatching { sessionStatusRepository.markRefreshSucceeded() }
                return result
            } catch (error: SessionInvalidException) {
                if (!retriedSessionInvalid) {
                    retriedSessionInvalid = true
                    continue
                }
                requireLogin(AuthErrorCode.SESSION_INVALID)
                throw error
            } catch (error: ReauthRequiredException) {
                requireLogin(error.errorCode)
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

    private suspend fun requireLogin(reason: AuthErrorCode) {
        sessionStore.setAccessToken(null)
        runCatching { sessionStatusRepository.markReauthRequired(reason = reason) }
        AuthNavigationEventBus.requireLogin(reason)
    }

    private companion object {
        val TemporaryFailureRetryDelaysMs = listOf(0L, 1000L, 2000L)
    }
}
