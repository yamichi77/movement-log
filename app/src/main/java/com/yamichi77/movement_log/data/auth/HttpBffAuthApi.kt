package com.yamichi77.movement_log.data.auth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    override suspend fun completeLogin(
        baseUrl: String,
        state: String,
        code: String?,
        error: String?,
        errorDescription: String?,
    ): CompleteLoginResult =
        withContext(Dispatchers.IO) {
            val base = parseBaseUrl(baseUrl)
            val urlBuilder = base.newBuilder()
                .addEncodedPathSegments("api/auth/callback")
                .addQueryParameter("state", state)
            code?.let { urlBuilder.addQueryParameter("code", it) }
            error?.let { urlBuilder.addQueryParameter("error", it) }
            errorDescription?.let { urlBuilder.addQueryParameter("error_description", it) }
            val url = urlBuilder.build()
            Log.d(
                LogTag,
                "completeLogin: start hasCode=${!code.isNullOrBlank()} hasError=${!error.isNullOrBlank()}",
            )
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Log.d(
                    LogTag,
                    "completeLogin: response code=${response.code} bodyLength=${body.length}",
                )
                when {
                    response.isSuccessful -> parseCompleteLoginSuccess(body)
                    response.code == 401 -> {
                        val parsed = parse401Error(body)
                        Log.w(
                            LogTag,
                            "completeLogin: unauthorized errorType=${parsed.javaClass.simpleName}",
                        )
                        throw parsed
                    }
                    else -> {
                        Log.w(LogTag, "completeLogin: failed code=${response.code}")
                        throw AuthApiException("login completion failed: code=${response.code}")
                    }
                }
            }
        }

    override suspend fun refreshAccessToken(
        baseUrl: String,
        accessToken: String?,
    ): RefreshAccessTokenResult =
        withContext(Dispatchers.IO) {
            val base = parseBaseUrl(baseUrl)
            val url = base.newBuilder()
                .addEncodedPathSegments("api/auth/token/refresh")
                .build()
            val normalizedAccessToken = accessToken?.trim()?.takeIf { it.isNotBlank() }
            Log.d(
                LogTag,
                "refreshAccessToken: start hasAccessToken=${normalizedAccessToken != null}",
            )
            val requestBuilder = Request.Builder()
                .url(url)
                .post("{}".toRequestBody(JsonMediaType))
            normalizedAccessToken?.let { token ->
                requestBuilder.header("Authorization", "Bearer $token")
            }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Log.d(
                    LogTag,
                    "refreshAccessToken: response code=${response.code} bodyLength=${body.length}",
                )
                when {
                    response.isSuccessful -> {
                        val parsed = parseRefreshSuccess(body)
                        Log.d(
                            LogTag,
                            "refreshAccessToken: success sessionRotated=${parsed.sessionRotated}",
                        )
                        parsed
                    }
                    response.code == 401 -> {
                        val parsed = parse401Error(body)
                        Log.w(
                            LogTag,
                            "refreshAccessToken: unauthorized errorType=${parsed.javaClass.simpleName}",
                        )
                        throw parsed
                    }
                    response.code == 503 && body.contains(
                        AuthErrorCode.REFRESH_TEMPORARY_FAILURE.name,
                    ) -> {
                        Log.w(LogTag, "refreshAccessToken: temporary failure")
                        throw RefreshTemporaryFailureException("refresh temporary failure")
                    }
                    else -> {
                        Log.w(LogTag, "refreshAccessToken: failed code=${response.code}")
                        throw AuthApiException("refresh failed: code=${response.code}")
                    }
                }
            }
        }

    override suspend fun logout(baseUrl: String, accessToken: String?) = withContext(Dispatchers.IO) {
        val base = parseBaseUrl(baseUrl)
        val url = base.newBuilder()
            .addEncodedPathSegments("api/auth/logout")
            .build()
        val normalizedAccessToken = accessToken?.trim()?.takeIf { it.isNotBlank() }
        Log.d(
            LogTag,
            "logout: start hasAccessToken=${normalizedAccessToken != null}",
        )
        val requestBuilder = Request.Builder()
            .url(url)
            .post("{}".toRequestBody(JsonMediaType))
        normalizedAccessToken?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
        }
        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            Log.d(LogTag, "logout: response code=${response.code}")
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

    private fun parseCompleteLoginSuccess(body: String): CompleteLoginResult {
        val normalizedBody = body.trim()
        if (normalizedBody.isBlank()) return CompleteLoginResult()
        val jsonObject = runCatching {
            json.parseToJsonElement(normalizedBody).jsonObject
        }.getOrElse {
            return CompleteLoginResult()
        }
        return CompleteLoginResult(
            accessToken = jsonObject.stringOrNull("access_token")
                ?: jsonObject.stringOrNull("token")
                ?: jsonObject.stringOrNull("id_token")
                ?: jsonObject.stringOrNull("jwt"),
            sessionRotated = jsonObject.booleanOrFalse("session_rotated"),
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

    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

    private fun JsonObject.booleanOrFalse(key: String): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull ?: false

    @Serializable
    private data class RefreshTokenResponse(
        @SerialName("access_token")
        val accessToken: String,
        @SerialName("session_rotated")
        val sessionRotated: Boolean = false,
    )

    private companion object {
        const val LogTag = "HttpBffAuthApi"
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
    }
}
