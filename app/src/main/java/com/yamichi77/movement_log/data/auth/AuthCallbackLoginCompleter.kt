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
    private val authClient: OidcAuthClient,
    private val sessionStore: AuthSessionStore,
    private val sessionStatusRepository: AuthSessionStatusRepository,
) {
    suspend fun complete(baseUrl: String, payload: AuthCallbackPayload): RefreshAccessTokenResult {
        val result = when {
            payload.canCompleteWithApi -> completeViaApi(baseUrl, payload)
            payload.hasDirectAccessToken -> {
                val directToken = payload.accessToken.orEmpty()
                sessionStore.replaceSession(
                    StoredAuthSession(
                        accessToken = directToken,
                    ),
                )
                RefreshAccessTokenResult(
                    accessToken = directToken,
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
        if (payload.code.isNullOrBlank() && !payload.error.isNullOrBlank()) {
            val detail = payload.errorDescription?.takeIf { it.isNotBlank() } ?: payload.error
            throw AuthApiException("auth callback failed: $detail")
        }
        return authClient.completeAuthorization(
            OidcAuthorizationCallback(
                state = payload.state,
                code = payload.code,
                error = payload.error,
                errorDescription = payload.errorDescription,
            ),
        )
    }
}
