package com.yamichi77.movement_log.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HttpBffAuthApi(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : BffAuthApi {
    override suspend fun refreshAccessToken(baseUrl: String): RefreshAccessTokenResult =
        withContext(Dispatchers.IO) {
            val base = parseBaseUrl(baseUrl)
            val url = base.newBuilder()
                .addEncodedPathSegments("api/auth/token/refresh")
                .build()
            val request = Request.Builder()
                .url(url)
                .post("{}".toRequestBody(JsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> parseRefreshSuccess(body)
                    response.code == 401 -> throw parse401Error(body)
                    response.code == 503 && body.contains(
                        AuthErrorCode.REFRESH_TEMPORARY_FAILURE.name,
                    ) -> throw RefreshTemporaryFailureException(
                        "refresh temporary failure",
                    )
                    else -> throw AuthApiException("refresh failed: code=${response.code}")
                }
            }
        }

    override suspend fun logout(baseUrl: String, accessToken: String?) = withContext(Dispatchers.IO) {
        val base = parseBaseUrl(baseUrl)
        val url = base.newBuilder()
            .addEncodedPathSegments("api/auth/logout")
            .build()
        val requestBuilder = Request.Builder()
            .url(url)
            .post("{}".toRequestBody(JsonMediaType))
        accessToken?.trim()?.takeIf { it.isNotBlank() }?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
        }
        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (response.code != 204 && !response.isSuccessful) {
                throw AuthApiException("logout failed: code=${response.code}")
            }
        }
    }

    private fun parseRefreshSuccess(body: String): RefreshAccessTokenResult {
        val dto = runCatching {
            json.decodeFromString(RefreshTokenResponse.serializer(), body)
        }.getOrElse {
            throw AuthApiException("invalid refresh response", it)
        }
        if (dto.accessToken.isBlank()) {
            throw AuthApiException("refresh response missing access_token")
        }
        return RefreshAccessTokenResult(
            accessToken = dto.accessToken,
            sessionRotated = dto.sessionRotated,
        )
    }

    private fun parse401Error(body: String): Throwable {
        return when {
            body.contains(AuthErrorCode.SESSION_INVALID.name) -> {
                SessionInvalidException(AuthErrorCode.SESSION_INVALID.name)
            }
            body.contains(AuthErrorCode.SESSION_STEP_UP_REQUIRED.name) -> {
                ReauthRequiredException(
                    AuthErrorCode.SESSION_STEP_UP_REQUIRED,
                    AuthErrorCode.SESSION_STEP_UP_REQUIRED.name,
                )
            }
            body.contains(AuthErrorCode.SESSION_COMPROMISED_REAUTH_REQUIRED.name) -> {
                ReauthRequiredException(
                    AuthErrorCode.SESSION_COMPROMISED_REAUTH_REQUIRED,
                    AuthErrorCode.SESSION_COMPROMISED_REAUTH_REQUIRED.name,
                )
            }
            body.contains(AuthErrorCode.SESSION_EXPIRED.name) -> {
                ReauthRequiredException(
                    AuthErrorCode.SESSION_EXPIRED,
                    AuthErrorCode.SESSION_EXPIRED.name,
                )
            }
            else -> {
                ReauthRequiredException(AuthErrorCode.UNKNOWN, "unknown auth error")
            }
        }
    }

    private fun parseBaseUrl(baseUrl: String): HttpUrl {
        val normalized = baseUrl.trim()
            .ifBlank { throw AuthApiException("baseUrl is blank") }
            .let { raw ->
                if (raw.startsWith("http://") || raw.startsWith("https://")) {
                    raw
                } else {
                    "https://$raw"
                }
            }
        return normalized.toHttpUrlOrNull()
            ?: throw AuthApiException("invalid baseUrl: $baseUrl")
    }

    @Serializable
    private data class RefreshTokenResponse(
        @SerialName("access_token")
        val accessToken: String,
        @SerialName("session_rotated")
        val sessionRotated: Boolean = false,
    )

    private companion object {
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
    }
}
