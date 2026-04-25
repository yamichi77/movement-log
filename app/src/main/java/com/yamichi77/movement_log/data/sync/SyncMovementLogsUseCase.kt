package com.yamichi77.movement_log.data.sync

import android.util.Log
import com.yamichi77.movement_log.data.auth.AuthErrorCode
import com.yamichi77.movement_log.data.auth.AuthNavigationEventBus
import com.yamichi77.movement_log.data.auth.AuthSessionRepository
import com.yamichi77.movement_log.data.auth.AuthSessionStatusRepository
import com.yamichi77.movement_log.data.auth.RefreshTemporaryFailureException
import com.yamichi77.movement_log.data.auth.UnauthorizedApiException
import com.yamichi77.movement_log.data.network.DuplicateMovementLogException
import com.yamichi77.movement_log.data.network.MovementApiException
import com.yamichi77.movement_log.data.network.MovementApiGateway
import com.yamichi77.movement_log.data.network.MovementLogUploadRequest
import com.yamichi77.movement_log.data.repository.ConnectionSettingsRepository
import com.yamichi77.movement_log.data.repository.MovementLogUploadRepository
import com.yamichi77.movement_log.data.repository.PendingUploadLog
import com.yamichi77.movement_log.data.settings.ConnectionSettings
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class SyncMovementLogsUseCase(
    private val connectionSettingsRepository: ConnectionSettingsRepository,
    private val movementLogUploadRepository: MovementLogUploadRepository,
    private val movementApiGateway: MovementApiGateway,
    private val authSessionRepository: AuthSessionRepository,
    private val sessionStatusRepository: AuthSessionStatusRepository,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun sync(limit: Int = DefaultUploadLimit): SyncMovementLogsResult {
        logDebug("sync: start limit=$limit")
        val now = nowEpochMillis()
        cleanupUploadedLogsIfDateChanged(now)
        val settings = connectionSettingsRepository.settings.first()
        val settingsError = validateSettings(settings)
        if (settingsError != null) {
            logWarn("sync: skipped invalid settings reason=$settingsError")
            connectionSettingsRepository.saveSendStatusText(
                formatStatus(now, "失敗", settingsError),
            )
            return SyncMovementLogsResult.Skipped(settingsError)
        }

        val pendingLogs = movementLogUploadRepository.getPendingLogs(limit)
        if (pendingLogs.isEmpty()) {
            logDebug("sync: no pending logs")
            connectionSettingsRepository.saveSendStatusText(
                formatStatus(now, "成功", "未送信ログはありません"),
            )
            return SyncMovementLogsResult.Success(0)
        }
        logDebug("sync: pending count=${pendingLogs.size}")

        val uploadedIds = mutableListOf<Long>()
        return try {
            var token = authSessionRepository.getOrRefreshAccessToken(settings.baseUrl)
            pendingLogs.forEach { log ->
                try {
                    token = uploadWithRefresh(
                        settings = settings,
                        token = token,
                        request = log.toUploadRequest(),
                    )
                } catch (_: DuplicateMovementLogException) {
                    // 既存データとの重複は送信済みとして扱う。
                    logDebug("sync: duplicate upload treated as uploaded logId=${log.id}")
                }
                uploadedIds += log.id
            }
            movementLogUploadRepository.markUploaded(uploadedIds)
            logDebug("sync: completed uploadedCount=${uploadedIds.size}")
            connectionSettingsRepository.saveSendStatusText(
                formatStatus(now, "成功", "${uploadedIds.size}件を送信しました"),
            )
            SyncMovementLogsResult.Success(uploadedIds.size)
        } catch (error: Throwable) {
            movementLogUploadRepository.markUploaded(uploadedIds)
            val detail = error.message ?: error::class.simpleName.orEmpty()
            logWarn(
                "sync: failed uploadedBeforeFailure=${uploadedIds.size} error=${error::class.simpleName} detail=$detail",
            )
            connectionSettingsRepository.saveSendStatusText(
                formatStatus(now, "失敗", detail),
            )
            val retryable = error is MovementApiException ||
                error is UnauthorizedApiException ||
                error is RefreshTemporaryFailureException
            if (retryable) {
                SyncMovementLogsResult.Retry(detail)
            } else {
                SyncMovementLogsResult.Failure(detail)
            }
        }
    }

    private suspend fun cleanupUploadedLogsIfDateChanged(nowEpochMillis: Long) {
        val latestRecordedAtEpochMillis = movementLogUploadRepository.getLatestUploadedRecordedAtEpochMillis() ?: return
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val latestLogDate = Instant.ofEpochMilli(latestRecordedAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        if (latestLogDate == today) return

        movementLogUploadRepository.deleteUploaded()
        logDebug("cleanupUploadedLogsIfDateChanged: deleted uploaded logs latestLogDate=$latestLogDate today=$today")
    }

    private suspend fun uploadWithRefresh(
        settings: ConnectionSettings,
        token: String,
        request: MovementLogUploadRequest,
    ): String = try {
        logDebug("uploadWithRefresh: upload first attempt")
        movementApiGateway.uploadMovementLog(
            baseUrl = settings.baseUrl,
            uploadPath = settings.uploadPath,
            token = token,
            request = request,
        )
        token
    } catch (_: UnauthorizedApiException) {
        logWarn("uploadWithRefresh: unauthorized, trying refresh+retry")
        val refreshed = authSessionRepository.refreshAccessToken(settings.baseUrl).accessToken
        try {
            movementApiGateway.uploadMovementLog(
                baseUrl = settings.baseUrl,
                uploadPath = settings.uploadPath,
                token = refreshed,
                request = request,
            )
            refreshed
        } catch (error: UnauthorizedApiException) {
            logWarn("uploadWithRefresh: unauthorized after refresh, escalating to reauth")
            sessionStatusRepository.markReauthRequired(
                reason = AuthErrorCode.SESSION_EXPIRED,
            )
            authSessionRepository.clearTokens()
            AuthNavigationEventBus.requireLogin(
                reason = AuthErrorCode.SESSION_EXPIRED,
                baseUrl = settings.baseUrl.trim().takeIf { it.isNotBlank() },
            )
            throw error
        }
    }

    private fun validateSettings(settings: ConnectionSettings): String? {
        if (settings.baseUrl.isBlank()) return "BFF Base URLが未設定です"
        if (settings.uploadPath.isBlank()) return "アップロードパスが未設定です"
        if (!settings.uploadPath.trim().startsWith("/")) {
            return "アップロードパスは / から始めてください"
        }
        return null
    }

    private fun PendingUploadLog.toUploadRequest(): MovementLogUploadRequest =
        MovementLogUploadRequest(
            seqTime = UploadTimestampFormatter.format(
                Instant.ofEpochMilli(recordedAtEpochMillis).atZone(ZoneId.systemDefault()),
            ),
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy.toNetworkAccuracy(),
            activity = activityStatus,
        )

    private fun Float.toNetworkAccuracy(): Double =
        toString().toDoubleOrNull() ?: toDouble()

    private fun formatStatus(
        nowEpochMillis: Long,
        result: String,
        detail: String,
    ): String {
        val timestamp = DisplayTimestampFormatter.format(
            Instant.ofEpochMilli(nowEpochMillis).atZone(ZoneId.systemDefault()),
        )
        return "最終送信: $timestamp - $result ($detail)"
    }

    private fun logDebug(message: String) {
        runCatching { Log.d(LogTag, message) }
    }

    private fun logWarn(message: String) {
        runCatching { Log.w(LogTag, message) }
    }

    internal companion object {
        const val LogTag = "SyncMovementLogsUseCase"
        internal const val DefaultUploadLimit = 200
        private val UploadTimestampFormatter = DateTimeFormatter.ofPattern(
            "yyyyMMddHHmmss",
            Locale.JAPAN,
        )
        private val DisplayTimestampFormatter = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd HH:mm:ss",
            Locale.JAPAN,
        )
    }
}

sealed interface SyncMovementLogsResult {
    data class Success(val uploadedCount: Int) : SyncMovementLogsResult

    data class Skipped(val reason: String) : SyncMovementLogsResult

    data class Retry(val reason: String) : SyncMovementLogsResult

    data class Failure(val reason: String) : SyncMovementLogsResult
}


