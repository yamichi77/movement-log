package com.yamichi77.movement_log.data.sync

import com.yamichi77.movement_log.data.auth.AuthSessionRepository
import com.yamichi77.movement_log.data.auth.RefreshTemporaryFailureException
import com.yamichi77.movement_log.data.auth.UnauthorizedApiException
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
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun sync(limit: Int = DefaultUploadLimit): SyncMovementLogsResult {
        val now = nowEpochMillis()
        val settings = connectionSettingsRepository.settings.first()
        val settingsError = validateSettings(settings)
        if (settingsError != null) {
            connectionSettingsRepository.saveSendStatusText(
                formatStatus(now, "失敗", settingsError),
            )
            return SyncMovementLogsResult.Skipped(settingsError)
        }

        val pendingLogs = movementLogUploadRepository.getPendingLogs(limit)
        if (pendingLogs.isEmpty()) {
            connectionSettingsRepository.saveSendStatusText(
                formatStatus(now, "成功", "未送信ログはありません"),
            )
            return SyncMovementLogsResult.Success(0)
        }

        return try {
            var token = authSessionRepository.getOrRefreshAccessToken(settings.baseUrl)
            val uploadedIds = mutableListOf<Long>()
            pendingLogs.forEach { log ->
                token = uploadWithRefresh(
                    settings = settings,
                    token = token,
                    request = log.toUploadRequest(),
                )
                uploadedIds += log.id
            }
            movementLogUploadRepository.markUploaded(uploadedIds)
            connectionSettingsRepository.saveSendStatusText(
                formatStatus(now, "成功", "${uploadedIds.size}件を送信しました"),
            )
            SyncMovementLogsResult.Success(uploadedIds.size)
        } catch (error: Throwable) {
            val detail = error.message ?: error::class.simpleName.orEmpty()
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

    private suspend fun uploadWithRefresh(
        settings: ConnectionSettings,
        token: String,
        request: MovementLogUploadRequest,
    ): String = try {
        movementApiGateway.uploadMovementLog(
            baseUrl = settings.baseUrl,
            uploadPath = settings.uploadPath,
            token = token,
            request = request,
        )
        token
    } catch (_: UnauthorizedApiException) {
        val refreshed = authSessionRepository.refreshAccessToken(settings.baseUrl).accessToken
        movementApiGateway.uploadMovementLog(
            baseUrl = settings.baseUrl,
            uploadPath = settings.uploadPath,
            token = refreshed,
            request = request,
        )
        refreshed
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
            accuracy = accuracy.toDouble(),
            activity = activityStatus,
        )

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

    private companion object {
        private const val DefaultUploadLimit = 200
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
