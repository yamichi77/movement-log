package com.yamichi77.movement_log.ui.history

data class HistoryMapUiPoint(
    val latitude: Double,
    val longitude: Double,
)

data class HistoryMapUiState(
    val points: List<HistoryMapUiPoint> = emptyList(),
    val logCount: Int = 0,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastPointText: String = "--",
    val lastUpdatedText: String = "",
    val isMapEnabled: Boolean = false,
)
