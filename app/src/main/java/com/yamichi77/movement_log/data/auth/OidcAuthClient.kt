package com.yamichi77.movement_log.data.auth

import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom

interface OidcAuthClient {
    suspend fun createLoginUri(): Uri

    suspend fun completeAuthorization(callback: OidcAuthorizationCallback): RefreshAccessTokenResult

    suspend fun refreshAccessToken(currentAccessToken: String?): RefreshAccessTokenResult

    suspend fun logout()
}

data class OidcAuthorizationCallback(
    val state: String? = null,
    val code: String? = null,
    val error: String? = null,
    val errorDescription: String? = null,
)

class HttpOidcAuthClient(
    private val client: OkHttpClient,
    private val sessionStore: AuthSessionStore,
    private val config: OidcAuthConfig,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : OidcAuthClient {
    private val metadataMutex = Mutex()

    @Volatile
    private var cachedMetadata: OidcProviderMetadata? = null

    override suspend fun createLoginUri(): Uri {
        val metadata = providerMetadata()
        val request = createPendingAuthorizationRequest()
        val uri = metadata.authorizationEndpoint.newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", requireClientId())
            .addQueryParameter("redirect_uri", requireRedirectUri())
            .addQueryParameter("scope", config.scopes.joinToString(" "))
            .addQueryParameter("state", request.state)
            .addQueryParameter("code_challenge", request.codeChallenge)
            .addQueryParameter("code_challenge_method", "S256")
            .build()
            .let { Uri.parse(it.toString()) }
        sessionStore.savePendingAuthorization(
            PendingAuthRequest(
                state = request.state,
                codeVerifier = request.codeVerifier,
            ),
        )
        logDebug("createLoginUri: generated authorization request")
        return uri
    }

    override suspend fun completeAuthorization(callback: OidcAuthorizationCallback): RefreshAccessTokenResult {
        val pending = sessionStore.pendingAuthorization()
            ?: throw AuthApiException("pending auth request not found")
        try {
            val error = callback.error?.trim()?.takeIf { it.isNotBlank() }
            val errorDescription = callback.errorDescription?.trim()?.takeIf { it.isNotBlank() }
            if (error != null) {
                val detail = errorDescription ?: error
                throw AuthApiException("auth callback failed: $detail")
            }

            val state = callback.state?.trim()?.takeIf { it.isNotBlank() }
                ?: throw AuthApiException("auth callback missing state")
            if (state != pending.state) {
                throw AuthApiException("auth callback state mismatch")
            }
            val code = callback.code?.trim()?.takeIf { it.isNotBlank() }
                ?: throw AuthApiException("auth callback missing code")
            val response = tokenRequest(
                formBuilder = FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("client_id", requireClientId())
                    .add("code", code)
                    .add("redirect_uri", requireRedirectUri())
                    .add("code_verifier", pending.codeVerifier),
                mode = TokenRequestMode.AuthorizationCode,
            )
            sessionStore.replaceSession(
                StoredAuthSession(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    idToken = response.idToken,
                    tokenType = response.tokenType,
                    accessTokenExpiresAtEpochMillis = response.accessTokenExpiresAtEpochMillis(),
                ),
            )
            logDebug("completeAuthorization: success hasRefresh=${response.refreshToken != null}")
            return RefreshAccessTokenResult(
                accessToken = response.accessToken,
                sessionRotated = false,
            )
        } finally {
            sessionStore.clearPendingAuthorization()
        }
    }

    override suspend fun refreshAccessToken(currentAccessToken: String?): RefreshAccessTokenResult {
        val currentSession = sessionStore.currentSession()
            ?: throw ReauthRequiredException(
                AuthErrorCode.SESSION_EXPIRED,
                "refresh token is not available",
            )
        val refreshToken = currentSession.refreshToken?.takeIf { it.isNotBlank() }
            ?: throw ReauthRequiredException(
                AuthErrorCode.SESSION_EXPIRED,
                "refresh token is not available",
            )
        val response = tokenRequest(
            formBuilder = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", requireClientId())
                .add("refresh_token", refreshToken),
            mode = TokenRequestMode.Refresh,
        )
        val updatedSession = currentSession.copy(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken ?: refreshToken,
            idToken = response.idToken ?: currentSession.idToken,
            tokenType = response.tokenType ?: currentSession.tokenType,
            accessTokenExpiresAtEpochMillis = response.accessTokenExpiresAtEpochMillis(),
        )
        sessionStore.replaceSession(updatedSession)
        val rotated = response.refreshToken != null && response.refreshToken != refreshToken
        logDebug("refreshAccessToken: success rotated=$rotated")
        return RefreshAccessTokenResult(
            accessToken = updatedSession.accessToken.orEmpty(),
            sessionRotated = rotated,
        )
    }

    override suspend fun logout() {
        sessionStore.clearPendingAuthorization()
    }

    private suspend fun providerMetadata(): OidcProviderMetadata {
        cachedMetadata?.let { return it }
        return metadataMutex.withLock {
            cachedMetadata ?: fetchProviderMetadata().also { cachedMetadata = it }
        }
    }

    private suspend fun fetchProviderMetadata(): OidcProviderMetadata = withContext(Dispatchers.IO) {
        val issuerUrl = parseHttpUrl(requireIssuer())
        val discoveryUrl = issuerUrl.newBuilder()
            .addPathSegment(".well-known")
            .addPathSegment("openid-configuration")
            .build()
        logDebug("fetchProviderMetadata: start")
        val request = Request.Builder()
            .url(discoveryUrl)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AuthApiException("OIDC discovery failed: code=${response.code}")
            }
            val metadata = runCatching {
                json.decodeFromString(OidcDiscoveryDocument.serializer(), body)
            }.getOrElse { error ->
                throw AuthApiException("invalid OIDC discovery response", error)
            }
            OidcProviderMetadata(
                authorizationEndpoint = parseHttpUrl(metadata.authorizationEndpoint),
                tokenEndpoint = parseHttpUrl(metadata.tokenEndpoint),
                endSessionEndpoint = metadata.endSessionEndpoint?.let(::parseHttpUrl),
            )
        }
    }

    private suspend fun tokenRequest(
        formBuilder: FormBody.Builder,
        mode: TokenRequestMode,
    ): TokenSuccessResponse = withContext(Dispatchers.IO) {
        val metadata = providerMetadata()
        val request = Request.Builder()
            .url(metadata.tokenEndpoint)
            .post(formBuilder.build())
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when {
                response.isSuccessful -> runCatching {
                    json.decodeFromString(TokenSuccessResponse.serializer(), body)
                }.getOrElse { error ->
                    throw AuthApiException("invalid token response", error)
                }.also {
                    if (it.accessToken.isBlank()) {
                        throw AuthApiException("token response missing access_token")
                    }
                }

                response.code == 400 || response.code == 401 -> {
                    val errorResponse = runCatching {
                        json.decodeFromString(TokenErrorResponse.serializer(), body)
                    }.getOrElse { TokenErrorResponse() }
                    throw mapTokenError(errorResponse, mode)
                }

                response.code == 503 -> {
                    throw RefreshTemporaryFailureException("token endpoint temporarily unavailable")
                }

                else -> {
                    throw AuthApiException("token request failed: code=${response.code}")
                }
            }
        }
    }

    private fun mapTokenError(
        response: TokenErrorResponse,
        mode: TokenRequestMode,
    ): Throwable {
        val error = response.error?.trim().orEmpty()
        val description = response.errorDescription?.trim().orEmpty()
        val message = listOf(error, description)
            .filter { it.isNotBlank() }
            .joinToString(": ")
            .ifBlank { "unknown token error" }
        return when {
            error == "temporarily_unavailable" -> {
                RefreshTemporaryFailureException(message)
            }

            mode == TokenRequestMode.Refresh && error == "invalid_grant" -> {
                ReauthRequiredException(AuthErrorCode.SESSION_EXPIRED, message)
            }

            mode == TokenRequestMode.Refresh && error.isNotBlank() -> {
                ReauthRequiredException(AuthErrorCode.SESSION_INVALID, message)
            }

            else -> {
                AuthApiException(message)
            }
        }
    }

    private fun createPendingAuthorizationRequest(): PendingAuthorizationRequestInternal {
        val state = randomUrlSafeString(StateByteLength)
        val codeVerifier = randomUrlSafeString(CodeVerifierByteLength)
        val codeChallenge = base64UrlSha256(codeVerifier)
        return PendingAuthorizationRequestInternal(
            state = state,
            codeVerifier = codeVerifier,
            codeChallenge = codeChallenge,
        )
    }

    private fun randomUrlSafeString(byteLength: Int): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
    }

    private fun base64UrlSha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
    }

    private fun requireIssuer(): String = config.issuer.takeIf { it.isNotBlank() }
        ?: throw AuthApiException("OIDC issuer is not configured")

    private fun requireClientId(): String = config.clientId.takeIf { it.isNotBlank() }
        ?: throw AuthApiException("OIDC client_id is not configured")

    private fun requireRedirectUri(): String = config.redirectUri.takeIf { it.isNotBlank() }
        ?: throw AuthApiException("OIDC redirect_uri is not configured")

    private fun parseHttpUrl(value: String): HttpUrl {
        val normalized = value.trim()
            .ifBlank { throw AuthApiException("HTTP URL is blank") }
            .let { raw -> if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw" }
        return normalized.toHttpUrlOrNull()
            ?: throw AuthApiException("invalid HTTP URL: $value")
    }

    private fun logDebug(message: String) {
        runCatching { Log.d(LogTag, message) }
    }

    private data class PendingAuthorizationRequestInternal(
        val state: String,
        val codeVerifier: String,
        val codeChallenge: String,
    )

    @Serializable
    private data class OidcDiscoveryDocument(
        @SerialName("authorization_endpoint")
        val authorizationEndpoint: String,
        @SerialName("token_endpoint")
        val tokenEndpoint: String,
        @SerialName("end_session_endpoint")
        val endSessionEndpoint: String? = null,
    )

    private data class OidcProviderMetadata(
        val authorizationEndpoint: HttpUrl,
        val tokenEndpoint: HttpUrl,
        val endSessionEndpoint: HttpUrl? = null,
    )

    @Serializable
    private data class TokenSuccessResponse(
        @SerialName("access_token")
        val accessToken: String,
        @SerialName("refresh_token")
        val refreshToken: String? = null,
        @SerialName("id_token")
        val idToken: String? = null,
        @SerialName("token_type")
        val tokenType: String? = null,
        @SerialName("expires_in")
        val expiresInSeconds: Long? = null,
    ) {
        fun accessTokenExpiresAtEpochMillis(nowEpochMillis: Long = System.currentTimeMillis()): Long? =
            expiresInSeconds?.let { nowEpochMillis + (it * 1000L) }
    }

    @Serializable
    private data class TokenErrorResponse(
        val error: String? = null,
        @SerialName("error_description")
        val errorDescription: String? = null,
    )

    private enum class TokenRequestMode {
        AuthorizationCode,
        Refresh,
    }

    private companion object {
        const val LogTag = "HttpOidcAuthClient"
        const val StateByteLength = 24
        const val CodeVerifierByteLength = 48
        val secureRandom = SecureRandom()
    }
}
