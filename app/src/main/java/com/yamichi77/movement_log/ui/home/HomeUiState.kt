package com.yamichi77.movement_log.ui.home

data class HomeMapPreviewPoint(
    val latitude: Double,
    val longitude: Double,
)

data class HomeUiState(
    val isCollecting: Boolean = false,
    val activityStatus: String = "",
    val latitudeText: String = "--",
    val longitudeText: String = "--",
    val lastUpdatedText: String = "",
    val logCount: Int = 0,
    val mapPreviewPoints: List<HomeMapPreviewPoint> = emptyList(),
    val lastPreviewLatitude: Double? = null,
    val lastPreviewLongitude: Double? = null,
    val isMapEnabled: Boolean = false,
)
