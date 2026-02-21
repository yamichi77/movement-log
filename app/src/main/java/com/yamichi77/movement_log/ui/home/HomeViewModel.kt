package com.yamichi77.movement_log.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yamichi77.movement_log.BuildConfig
import com.yamichi77.movement_log.R
import com.yamichi77.movement_log.data.repository.MovementLogRepository
import com.yamichi77.movement_log.data.repository.MovementLogRepositoryProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeViewModel(
    application: Application,
    private val repository: MovementLogRepository =
        MovementLogRepositoryProvider.get(application),
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        repository = MovementLogRepositoryProvider.get(application),
    )

    private val activityStatusStillText: String = runCatching {
        application.getString(R.string.activity_status_still)
    }.getOrDefault("")
    private val notRecordedText: String = runCatching {
        application.getString(R.string.home_not_recorded)
    }.getOrDefault("--")
    private val unknownValueText: String = runCatching {
        application.getString(R.string.home_unknown_value)
    }.getOrDefault("--")
    private val isMapEnabled: Boolean = BuildConfig.MAPS_API_KEY.isNotBlank()
    private val dateTimeFormatter = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss",
        Locale.JAPAN,
    )

    init {
        viewModelScope.launch {
            repository.startCollecting()
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.homeTrackingSnapshot,
        repository.historyMapSnapshot,
    ) { homeSnapshot, historySnapshot ->
        val previewPoints = historySnapshot.points.takeLast(HOME_MAP_PREVIEW_POINT_LIMIT)
        val lastPreviewPoint = previewPoints.lastOrNull()
        HomeUiState(
            isCollecting = homeSnapshot.isCollecting,
            activityStatus = homeSnapshot.activityStatus,
            latitudeText = formatCoordinate(homeSnapshot.latitude),
            longitudeText = formatCoordinate(homeSnapshot.longitude),
            lastUpdatedText = homeSnapshot.updatedAtEpochMillis?.let(::formatDateTime)
                ?: notRecordedText,
            logCount = homeSnapshot.logCount,
            mapPreviewPoints = previewPoints.map { point ->
                HomeMapPreviewPoint(
                    latitude = point.latitude,
                    longitude = point.longitude,
                )
            },
            lastPreviewLatitude = lastPreviewPoint?.latitude,
            lastPreviewLongitude = lastPreviewPoint?.longitude,
            isMapEnabled = isMapEnabled,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(
                activityStatus = activityStatusStillText,
                lastUpdatedText = notRecordedText,
                isMapEnabled = isMapEnabled,
            ),
        )

    fun onStartCollectingClick() {
        viewModelScope.launch {
            repository.startCollecting()
        }
    }

    fun onStopCollectingClick() {
        viewModelScope.launch {
            repository.stopCollecting()
        }
    }

    private fun formatCoordinate(value: Double?): String {
        if (value == null) return unknownValueText
        return String.format(Locale.US, "%.4f", value)
    }

    private fun formatDateTime(epochMillis: Long): String = dateTimeFormatter.format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
    )

    private companion object {
        const val HOME_MAP_PREVIEW_POINT_LIMIT = 20
    }
}
