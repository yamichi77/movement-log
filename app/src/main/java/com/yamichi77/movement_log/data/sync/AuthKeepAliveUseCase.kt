package com.yamichi77.movement_log.data.sync

import android.util.Log
import com.yamichi77.movement_log.data.auth.AuthApiException
import com.yamichi77.movement_log.data.auth.AuthErrorCode
import com.yamichi77.movement_log.data.auth.AuthSessionReauthNotifier
import com.yamichi77.movement_log.data.auth.AuthSessionRepository
import com.yamichi77.movement_log.data.auth.AuthSessionStatus
import com.yamichi77.movement_log.data.auth.AuthSessionStatusRepository
import com.yamichi77.movement_log.data.auth.ReauthRequiredException
import com.yamichi77.movement_log.data.auth.RefreshTemporaryFailureException
import com.yamichi77.movement_log.data.auth.SessionInvalidException
import com.yamichi77.movement_log.data.auth.UnauthorizedApiException
import com.yamichi77.movement_log.data.network.MovementApiException
import com.yamichi77.movement_log.data.network.MovementApiGateway
import com.yamichi77.movement_log.data.repository.ConnectionSettingsRepository
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.concurrent.TimeUnit

class AuthKeepAliveUseCase(
    private val connectionSettingsRepository: ConnectionSettingsRepository,
    private val movementApiGateway: MovementApiGateway,
    private val authSessionRepository: AuthSessionRepository,
    private val sessionStatusRepository: AuthSessionStatusRepository,
    private val reauthNotifier: AuthSessionReauthNotifier,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun run(): AuthKeepAliveResult {
        logDebug("run: start")
        val settings = connectionSettingsRepository.settings.first()
        if (settings.baseUrl.isBlank()) {
            logDebug("run: skip because baseUrl is blank")
            return AuthKeepAliveResult.Success
        }

        val status = sessionStatusRepository.status.first()
        if (!status.isSessionManaged) {
            logDebug("run: skip because session is not managed")
            return AuthKeepAliveResult.Success
        }

        if (status.reauthRequired) {
            logDebug("run: reauthRequired already true, only notifying if allowed")
            notifyIfAllowed(
                status = status,
                fallbackReason = AuthErrorCode.SESSION_EXPIRED,
            )
            return AuthKeepAliveResult.Success
        }

        val currentToken = authSessionRepository.accessToken.value?.takeIf { it.isNotBlank() }
        if (currentToken != null) {
            try {
                movementApiGateway.verifyToken(
                    baseUrl = settings.baseUrl,
                    token = currentToken,
                )
                logDebug("run: current access token is still valid, skip refresh")
                return AuthKeepAliveResult.Success
            } catch (_: UnauthorizedApiException) {
                logWarn("run: current access token is unauthorized, fallback to refresh")
            } catch (error: MovementApiException) {
                logWarn("run: token verification failed reason=${error.message.orEmpty()}")
                return AuthKeepAliveResult.Retry(error.message.orEmpty())
            } catch (error: IOException) {
                logWarn("run: token verification io failure reason=${error.message.orEmpty()}")
                return AuthKeepAliveResult.Retry(error.message.orEmpty())
            }
        }

        return try {
            authSessionRepository.refreshAccessToken(settings.baseUrl)
            logDebug("run: refresh success")
            AuthKeepAliveResult.Success
        } catch (error: ReauthRequiredException) {
            logWarn("run: refresh requires reauth reason=${error.errorCode}")
            notifyIfAllowed(
                status = sessionStatusRepository.status.first(),
                fallbackReason = error.errorCode,
            )
            AuthKeepAliveResult.Success
        } catch (_: SessionInvalidException) {
            logWarn("run: refresh got session invalid")
            notifyIfAllowed(
                status = sessionStatusRepository.status.first(),
                fallbackReason = AuthErrorCode.SESSION_INVALID,
            )
            AuthKeepAliveResult.Success
        } catch (error: RefreshTemporaryFailureException) {
            logWarn("run: temporary failure reason=${error.message.orEmpty()}")
            AuthKeepAliveResult.Retry(error.message.orEmpty())
        } catch (error: AuthApiException) {
            logWarn("run: auth api failure reason=${error.message.orEmpty()}")
            AuthKeepAliveResult.Retry(error.message.orEmpty())
        } catch (error: IOException) {
            logWarn("run: io failure reason=${error.message.orEmpty()}")
            AuthKeepAliveResult.Retry(error.message.orEmpty())
        }
    }

    private suspend fun notifyIfAllowed(status: AuthSessionStatus, fallbackReason: AuthErrorCode) {
        val now = nowEpochMillis()
        val lastNotifiedAt = status.lastReauthNotifiedAtEpochMillis
        if (lastNotifiedAt != null && now - lastNotifiedAt < ReauthNotificationIntervalMillis) {
            logDebug("notifyIfAllowed: suppressed by rate limit")
            return
        }
        val reason = status.reauthReason ?: fallbackReason
        val notified = reauthNotifier.notifyReauthRequired(reason)
        logDebug("notifyIfAllowed: notified=$notified reason=$reason")
        if (notified) {
            sessionStatusRepository.markReauthNotificationSent(now)
        }
    }

    private fun logDebug(message: String) {
        runCatching { Log.d(LogTag, message) }
    }

    private fun logWarn(message: String) {
        runCatching { Log.w(LogTag, message) }
    }

    private companion object {
        const val LogTag = "AuthKeepAliveUseCase"
        val ReauthNotificationIntervalMillis: Long = TimeUnit.HOURS.toMillis(12)
    }
}

sealed interface AuthKeepAliveResult {
    data object Success : AuthKeepAliveResult

    data class Retry(val reason: String) : AuthKeepAliveResult
}
