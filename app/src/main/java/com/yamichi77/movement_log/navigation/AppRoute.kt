package com.yamichi77.movement_log.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable
    data object Permission : AppRoute

    @Serializable
    data object Home : AppRoute

    @Serializable
    data object HistoryMap : AppRoute

    @Serializable
    data object LogTable : AppRoute

    @Serializable
    data object ConnectionSettings : AppRoute
}
