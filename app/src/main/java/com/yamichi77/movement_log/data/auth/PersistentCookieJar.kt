package com.yamichi77.movement_log.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.IOException

interface AuthCookieStore {
    fun clear()
}

private val Context.authCookieDataStore by preferencesDataStore(name = "auth_cookie_store")

class PersistentCookieJar(
    private val appContext: Context,
) : CookieJar, AuthCookieStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val cookiesByKey: MutableMap<String, Cookie> = mutableMapOf()

    init {
        loadPersistedCookies()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            cookies.forEach { cookie ->
                val key = cookieKey(cookie)
                if (cookie.expiresAt < System.currentTimeMillis()) {
                    cookiesByKey.remove(key)
                } else {
                    cookiesByKey[key] = cookie
                }
            }
        }
        persistAsync()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        var changed = false
        val result = mutableListOf<Cookie>()
        synchronized(lock) {
            val iterator = cookiesByKey.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val cookie = entry.value
                if (cookie.expiresAt < now) {
                    iterator.remove()
                    changed = true
                    continue
                }
                if (cookie.matches(url)) {
                    result += cookie
                }
            }
        }
        if (changed) {
            persistAsync()
        }
        return result
    }

    private fun loadPersistedCookies() {
        val stored = runBlocking {
            appContext.authCookieDataStore.data
                .catch { error ->
                    if (error is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }
                .first()[CookieJsonKey]
                .orEmpty()
        }
        if (stored.isBlank()) return
        val decoded = runCatching {
            json.decodeFromString<List<StoredCookie>>(stored)
        }.getOrElse { emptyList() }
        val now = System.currentTimeMillis()
        synchronized(lock) {
            cookiesByKey.clear()
            decoded.mapNotNull { it.toCookieOrNull() }
                .filter { it.expiresAt >= now }
                .forEach { cookie ->
                    cookiesByKey[cookieKey(cookie)] = cookie
                }
        }
    }

    private fun persistAsync() {
        val snapshot = synchronized(lock) {
            cookiesByKey.values.map { StoredCookie.fromCookie(it) }
        }
        scope.launch {
            appContext.authCookieDataStore.edit { preferences ->
                preferences[CookieJsonKey] = json.encodeToString(snapshot)
            }
        }
    }

    override fun clear() {
        synchronized(lock) {
            cookiesByKey.clear()
        }
        persistAsync()
    }

    private fun cookieKey(cookie: Cookie): String = buildString {
        append(cookie.name)
        append("|")
        append(cookie.domain)
        append("|")
        append(cookie.path)
    }

    @Serializable
    private data class StoredCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean,
        val persistent: Boolean,
    ) {
        fun toCookieOrNull(): Cookie? = runCatching {
            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .path(path)
            if (hostOnly) {
                builder.hostOnlyDomain(domain)
            } else {
                builder.domain(domain)
            }
            if (persistent) {
                builder.expiresAt(expiresAt)
            } else {
                builder.expiresAt(Long.MAX_VALUE)
            }
            if (secure) builder.secure()
            if (httpOnly) builder.httpOnly()
            builder.build()
        }.getOrNull()

        companion object {
            fun fromCookie(cookie: Cookie): StoredCookie = StoredCookie(
                name = cookie.name,
                value = cookie.value,
                domain = cookie.domain,
                path = cookie.path,
                expiresAt = cookie.expiresAt,
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                hostOnly = cookie.hostOnly,
                persistent = cookie.persistent,
            )
        }
    }

    private companion object {
        val CookieJsonKey = stringPreferencesKey("cookies_json")
    }
}
