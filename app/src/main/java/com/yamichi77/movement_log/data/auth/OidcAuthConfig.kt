package com.yamichi77.movement_log.data.auth

import com.yamichi77.movement_log.BuildConfig

data class OidcAuthConfig(
    val issuer: String,
    val clientId: String,
    val redirectUri: String,
    val scopes: Set<String>,
) {
    init {
        require(scopes.isNotEmpty()) { "OIDC scopes must not be empty" }
    }
}

fun buildOidcAuthConfig(): OidcAuthConfig = OidcAuthConfig(
    issuer = BuildConfig.AUTH_ISSUER.trim(),
    clientId = BuildConfig.AUTH_CLIENT_ID.trim(),
    redirectUri = BuildConfig.AUTH_REDIRECT_URI.trim(),
    scopes = BuildConfig.AUTH_SCOPES.split(" ")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet(),
)
