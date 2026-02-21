package com.yamichi77.movement_log.ui.logtable

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

class LogTableViewModel(
    application: Application,
    private val repository: MovementLogRepository =
        MovementLogRepositoryProvider.get(application),
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        repository = MovementLogRepositoryProvider.get(application),
    )

    private val dateTimeFormatter = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss",
        Locale.JAPAN,
    )

    val uiState: StateFlow<LogTableUiState> = repository.logTableSnapshot
        .map { snapshot ->
            LogTableUiState(
                items = snapshot.items.map { item ->
                    LogTableUiItem(
                        stableId = item.id,
                        timeText = formatDateTime(item.recordedAtEpochMillis),
                        latitudeText = formatCoordinate(item.latitude),
                        longitudeText = formatCoordinate(item.longitude),
                        activityStatusText = item.activityStatus,
                    )
                },
                displayedCount = snapshot.displayedCount,
                totalCount = snapshot.totalCount,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LogTableUiState(),
        )

    private fun formatDateTime(epochMillis: Long): String = dateTimeFormatter.format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
    )

    private fun formatCoordinate(value: Double): String = String.format(
        Locale.US,
        "%.4f",
        value,
    )
}
