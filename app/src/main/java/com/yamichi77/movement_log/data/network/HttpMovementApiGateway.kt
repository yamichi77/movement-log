package com.yamichi77.movement_log.data.network

import android.util.Log
import com.yamichi77.movement_log.data.auth.UnauthorizedApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HttpMovementApiGateway(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : MovementApiGateway {
    override suspend fun verifyToken(
        baseUrl: String,
        token: String,
    ) = withContext(Dispatchers.IO) {
        val requestUrl = parseBaseUrl(baseUrl).newBuilder()
            .addEncodedPathSegments("api/auth/token")
            .build()
        val request = Request.Builder()
            .url(requestUrl)
            .post("{}".toRequestBody(JsonMediaType))
            .header("Authorization", "Bearer $token")
            .build()
        logDebug("verifyToken: url=$requestUrl")

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            logDebug(
                "verifyToken: response code=${response.code} body=${responseBody.take(LogBodyPreviewLength)}",
            )
            if (response.code == 401) {
                throw UnauthorizedApiException(
                    buildFailureMessage(
                        prefix = "token verification failed",
                        code = response.code,
                        responseBody = responseBody,
                    ),
                )
            }
            if (!response.isSuccessful) {
                throw MovementApiException(
                    buildFailureMessage(
                        prefix = "token verification failed",
                        code = response.code,
                        responseBody = responseBody,
                    ),
                )
            }
        }
    }

    override suspend fun uploadMovementLog(
        baseUrl: String,
        uploadPath: String,
        token: String,
        request: MovementLogUploadRequest,
    ) = withContext(Dispatchers.IO) {
        val requestUrl = buildUploadUrl(baseUrl = parseBaseUrl(baseUrl), uploadPath = uploadPath)
        val bodyJson = buildUploadBody(request)
        val httpRequest = Request.Builder()
            .url(requestUrl)
            .post(bodyJson.toRequestBody(JsonMediaType))
            .header("Authorization", "Bearer $token")
            .build()
        logDebug("uploadMovementLog: url=$requestUrl body=$bodyJson")

        client.newCall(httpRequest).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            logDebug(
                "uploadMovementLog: response code=${response.code} body=${responseBody.take(LogBodyPreviewLength)}",
            )
            if (response.code == 401) {
                throw UnauthorizedApiException(
                    buildFailureMessage(
                        prefix = "upload failed",
                        code = response.code,
                        responseBody = responseBody,
                    ),
                )
            }
            if (response.code == 409) {
                throw DuplicateMovementLogException(
                    buildFailureMessage(
                        prefix = "upload failed",
                        code = response.code,
                        responseBody = responseBody,
                    ),
                )
            }
            if (!response.isSuccessful) {
                throw MovementApiException(
                    buildFailureMessage(
                        prefix = "upload failed",
                        code = response.code,
                        responseBody = responseBody,
                    ),
                )
            }
        }
    }

    private fun buildUploadBody(request: MovementLogUploadRequest): String {
        val payload: JsonObject = buildJsonObject {
            put("SeqTime", JsonPrimitive(request.seqTime))
            put("Latitude", JsonPrimitive(request.latitude))
            put("Longitude", JsonPrimitive(request.longitude))
            put("Accuracy", JsonPrimitive(request.accuracy))
            put("Activity", JsonPrimitive(request.activity))
        }
        return json.encodeToString(JsonObject.serializer(), payload)
    }

    private fun parseBaseUrl(baseUrl: String): HttpUrl {
        val normalized = baseUrl.trim()
            .ifBlank { throw MovementApiException("baseUrl is blank") }
            .let { raw ->
                if (raw.startsWith("http://") || raw.startsWith("https://")) {
                    raw
                } else {
                    "https://$raw"
                }
            }
        return normalized.toHttpUrlOrNull()
            ?: throw MovementApiException("invalid baseUrl: $baseUrl")
    }

    private fun buildUploadUrl(baseUrl: HttpUrl, uploadPath: String): HttpUrl {
        val normalizedPath = uploadPath.trim()
            .ifBlank { "/api/movelog" }
            .let { path ->
                if (path.startsWith("/")) {
                    path
                } else {
                    "/$path"
                }
            }
        return baseUrl.resolve(normalizedPath)
            ?: throw MovementApiException("invalid uploadPath: $uploadPath")
    }

    private fun buildFailureMessage(
        prefix: String,
        code: Int,
        responseBody: String,
    ): String {
        val normalizedBody = responseBody.trim()
        return if (normalizedBody.isBlank()) {
            "$prefix: code=$code"
        } else {
            "$prefix: code=$code body=$normalizedBody"
        }
    }

    private fun logDebug(message: String) {
        runCatching { Log.d(LogTag, message) }
    }

    private companion object {
        const val LogTag = "HttpMovementApiGateway"
        const val LogBodyPreviewLength = 500
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
    }
}
