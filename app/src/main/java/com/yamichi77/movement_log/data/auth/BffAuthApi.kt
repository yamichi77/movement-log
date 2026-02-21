package com.yamichi77.movement_log.data.auth

data class RefreshAccessTokenResult(
    val accessToken: String,
    val sessionRotated: Boolean,
)

interface BffAuthApi {
    suspend fun refreshAccessToken(baseUrl: String): RefreshAccessTokenResult

    suspend fun logout(baseUrl: String)
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
