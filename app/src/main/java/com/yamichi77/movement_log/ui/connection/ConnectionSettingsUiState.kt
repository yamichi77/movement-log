package com.yamichi77.movement_log.ui.connection

data class ConnectionSettingsUiState(
    val baseUrl: String = "",
    val uploadPath: String = "",
    val walkingIntervalInput: String = "",
    val runningIntervalInput: String = "",
    val bicycleIntervalInput: String = "",
    val vehicleIntervalInput: String = "",
    val stillIntervalInput: String = "",
    val sendStatusText: String = "",
    val baseUrlError: String? = null,
    val uploadPathError: String? = null,
    val walkingIntervalError: String? = null,
    val runningIntervalError: String? = null,
    val bicycleIntervalError: String? = null,
    val vehicleIntervalError: String? = null,
    val stillIntervalError: String? = null,
    val isTestingConnectivity: Boolean = false,
    val connectivityResult: ConnectivityResult = ConnectivityResult.None,
    val connectivityMessage: String? = null,
    val isSaving: Boolean = false,
    val saveResult: SaveResult = SaveResult.None,
    val isLoggingOut: Boolean = false,
    val logoutResult: LogoutResult = LogoutResult.None,
    val logoutMessage: String? = null,
) {
    enum class ConnectivityResult {
        None,
        Success,
        Error,
    }

    enum class SaveResult {
        None,
        Success,
        Error,
    }

    enum class LogoutResult {
        None,
        Success,
        Error,
    }
}
