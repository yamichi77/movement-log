package com.yamichi77.movement_log.ui.home

import android.app.Application
import com.yamichi77.movement_log.MainDispatcherRule
import com.yamichi77.movement_log.data.repository.HistoryMapPoint
import com.yamichi77.movement_log.data.repository.HistoryMapSnapshot
import com.yamichi77.movement_log.data.repository.HomeTrackingSnapshot
import com.yamichi77.movement_log.data.repository.LogTableSnapshot
import com.yamichi77.movement_log.data.repository.MovementLogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_startsCollectingAndMapsUnknownCoordinate() = runTest {
        val fakeRepository = FakeMovementLogRepository(
            initialHome = HomeTrackingSnapshot(
                isCollecting = false,
                activityStatus = "停止中",
                latitude = null,
                longitude = null,
                updatedAtEpochMillis = null,
                logCount = 0,
            ),
        )
        val viewModel = HomeViewModel(
            application = Application(),
            repository = fakeRepository,
        )
        val collectJob = backgroundScope.launch {
            viewModel.uiState.collect {}
        }

        advanceUntilIdle()

        assertEquals(1, fakeRepository.startCollectingCallCount)
        assertEquals("--", viewModel.uiState.value.latitudeText)
        assertEquals("--", viewModel.uiState.value.longitudeText)
        assertEquals("--", viewModel.uiState.value.lastUpdatedText)

        collectJob.cancel()
    }

    @Test
    fun mapsSnapshotToUiState_formatsCoordinateAndDateTime() = runTest {
        val fakeRepository = FakeMovementLogRepository()
        val viewModel = HomeViewModel(
            application = Application(),
            repository = fakeRepository,
        )
        val collectJob = backgroundScope.launch {
            viewModel.uiState.collect {}
        }
        val epochMillis = 1_700_000_000_000L

        fakeRepository.emitHomeSnapshot(
            HomeTrackingSnapshot(
                isCollecting = true,
                activityStatus = "徒歩",
                latitude = 35.123456,
                longitude = 139.987654,
                updatedAtEpochMillis = epochMillis,
                logCount = 9,
            ),
        )
        advanceUntilIdle()

        val expectedDateTime = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd HH:mm:ss",
            Locale.JAPAN,
        ).format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
        assertTrue(viewModel.uiState.value.isCollecting)
        assertEquals("徒歩", viewModel.uiState.value.activityStatus)
        assertEquals("35.1235", viewModel.uiState.value.latitudeText)
        assertEquals("139.9877", viewModel.uiState.value.longitudeText)
        assertEquals(expectedDateTime, viewModel.uiState.value.lastUpdatedText)
        assertEquals(9, viewModel.uiState.value.logCount)

        collectJob.cancel()
    }

    @Test
    fun onStartAndStopCollectingClick_invokesRepositoryMethods() = runTest {
        val fakeRepository = FakeMovementLogRepository()
        val viewModel = HomeViewModel(
            application = Application(),
            repository = fakeRepository,
        )

        advanceUntilIdle()
        viewModel.onStartCollectingClick()
        viewModel.onStopCollectingClick()
        advanceUntilIdle()

        assertEquals(2, fakeRepository.startCollectingCallCount)
        assertEquals(1, fakeRepository.stopCollectingCallCount)
    }

    @Test
    fun mapsHistorySnapshotToPreview_usesLastTwentyPoints() = runTest {
        val fakeRepository = FakeMovementLogRepository()
        val viewModel = HomeViewModel(
            application = Application(),
            repository = fakeRepository,
        )
        val collectJob = backgroundScope.launch {
            viewModel.uiState.collect {}
        }
        val historyPoints = (1..25).map { index ->
            HistoryMapPoint(
                latitude = 35.0 + (index / 1_000.0),
                longitude = 139.0 + (index / 1_000.0),
            )
        }

        fakeRepository.emitHistorySnapshot(
            HistoryMapSnapshot(
                points = historyPoints,
                logCount = historyPoints.size,
                lastLatitude = historyPoints.last().latitude,
                lastLongitude = historyPoints.last().longitude,
                lastUpdatedAtEpochMillis = null,
            ),
        )
        advanceUntilIdle()

        val previewPoints = viewModel.uiState.value.mapPreviewPoints
        val expectedPreviewPoints = historyPoints.takeLast(20)
        assertEquals(20, previewPoints.size)
        assertEquals(expectedPreviewPoints.first().latitude, previewPoints.first().latitude, 0.0)
        assertEquals(expectedPreviewPoints.first().longitude, previewPoints.first().longitude, 0.0)
        assertEquals(expectedPreviewPoints.last().latitude, previewPoints.last().latitude, 0.0)
        assertEquals(expectedPreviewPoints.last().longitude, previewPoints.last().longitude, 0.0)
        assertEquals(
            expectedPreviewPoints.last().latitude,
            viewModel.uiState.value.lastPreviewLatitude ?: Double.NaN,
            0.0,
        )
        assertEquals(
            expectedPreviewPoints.last().longitude,
            viewModel.uiState.value.lastPreviewLongitude ?: Double.NaN,
            0.0,
        )

        collectJob.cancel()
    }

    private class FakeMovementLogRepository(
        initialHome: HomeTrackingSnapshot = HomeTrackingSnapshot(
            isCollecting = false,
            activityStatus = "停止中",
            latitude = null,
            longitude = null,
            updatedAtEpochMillis = null,
            logCount = 0,
        ),
    ) : MovementLogRepository {
        private val homeSnapshot = MutableStateFlow(initialHome)
        private val historySnapshot = MutableStateFlow(
            HistoryMapSnapshot(
                points = emptyList(),
                logCount = 0,
                lastLatitude = null,
                lastLongitude = null,
                lastUpdatedAtEpochMillis = null,
            ),
        )

        override val homeTrackingSnapshot: Flow<HomeTrackingSnapshot> = homeSnapshot
        override val historyMapSnapshot: Flow<HistoryMapSnapshot> = historySnapshot
        override val logTableSnapshot: Flow<LogTableSnapshot> =
            MutableStateFlow(
                LogTableSnapshot(
                    items = emptyList(),
                    displayedCount = 0,
                    totalCount = 0,
                ),
            )

        var startCollectingCallCount: Int = 0
        var stopCollectingCallCount: Int = 0

        fun emitHomeSnapshot(value: HomeTrackingSnapshot) {
            homeSnapshot.value = value
        }

        fun emitHistorySnapshot(value: HistoryMapSnapshot) {
            historySnapshot.value = value
        }

        override suspend fun startCollecting() {
            startCollectingCallCount += 1
        }

        override suspend fun stopCollecting() {
            stopCollectingCallCount += 1
        }
    }
}
