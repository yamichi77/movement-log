package com.yamichi77.movement_log.ui.connection

import android.app.Application
import com.yamichi77.movement_log.MainDispatcherRule
import com.yamichi77.movement_log.data.repository.ConnectionSettingsRepository
import com.yamichi77.movement_log.data.repository.ConnectivityTestResult
import com.yamichi77.movement_log.data.repository.TrackingFrequencySettingsRepository
import com.yamichi77.movement_log.data.settings.ConnectionSettings
import com.yamichi77.movement_log.data.settings.TrackingFrequencySettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_readsPersistedSettingsIntoUiState() = runTest {
        val fakeRepository = FakeConnectionSettingsRepository(
            initial = ConnectionSettings(
                baseUrl = "https://example.com",
                uploadPath = "/api/custom-upload",
            ),
            initialSendStatus = "send status",
        )
        val fakeFrequencyRepository = FakeTrackingFrequencySettingsRepository(
            initial = TrackingFrequencySettings(
                walkingSec = 11,
                runningSec = 22,
                bicycleSec = 33,
                vehicleSec = 44,
                stillSec = 55,
            ),
        )
        val viewModel = ConnectionSettingsViewModel(
            application = Application(),
            repository = fakeRepository,
            trackingFrequencySettingsRepository = fakeFrequencyRepository,
        )

        advanceUntilIdle()

        assertEquals("https://example.com", viewModel.uiState.value.baseUrl)
        assertEquals("/api/custom-upload", viewModel.uiState.value.uploadPath)
        assertEquals("send status", viewModel.uiState.value.sendStatusText)
        assertEquals("11", viewModel.uiState.value.walkingIntervalInput)
        assertEquals("22", viewModel.uiState.value.runningIntervalInput)
        assertEquals("33", viewModel.uiState.value.bicycleIntervalInput)
        assertEquals("44", viewModel.uiState.value.vehicleIntervalInput)
        assertEquals("55", viewModel.uiState.value.stillIntervalInput)
    }

    @Test
    fun onConnectivityTestClick_withInvalidFields_setsValidationErrors() = runTest {
        val fakeRepository = FakeConnectionSettingsRepository(initial = ConnectionSettings.Default)
        val fakeFrequencyRepository = FakeTrackingFrequencySettingsRepository(
            initial = TrackingFrequencySettings.Default,
        )
        val viewModel = ConnectionSettingsViewModel(
            application = Application(),
            repository = fakeRepository,
            trackingFrequencySettingsRepository = fakeFrequencyRepository,
        )

        advanceUntilIdle()
        viewModel.onBaseUrlChanged("")
        viewModel.onUploadPathChanged("invalid")
        viewModel.onConnectivityTestClick()

        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.baseUrlError)
        assertNotNull(viewModel.uiState.value.uploadPathError)
        assertEquals(0, fakeRepository.connectivityTestCallCount)
    }

    @Test
    fun onConnectivityTestClick_success_updatesState() = runTest {
        val fakeRepository = FakeConnectionSettingsRepository(
            initial = ConnectionSettings.Default,
            connectivityResult = ConnectivityTestResult(sessionRotated = true),
        )
        val fakeFrequencyRepository = FakeTrackingFrequencySettingsRepository(
            initial = TrackingFrequencySettings.Default,
        )
        val viewModel = ConnectionSettingsViewModel(
            application = Application(),
            repository = fakeRepository,
            trackingFrequencySettingsRepository = fakeFrequencyRepository,
        )

        advanceUntilIdle()
        viewModel.onBaseUrlChanged("https://portal.yamichi.com")
        viewModel.onUploadPathChanged("/api/movelog")
        viewModel.onConnectivityTestClick()

        advanceUntilIdle()

        assertEquals(1, fakeRepository.connectivityTestCallCount)
        assertEquals(
            ConnectionSettingsUiState.ConnectivityResult.Success,
            viewModel.uiState.value.connectivityResult,
        )
        assertEquals(ConnectionSettingsUiState.SaveResult.Success, viewModel.uiState.value.saveResult)
    }

    @Test
    fun onConnectivityTestClick_failure_setsErrorStatus() = runTest {
        val fakeRepository = FakeConnectionSettingsRepository(
            initial = ConnectionSettings.Default,
            connectivityError = IllegalStateException("network down"),
        )
        val fakeFrequencyRepository = FakeTrackingFrequencySettingsRepository(
            initial = TrackingFrequencySettings.Default,
        )
        val viewModel = ConnectionSettingsViewModel(
            application = Application(),
            repository = fakeRepository,
            trackingFrequencySettingsRepository = fakeFrequencyRepository,
        )

        advanceUntilIdle()
        viewModel.onBaseUrlChanged("https://portal.yamichi.com")
        viewModel.onUploadPathChanged("/api/movelog")
        viewModel.onConnectivityTestClick()

        advanceUntilIdle()

        assertEquals(
            ConnectionSettingsUiState.ConnectivityResult.Error,
            viewModel.uiState.value.connectivityResult,
        )
        assertTrue(viewModel.uiState.value.connectivityMessage.orEmpty().contains("network down"))
    }

    @Test
    fun onSaveClick_whenFrequencyIsNotNumeric_setsFrequencyError() = runTest {
        val fakeRepository = FakeConnectionSettingsRepository(initial = ConnectionSettings.Default)
        val fakeFrequencyRepository = FakeTrackingFrequencySettingsRepository(
            initial = TrackingFrequencySettings.Default,
        )
        val viewModel = ConnectionSettingsViewModel(
            application = Application(),
            repository = fakeRepository,
            trackingFrequencySettingsRepository = fakeFrequencyRepository,
        )

        advanceUntilIdle()
        viewModel.onBaseUrlChanged("https://api.example.com")
        viewModel.onUploadPathChanged("/api/movelog")
        viewModel.onWalkingIntervalChanged("abc")
        viewModel.onSaveClick()

        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.walkingIntervalError)
        assertEquals(ConnectionSettingsUiState.SaveResult.Error, viewModel.uiState.value.saveResult)
        assertEquals(0, fakeRepository.saveCallCount)
        assertEquals(0, fakeFrequencyRepository.saveCallCount)
    }

    @Test
    fun onSaveClick_whenFrequencyIsOutOfRange_setsFrequencyError() = runTest {
        val fakeRepository = FakeConnectionSettingsRepository(initial = ConnectionSettings.Default)
        val fakeFrequencyRepository = FakeTrackingFrequencySettingsRepository(
            initial = TrackingFrequencySettings.Default,
        )
        val viewModel = ConnectionSettingsViewModel(
            application = Application(),
            repository = fakeRepository,
            trackingFrequencySettingsRepository = fakeFrequencyRepository,
        )

        advanceUntilIdle()
        viewModel.onBaseUrlChanged("https://api.example.com")
        viewModel.onUploadPathChanged("/api/movelog")
        viewModel.onStillIntervalChanged("3601")
        viewModel.onSaveClick()

        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.stillIntervalError)
        assertEquals(ConnectionSettingsUiState.SaveResult.Error, viewModel.uiState.value.saveResult)
        assertEquals(0, fakeRepository.saveCallCount)
        assertEquals(0, fakeFrequencyRepository.saveCallCount)
    }

    @Test
    fun onSaveClick_savesConnectionAndFrequencySettings() = runTest {
        val fakeRepository = FakeConnectionSettingsRepository(initial = ConnectionSettings.Default)
        val fakeFrequencyRepository = FakeTrackingFrequencySettingsRepository(
            initial = TrackingFrequencySettings.Default,
        )
        val viewModel = ConnectionSettingsViewModel(
            application = Application(),
            repository = fakeRepository,
            trackingFrequencySettingsRepository = fakeFrequencyRepository,
        )

        advanceUntilIdle()
        viewModel.onBaseUrlChanged("https://api.example.com")
        viewModel.onUploadPathChanged("/api/new-path")
        viewModel.onWalkingIntervalChanged("31")
        viewModel.onRunningIntervalChanged("26")
        viewModel.onBicycleIntervalChanged("21")
        viewModel.onVehicleIntervalChanged("16")
        viewModel.onStillIntervalChanged("901")
        viewModel.onSaveClick()

        advanceUntilIdle()

        val savedConnection = fakeRepository.lastSaved
        val savedFrequency = fakeFrequencyRepository.lastSaved
        assertNotNull(savedConnection)
        assertNotNull(savedFrequency)
        assertEquals("https://api.example.com", savedConnection?.baseUrl)
        assertEquals("/api/new-path", savedConnection?.uploadPath)
        assertEquals(31, savedFrequency?.walkingSec)
        assertEquals(26, savedFrequency?.runningSec)
        assertEquals(21, savedFrequency?.bicycleSec)
        assertEquals(16, savedFrequency?.vehicleSec)
        assertEquals(901, savedFrequency?.stillSec)
        assertEquals(
            ConnectionSettingsUiState.SaveResult.Success,
            viewModel.uiState.value.saveResult,
        )
        assertEquals(1, fakeRepository.saveCallCount)
        assertEquals(1, fakeFrequencyRepository.saveCallCount)
    }

    @Test
    fun onSaveClick_whenFrequencyRepositoryThrows_updatesErrorResult() = runTest {
        val fakeRepository = FakeConnectionSettingsRepository(initial = ConnectionSettings.Default)
        val fakeFrequencyRepository = FakeTrackingFrequencySettingsRepository(
            initial = TrackingFrequencySettings.Default,
            shouldFailOnSave = true,
        )
        val viewModel = ConnectionSettingsViewModel(
            application = Application(),
            repository = fakeRepository,
            trackingFrequencySettingsRepository = fakeFrequencyRepository,
        )

        advanceUntilIdle()
        viewModel.onBaseUrlChanged("https://api.example.com")
        viewModel.onUploadPathChanged("/api/movelog")
        viewModel.onSaveClick()

        advanceUntilIdle()

        assertEquals(ConnectionSettingsUiState.SaveResult.Error, viewModel.uiState.value.saveResult)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(1, fakeRepository.saveCallCount)
        assertEquals(1, fakeFrequencyRepository.saveCallCount)
    }

    @Test
    fun onLogoutClick_callsRepositoryAndUpdatesSuccessResult() = runTest {
        val fakeRepository = FakeConnectionSettingsRepository(initial = ConnectionSettings.Default)
        val fakeFrequencyRepository = FakeTrackingFrequencySettingsRepository(
            initial = TrackingFrequencySettings.Default,
        )
        val viewModel = ConnectionSettingsViewModel(
            application = Application(),
            repository = fakeRepository,
            trackingFrequencySettingsRepository = fakeFrequencyRepository,
        )

        advanceUntilIdle()
        viewModel.onLogoutClick()

        advanceUntilIdle()

        assertEquals(1, fakeRepository.logoutCallCount)
        assertEquals(
            ConnectionSettingsUiState.LogoutResult.Success,
            viewModel.uiState.value.logoutResult,
        )
        assertFalse(viewModel.uiState.value.isLoggingOut)
    }

    @Test
    fun onLogoutClick_whenRepositoryThrows_updatesErrorResult() = runTest {
        val fakeRepository = FakeConnectionSettingsRepository(
            initial = ConnectionSettings.Default,
            shouldFailOnLogout = true,
        )
        val fakeFrequencyRepository = FakeTrackingFrequencySettingsRepository(
            initial = TrackingFrequencySettings.Default,
        )
        val viewModel = ConnectionSettingsViewModel(
            application = Application(),
            repository = fakeRepository,
            trackingFrequencySettingsRepository = fakeFrequencyRepository,
        )

        advanceUntilIdle()
        viewModel.onLogoutClick()

        advanceUntilIdle()

        assertEquals(1, fakeRepository.logoutCallCount)
        assertEquals(
            ConnectionSettingsUiState.LogoutResult.Error,
            viewModel.uiState.value.logoutResult,
        )
        assertFalse(viewModel.uiState.value.isLoggingOut)
    }

    private class FakeConnectionSettingsRepository(
        initial: ConnectionSettings,
        initialSendStatus: String = "",
        private val shouldFailOnSave: Boolean = false,
        private val shouldFailOnLogout: Boolean = false,
        private val connectivityResult: ConnectivityTestResult = ConnectivityTestResult(
            sessionRotated = false,
        ),
        private val connectivityError: Throwable? = null,
    ) : ConnectionSettingsRepository {
        private val settingsState = MutableStateFlow(initial)
        private val sendStatusState = MutableStateFlow(initialSendStatus)

        override val settings: Flow<ConnectionSettings> = settingsState
        override val sendStatusText: Flow<String> = sendStatusState

        var lastSaved: ConnectionSettings? = null
        var saveCallCount: Int = 0
        var connectivityTestCallCount: Int = 0
        var logoutCallCount: Int = 0

        override suspend fun save(settings: ConnectionSettings) {
            saveCallCount += 1
            if (shouldFailOnSave) {
                throw IllegalStateException("save failed for test")
            }
            lastSaved = settings
            settingsState.value = settings
        }

        override suspend fun saveSendStatusText(text: String) {
            sendStatusState.value = text
        }

        override suspend fun testConnectivity(settings: ConnectionSettings): ConnectivityTestResult {
            connectivityTestCallCount += 1
            connectivityError?.let { throw it }
            settingsState.value = settings
            return connectivityResult
        }

        override suspend fun logout() {
            logoutCallCount += 1
            if (shouldFailOnLogout) {
                throw IllegalStateException("logout failed for test")
            }
        }
    }

    private class FakeTrackingFrequencySettingsRepository(
        initial: TrackingFrequencySettings,
        private val shouldFailOnSave: Boolean = false,
    ) : TrackingFrequencySettingsRepository {
        private val settingsState = MutableStateFlow(initial)

        override val settings: Flow<TrackingFrequencySettings> = settingsState

        var lastSaved: TrackingFrequencySettings? = null
        var saveCallCount: Int = 0

        override suspend fun save(settings: TrackingFrequencySettings) {
            saveCallCount += 1
            if (shouldFailOnSave) {
                throw IllegalStateException("save failed for test")
            }
            lastSaved = settings
            settingsState.value = settings
        }
    }
}
