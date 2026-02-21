package com.yamichi77.movement_log.data.auth

import android.content.Context
import okhttp3.CookieJar

object PersistentCookieJarProvider {
    @Volatile
    private var instance: CookieJar? = null

    fun get(context: Context): CookieJar =
        instance ?: synchronized(this) {
            instance ?: PersistentCookieJar(context.applicationContext).also { instance = it }
        }
}
