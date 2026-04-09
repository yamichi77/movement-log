package com.yamichi77.movement_log.data.auth

data class RefreshAccessTokenResult(
    val accessToken: String,
    val sessionRotated: Boolean,
)

data class CompleteLoginResult(
    val accessToken: String? = null,
    val sessionRotated: Boolean = false,
)

interface BffAuthApi {
    suspend fun completeLogin(
        baseUrl: String,
        state: String,
        code: String?,
        error: String?,
        errorDescription: String?,
    ): CompleteLoginResult

    suspend fun refreshAccessToken(baseUrl: String, accessToken: String?): RefreshAccessTokenResult

    suspend fun logout(baseUrl: String, accessToken: String?)
}

class ReauthRequiredException(
    val errorCode: AuthErrorCode,
    message: String,
) : RuntimeException(message)

class SessionInvalidException(
    message: String,
) : RuntimeException(message)

class RefreshTemporaryFailureException(
    message: String,
) : RuntimeException(message)

class AuthApiException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class UnauthorizedApiException(
    message: String,
) : RuntimeException(message)
