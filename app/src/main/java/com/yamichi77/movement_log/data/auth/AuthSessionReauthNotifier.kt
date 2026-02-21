package com.yamichi77.movement_log.data.auth

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yamichi77.movement_log.MainActivity

interface AuthSessionReauthNotifier {
    fun notifyReauthRequired(reason: AuthErrorCode): Boolean
}

class AndroidAuthSessionReauthNotifier(
    private val appContext: Context,
) : AuthSessionReauthNotifier {
    override fun notifyReauthRequired(reason: AuthErrorCode): Boolean {
        if (!canPostNotification()) return false

        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return false
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)

        val intent = Intent(appContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Session expired")
            .setContentText(buildMessage(reason))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }

    private fun canPostNotification(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    private fun buildMessage(reason: AuthErrorCode): String = when (reason) {
        AuthErrorCode.SESSION_STEP_UP_REQUIRED -> {
            "Additional authentication is required. Open the app and sign in again."
        }

        AuthErrorCode.SESSION_COMPROMISED_REAUTH_REQUIRED -> {
            "Reauthentication is required for security reasons."
        }

        else -> {
            "Sign in again from Connection Settings in the app."
        }
    }

    private companion object {
        const val CHANNEL_ID = "auth_session_alert_channel"
        const val CHANNEL_NAME = "Auth session alerts"
        const val NOTIFICATION_ID = 301
    }
}
