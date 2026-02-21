package com.yamichi77.movement_log.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.yamichi77.movement_log.R

enum class TopLevelDestination(
    @param:StringRes val labelResId: Int,
    val icon: ImageVector,
    val route: AppRoute,
) {
    HOME(R.string.menu_home, Icons.Default.Home, AppRoute.Home),
    HISTORY_MAP(R.string.menu_history_map, Icons.Default.Map, AppRoute.HistoryMap),
    LOG_TABLE(R.string.menu_log_table, Icons.AutoMirrored.Filled.List, AppRoute.LogTable),
    CONNECTION_SETTINGS(R.string.menu_connection_settings, Icons.Default.Settings, AppRoute.ConnectionSettings),
}
