package com.yamichi77.movement_log.data.auth

import android.content.Context
import okhttp3.OkHttpClient

object AuthSessionStoreProvider {
    @Volatile
    private var instance: AuthSessionStore? = null

    fun get(context: Context): AuthSessionStore =
        instance ?: synchronized(this) {
            instance ?: AuthSessionStore(
                appContext = context.applicationContext,
            ).also { instance = it }
        }
}

object AuthSessionStatusRepositoryProvider {
    @Volatile
    private var instance: AuthSessionStatusRepository? = null

    fun get(context: Context): AuthSessionStatusRepository =
        instance ?: synchronized(this) {
            instance ?: DataStoreAuthSessionStatusRepository(
                appContext = context.applicationContext,
            ).also { instance = it }
        }
}

object AuthHttpClientProvider {
    @Volatile
    private var instance: OkHttpClient? = null

    fun get(context: Context): OkHttpClient =
        instance ?: synchronized(this) {
            instance ?: OkHttpClient.Builder()
                .build()
                .also { instance = it }
        }
}

object OidcAuthClientProvider {
    @Volatile
    private var instance: OidcAuthClient? = null

    fun get(context: Context): OidcAuthClient =
        instance ?: synchronized(this) {
            instance ?: HttpOidcAuthClient(
                client = AuthHttpClientProvider.get(context.applicationContext),
                sessionStore = AuthSessionStoreProvider.get(context.applicationContext),
                config = buildOidcAuthConfig(),
            ).also { instance = it }
        }
}

object AuthSessionReauthNotifierProvider {
    @Volatile
    private var instance: AuthSessionReauthNotifier? = null

    fun get(context: Context): AuthSessionReauthNotifier =
        instance ?: synchronized(this) {
            instance ?: AndroidAuthSessionReauthNotifier(
                appContext = context.applicationContext,
            ).also { instance = it }
        }
}

object AuthSessionRepositoryProvider {
    @Volatile
    private var instance: AuthSessionRepository? = null

    fun get(context: Context): AuthSessionRepository =
        instance ?: synchronized(this) {
            instance ?: DefaultAuthSessionRepository(
                authClient = OidcAuthClientProvider.get(context.applicationContext),
                sessionStore = AuthSessionStoreProvider.get(context.applicationContext),
                sessionStatusRepository = AuthSessionStatusRepositoryProvider.get(
                    context.applicationContext,
                ),
            ).also { instance = it }
        }

    fun setForTesting(repository: AuthSessionRepository?) {
        instance = repository
    }
}
