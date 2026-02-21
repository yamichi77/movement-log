package com.yamichi77.movement_log.ui.logtable

data class LogTableUiItem(
    val stableId: Long,
    val timeText: String,
    val latitudeText: String,
    val longitudeText: String,
    val activityStatusText: String,
)

data class LogTableUiState(
    val items: List<LogTableUiItem> = emptyList(),
    val displayedCount: Int = 0,
    val totalCount: Int = 0,
)
