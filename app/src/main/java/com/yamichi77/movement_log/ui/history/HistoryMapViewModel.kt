package com.yamichi77.movement_log.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yamichi77.movement_log.BuildConfig
import com.yamichi77.movement_log.R
import com.yamichi77.movement_log.data.repository.MovementLogRepository
import com.yamichi77.movement_log.data.repository.MovementLogRepositoryProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistoryMapViewModel(
    application: Application,
    private val repository: MovementLogRepository =
        MovementLogRepositoryProvider.get(application),
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        repository = MovementLogRepositoryProvider.get(application),
    )

    private val notRecordedText: String = runCatching {
        application.getString(R.string.home_not_recorded)
    }.getOrDefault("--")
    private val isMapEnabled: Boolean = BuildConfig.MAPS_API_KEY.isNotBlank()
    private val dateTimeFormatter = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss",
        Locale.JAPAN,
    )

    val uiState: StateFlow<HistoryMapUiState> = repository.historyMapSnapshot
        .map { snapshot ->
            val lastPointText = if (snapshot.lastLatitude == null || snapshot.lastLongitude == null) {
                notRecordedText
            } else {
                String.format(
                    Locale.US,
                    "%.4f, %.4f",
                    snapshot.lastLatitude,
                    snapshot.lastLongitude,
                )
            }
            HistoryMapUiState(
                points = snapshot.points.map { point ->
                    HistoryMapUiPoint(
                        latitude = point.latitude,
                        longitude = point.longitude,
                    )
                },
                logCount = snapshot.logCount,
                lastLatitude = snapshot.lastLatitude,
                lastLongitude = snapshot.lastLongitude,
                lastPointText = lastPointText,
                lastUpdatedText = snapshot.lastUpdatedAtEpochMillis?.let(::formatDateTime)
                    ?: notRecordedText,
                isMapEnabled = isMapEnabled,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryMapUiState(
                lastUpdatedText = notRecordedText,
                isMapEnabled = isMapEnabled,
            ),
        )

    private fun formatDateTime(epochMillis: Long): String = dateTimeFormatter.format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
    )
}
