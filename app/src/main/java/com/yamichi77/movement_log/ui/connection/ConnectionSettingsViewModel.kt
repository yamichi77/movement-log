package com.yamichi77.movement_log.ui.connection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yamichi77.movement_log.R
import com.yamichi77.movement_log.data.repository.ConnectionSettingsRepository
import com.yamichi77.movement_log.data.repository.ConnectionSettingsRepositoryProvider
import com.yamichi77.movement_log.data.repository.ConnectivityTestResult
import com.yamichi77.movement_log.data.repository.TrackingFrequencySettingsRepository
import com.yamichi77.movement_log.data.repository.TrackingFrequencySettingsRepositoryProvider
import com.yamichi77.movement_log.data.settings.ConnectionSettings
import com.yamichi77.movement_log.data.settings.TrackingFrequencySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ConnectionSettingsViewModel(
    application: Application,
    private val repository: ConnectionSettingsRepository =
        ConnectionSettingsRepositoryProvider.get(application),
    private val trackingFrequencySettingsRepository: TrackingFrequencySettingsRepository =
        TrackingFrequencySettingsRepositoryProvider.get(application),
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        repository = ConnectionSettingsRepositoryProvider.get(application),
        trackingFrequencySettingsRepository = TrackingFrequencySettingsRepositoryProvider.get(application),
    )

    private val sendStatusInitialText: String = runCatching {
        application.getString(R.string.connection_settings_send_status_initial)
    }.getOrDefault("")
    private val inputRequiredText: String = runCatching {
        application.getString(R.string.connection_settings_error_required)
    }.getOrDefault("Required")
    private val invalidBaseUrlText: String = runCatching {
        application.getString(R.string.connection_settings_error_invalid_base_url)
    }.getOrDefault("Invalid base URL")
    private val invalidUploadPathText: String = runCatching {
        application.getString(R.string.connection_settings_error_invalid_upload_path)
    }.getOrDefault("Upload path must start with /")
    private val connectivitySuccessText: String = runCatching {
        application.getString(R.string.connection_settings_connectivity_success)
    }.getOrDefault("Connectivity test passed")
    private val connectivitySuccessRotatedText: String = runCatching {
        application.getString(R.string.connection_settings_connectivity_success_rotated)
    }.getOrDefault("Connectivity test passed (session rotated)")
    private val connectivityFailedText: String = runCatching {
        application.getString(R.string.connection_settings_connectivity_failed)
    }.getOrDefault("Connectivity test failed")
    private val saveFailedText: String = runCatching {
        application.getString(R.string.connection_settings_save_failed)
    }.getOrDefault("Failed to save settings")
    private val intervalNumberOnlyText: String = runCatching {
        application.getString(R.string.tracking_frequency_error_number)
    }.getOrDefault("Enter a number")
    private val intervalRangeText: String = runCatching {
        application.getString(R.string.tracking_frequency_error_range)
    }.getOrDefault("Enter a value between 5 and 3600")

    private val _uiState = MutableStateFlow(
        ConnectionSettingsUiState(
            baseUrl = ConnectionSettings.Default.baseUrl,
            uploadPath = ConnectionSettings.Default.uploadPath,
            walkingIntervalInput = TrackingFrequencySettings.Default.walkingSec.toString(),
            runningIntervalInput = TrackingFrequencySettings.Default.runningSec.toString(),
            bicycleIntervalInput = TrackingFrequencySettings.Default.bicycleSec.toString(),
            vehicleIntervalInput = TrackingFrequencySettings.Default.vehicleSec.toString(),
            stillIntervalInput = TrackingFrequencySettings.Default.stillSec.toString(),
            sendStatusText = sendStatusInitialText,
        ),
    )
    val uiState: StateFlow<ConnectionSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _uiState.update { current ->
                    current.copy(
                        baseUrl = settings.baseUrl,
                        uploadPath = settings.uploadPath,
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.sendStatusText.collect { statusText ->
                _uiState.update { current ->
                    current.copy(
                        sendStatusText = statusText.ifBlank { sendStatusInitialText },
                    )
                }
            }
        }
        viewModelScope.launch {
            trackingFrequencySettingsRepository.settings.collect { settings ->
                _uiState.update { current ->
                    current.copy(
                        walkingIntervalInput = settings.walkingSec.toString(),
                        runningIntervalInput = settings.runningSec.toString(),
                        bicycleIntervalInput = settings.bicycleSec.toString(),
                        vehicleIntervalInput = settings.vehicleSec.toString(),
                        stillIntervalInput = settings.stillSec.toString(),
                    )
                }
            }
        }
    }

    fun onBaseUrlChanged(value: String) {
        _uiState.update {
            it.copy(
                baseUrl = value,
                baseUrlError = null,
                connectivityResult = ConnectionSettingsUiState.ConnectivityResult.None,
                connectivityMessage = null,
                saveResult = ConnectionSettingsUiState.SaveResult.None,
            )
        }
    }

    fun onUploadPathChanged(value: String) {
        _uiState.update {
            it.copy(
                uploadPath = value,
                uploadPathError = null,
                connectivityResult = ConnectionSettingsUiState.ConnectivityResult.None,
                connectivityMessage = null,
                saveResult = ConnectionSettingsUiState.SaveResult.None,
            )
        }
    }

    fun onWalkingIntervalChanged(value: String) {
        _uiState.update {
            it.copy(
                walkingIntervalInput = value,
                walkingIntervalError = null,
                saveResult = ConnectionSettingsUiState.SaveResult.None,
            )
        }
    }

    fun onRunningIntervalChanged(value: String) {
        _uiState.update {
            it.copy(
                runningIntervalInput = value,
                runningIntervalError = null,
                saveResult = ConnectionSettingsUiState.SaveResult.None,
            )
        }
    }

    fun onBicycleIntervalChanged(value: String) {
        _uiState.update {
            it.copy(
                bicycleIntervalInput = value,
                bicycleIntervalError = null,
                saveResult = ConnectionSettingsUiState.SaveResult.None,
            )
        }
    }

    fun onVehicleIntervalChanged(value: String) {
        _uiState.update {
            it.copy(
                vehicleIntervalInput = value,
                vehicleIntervalError = null,
                saveResult = ConnectionSettingsUiState.SaveResult.None,
            )
        }
    }

    fun onStillIntervalChanged(value: String) {
        _uiState.update {
            it.copy(
                stillIntervalInput = value,
                stillIntervalError = null,
                saveResult = ConnectionSettingsUiState.SaveResult.None,
            )
        }
    }

    fun onConnectivityTestClick() {
        val validationErrors = validateCurrentInput()
        if (validationErrors != null) {
            _uiState.update {
                it.copy(
                    baseUrlError = validationErrors.baseUrlError,
                    uploadPathError = validationErrors.uploadPathError,
                    connectivityResult = ConnectionSettingsUiState.ConnectivityResult.Error,
                    connectivityMessage = connectivityFailedText,
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTestingConnectivity = true,
                    connectivityResult = ConnectionSettingsUiState.ConnectivityResult.None,
                    connectivityMessage = null,
                )
            }

            val current = _uiState.value
            runCatching {
                repository.testConnectivity(
                    ConnectionSettings(
                        baseUrl = current.baseUrl.trim(),
                        uploadPath = current.uploadPath.trim(),
                    ),
                )
            }.onSuccess { result ->
                onConnectivitySuccess(result)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isTestingConnectivity = false,
                        connectivityResult = ConnectionSettingsUiState.ConnectivityResult.Error,
                        connectivityMessage = buildConnectivityFailureMessage(error),
                    )
                }
            }
        }
    }

    fun onSaveClick() {
        val validationErrors = validateCurrentInput()
        val frequencyValidation = validateTrackingFrequencyInput(_uiState.value)

        if (validationErrors != null || frequencyValidation.hasError) {
            _uiState.update {
                it.copy(
                    baseUrlError = validationErrors?.baseUrlError,
                    uploadPathError = validationErrors?.uploadPathError,
                    walkingIntervalError = frequencyValidation.walkingError,
                    runningIntervalError = frequencyValidation.runningError,
                    bicycleIntervalError = frequencyValidation.bicycleError,
                    vehicleIntervalError = frequencyValidation.vehicleError,
                    stillIntervalError = frequencyValidation.stillError,
                    saveResult = ConnectionSettingsUiState.SaveResult.Error,
                )
            }
            return
        }

        val frequencySettings = frequencyValidation.toSettings() ?: run {
            _uiState.update {
                it.copy(saveResult = ConnectionSettingsUiState.SaveResult.Error)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    saveResult = ConnectionSettingsUiState.SaveResult.None,
                )
            }

            val current = _uiState.value
            runCatching {
                repository.save(
                    ConnectionSettings(
                        baseUrl = current.baseUrl.trim(),
                        uploadPath = current.uploadPath.trim(),
                    ),
                )
                trackingFrequencySettingsRepository.save(frequencySettings)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveResult = ConnectionSettingsUiState.SaveResult.Success,
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveResult = ConnectionSettingsUiState.SaveResult.Error,
                        connectivityMessage = saveFailedText,
                    )
                }
            }
        }
    }

    private fun onConnectivitySuccess(result: ConnectivityTestResult) {
        _uiState.update {
            it.copy(
                isTestingConnectivity = false,
                connectivityResult = ConnectionSettingsUiState.ConnectivityResult.Success,
                connectivityMessage = if (result.sessionRotated) {
                    connectivitySuccessRotatedText
                } else {
                    connectivitySuccessText
                },
                saveResult = ConnectionSettingsUiState.SaveResult.Success,
            )
        }
    }

    private fun buildConnectivityFailureMessage(error: Throwable): String {
        val message = error.message?.takeIf { it.isNotBlank() }
        return if (message == null) {
            connectivityFailedText
        } else {
            "$connectivityFailedText: $message"
        }
    }

    private fun validateCurrentInput(): ValidationErrors? {
        val current = _uiState.value
        val baseUrlError = validateBaseUrl(current.baseUrl.trim())
        val uploadPathError = validateUploadPath(current.uploadPath.trim())
        if (baseUrlError == null && uploadPathError == null) {
            return null
        }
        return ValidationErrors(
            baseUrlError = baseUrlError,
            uploadPathError = uploadPathError,
        )
    }

    private fun validateTrackingFrequencyInput(
        uiState: ConnectionSettingsUiState,
    ): TrackingFrequencyValidationResult {
        val walking = validateIntervalInput(uiState.walkingIntervalInput)
        val running = validateIntervalInput(uiState.runningIntervalInput)
        val bicycle = validateIntervalInput(uiState.bicycleIntervalInput)
        val vehicle = validateIntervalInput(uiState.vehicleIntervalInput)
        val still = validateIntervalInput(uiState.stillIntervalInput)

        return TrackingFrequencyValidationResult(
            walkingSec = walking.value,
            runningSec = running.value,
            bicycleSec = bicycle.value,
            vehicleSec = vehicle.value,
            stillSec = still.value,
            walkingError = walking.error,
            runningError = running.error,
            bicycleError = bicycle.error,
            vehicleError = vehicle.error,
            stillError = still.error,
        )
    }

    private fun validateIntervalInput(raw: String): FieldValidation {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return FieldValidation(error = inputRequiredText)

        val parsed = trimmed.toIntOrNull() ?: return FieldValidation(error = intervalNumberOnlyText)
        if (parsed !in MIN_INTERVAL_SECONDS..MAX_INTERVAL_SECONDS) {
            return FieldValidation(error = intervalRangeText)
        }

        return FieldValidation(value = parsed)
    }

    private fun validateBaseUrl(baseUrl: String): String? {
        if (baseUrl.isBlank()) return inputRequiredText
        val normalized = if (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            baseUrl
        } else {
            "https://$baseUrl"
        }
        return if (normalized.toHttpUrlOrNull() == null) {
            invalidBaseUrlText
        } else {
            null
        }
    }

    private fun validateUploadPath(uploadPath: String): String? {
        if (uploadPath.isBlank()) return inputRequiredText
        return if (!uploadPath.startsWith("/")) invalidUploadPathText else null
    }

    private data class ValidationErrors(
        val baseUrlError: String?,
        val uploadPathError: String?,
    )

    private data class FieldValidation(
        val value: Int? = null,
        val error: String? = null,
    )

    private data class TrackingFrequencyValidationResult(
        val walkingSec: Int?,
        val runningSec: Int?,
        val bicycleSec: Int?,
        val vehicleSec: Int?,
        val stillSec: Int?,
        val walkingError: String?,
        val runningError: String?,
        val bicycleError: String?,
        val vehicleError: String?,
        val stillError: String?,
    ) {
        val hasError: Boolean
            get() = walkingError != null ||
                runningError != null ||
                bicycleError != null ||
                vehicleError != null ||
                stillError != null

        fun toSettings(): TrackingFrequencySettings? {
            val walking = walkingSec ?: return null
            val running = runningSec ?: return null
            val bicycle = bicycleSec ?: return null
            val vehicle = vehicleSec ?: return null
            val still = stillSec ?: return null
            return TrackingFrequencySettings(
                walkingSec = walking,
                runningSec = running,
                bicycleSec = bicycle,
                vehicleSec = vehicle,
                stillSec = still,
            )
        }
    }

    private companion object {
        const val MIN_INTERVAL_SECONDS = 5
        const val MAX_INTERVAL_SECONDS = 3600
    }
}
