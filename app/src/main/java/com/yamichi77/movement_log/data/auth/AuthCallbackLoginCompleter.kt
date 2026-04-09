package com.yamichi77.movement_log.data.auth

data class AuthCallbackPayload(
    val accessToken: String? = null,
    val state: String? = null,
    val code: String? = null,
    val error: String? = null,
    val errorDescription: String? = null,
) {
    val hasDirectAccessToken: Boolean
        get() = !accessToken.isNullOrBlank()

    val canCompleteWithApi: Boolean
        get() = !state.isNullOrBlank() && (!code.isNullOrBlank() || !error.isNullOrBlank())

    val hasUsableData: Boolean
        get() = hasDirectAccessToken || canCompleteWithApi
}

class AuthCallbackLoginCompleter(
    private val authApi: BffAuthApi,
    private val sessionStore: AuthSessionStore,
    private val sessionStatusRepository: AuthSessionStatusRepository,
) {
    suspend fun complete(baseUrl: String, payload: AuthCallbackPayload): RefreshAccessTokenResult {
        val result = when {
            payload.canCompleteWithApi -> completeViaApi(baseUrl, payload)
            payload.hasDirectAccessToken -> {
                RefreshAccessTokenResult(
                    accessToken = payload.accessToken.orEmpty(),
                    sessionRotated = false,
                )
            }
            else -> {
                throw AuthApiException("auth callback missing usable data")
            }
        }
        sessionStore.setAccessToken(result.accessToken)
        runCatching { sessionStatusRepository.markSessionEstablished() }
        return result
    }

    private suspend fun completeViaApi(
        baseUrl: String,
        payload: AuthCallbackPayload,
    ): RefreshAccessTokenResult {
        val state = payload.state?.trim()?.takeIf { it.isNotBlank() }
            ?: throw AuthApiException("auth callback missing state")
        if (payload.code.isNullOrBlank() && !payload.error.isNullOrBlank()) {
            val detail = payload.errorDescription?.takeIf { it.isNotBlank() } ?: payload.error
            throw AuthApiException("auth callback failed: $detail")
        }

        val completion = authApi.completeLogin(
            baseUrl = baseUrl,
            state = state,
            code = payload.code?.trim()?.takeIf { it.isNotBlank() },
            error = payload.error?.trim()?.takeIf { it.isNotBlank() },
            errorDescription = payload.errorDescription?.trim()?.takeIf { it.isNotBlank() },
        )
        val callbackToken = completion.accessToken?.trim()?.takeIf { it.isNotBlank() }
        return if (callbackToken != null) {
            RefreshAccessTokenResult(
                accessToken = callbackToken,
                sessionRotated = completion.sessionRotated,
            )
        } else {
            authApi.refreshAccessToken(baseUrl = baseUrl, accessToken = null)
        }
    }
}
