package com.yamichi77.movement_log.data.sync

import com.yamichi77.movement_log.data.auth.AuthApiException
import com.yamichi77.movement_log.data.auth.AuthErrorCode
import com.yamichi77.movement_log.data.auth.AuthSessionReauthNotifier
import com.yamichi77.movement_log.data.auth.AuthSessionRepository
import com.yamichi77.movement_log.data.auth.AuthSessionStatus
import com.yamichi77.movement_log.data.auth.AuthSessionStatusRepository
import com.yamichi77.movement_log.data.auth.ReauthRequiredException
import com.yamichi77.movement_log.data.auth.RefreshTemporaryFailureException
import com.yamichi77.movement_log.data.auth.SessionInvalidException
import com.yamichi77.movement_log.data.repository.ConnectionSettingsRepository
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.concurrent.TimeUnit

class AuthKeepAliveUseCase(
    private val connectionSettingsRepository: ConnectionSettingsRepository,
    private val authSessionRepository: AuthSessionRepository,
    private val sessionStatusRepository: AuthSessionStatusRepository,
    private val reauthNotifier: AuthSessionReauthNotifier,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun run(): AuthKeepAliveResult {
        val settings = connectionSettingsRepository.settings.first()
        if (settings.baseUrl.isBlank()) {
            return AuthKeepAliveResult.Success
        }

        val status = sessionStatusRepository.status.first()
        if (!status.isSessionManaged) {
            return AuthKeepAliveResult.Success
        }

        if (status.reauthRequired) {
            notifyIfAllowed(
                status = status,
                fallbackReason = AuthErrorCode.SESSION_EXPIRED,
            )
            return AuthKeepAliveResult.Success
        }

        return try {
            authSessionRepository.refreshAccessToken(settings.baseUrl)
            AuthKeepAliveResult.Success
        } catch (error: ReauthRequiredException) {
            notifyIfAllowed(
                status = sessionStatusRepository.status.first(),
                fallbackReason = error.errorCode,
            )
            AuthKeepAliveResult.Success
        } catch (_: SessionInvalidException) {
            notifyIfAllowed(
                status = sessionStatusRepository.status.first(),
                fallbackReason = AuthErrorCode.SESSION_INVALID,
            )
            AuthKeepAliveResult.Success
        } catch (error: RefreshTemporaryFailureException) {
            AuthKeepAliveResult.Retry(error.message.orEmpty())
        } catch (error: AuthApiException) {
            AuthKeepAliveResult.Retry(error.message.orEmpty())
        } catch (error: IOException) {
            AuthKeepAliveResult.Retry(error.message.orEmpty())
        }
    }

    private suspend fun notifyIfAllowed(status: AuthSessionStatus, fallbackReason: AuthErrorCode) {
        val now = nowEpochMillis()
        val lastNotifiedAt = status.lastReauthNotifiedAtEpochMillis
        if (lastNotifiedAt != null && now - lastNotifiedAt < ReauthNotificationIntervalMillis) {
            return
        }
        val reason = status.reauthReason ?: fallbackReason
        val notified = reauthNotifier.notifyReauthRequired(reason)
        if (notified) {
            sessionStatusRepository.markReauthNotificationSent(now)
        }
    }

    private companion object {
        val ReauthNotificationIntervalMillis: Long = TimeUnit.HOURS.toMillis(12)
    }
}

sealed interface AuthKeepAliveResult {
    data object Success : AuthKeepAliveResult

    data class Retry(val reason: String) : AuthKeepAliveResult
}
