package com.yamichi77.movement_log.ui.history

import android.app.Application
import com.yamichi77.movement_log.BuildConfig
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryMapViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun emptySnapshot_showsNotRecordedText() = runTest {
        val fakeRepository = FakeMovementLogRepository()
        val viewModel = HistoryMapViewModel(
            application = Application(),
            repository = fakeRepository,
        )
        val collectJob = backgroundScope.launch {
            viewModel.uiState.collect {}
        }

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.points.isEmpty())
        assertEquals("--", viewModel.uiState.value.lastPointText)
        assertEquals("--", viewModel.uiState.value.lastUpdatedText)

        collectJob.cancel()
    }

    @Test
    fun nonEmptySnapshot_mapsPointsAndLastPointText() = runTest {
        val fakeRepository = FakeMovementLogRepository()
        val viewModel = HistoryMapViewModel(
            application = Application(),
            repository = fakeRepository,
        )
        val collectJob = backgroundScope.launch {
            viewModel.uiState.collect {}
        }
        val epochMillis = 1_700_000_123_000L

        fakeRepository.emitHistoryMapSnapshot(
            HistoryMapSnapshot(
                points = listOf(
                    HistoryMapPoint(latitude = 35.00001, longitude = 139.00001),
                    HistoryMapPoint(latitude = 35.12345, longitude = 139.98765),
                ),
                logCount = 2,
                lastLatitude = 35.12345,
                lastLongitude = 139.98765,
                lastUpdatedAtEpochMillis = epochMillis,
            ),
        )
        advanceUntilIdle()

        val expectedDateTime = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd HH:mm:ss",
            Locale.JAPAN,
        ).format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
        assertEquals(2, viewModel.uiState.value.points.size)
        assertEquals(35.12345, viewModel.uiState.value.lastLatitude)
        assertEquals(139.98765, viewModel.uiState.value.lastLongitude)
        assertEquals("35.1235, 139.9877", viewModel.uiState.value.lastPointText)
        assertEquals(expectedDateTime, viewModel.uiState.value.lastUpdatedText)
        assertEquals(2, viewModel.uiState.value.logCount)

        collectJob.cancel()
    }

    @Test
    fun isMapEnabled_matchesBuildConfig() = runTest {
        val fakeRepository = FakeMovementLogRepository()
        val viewModel = HistoryMapViewModel(
            application = Application(),
            repository = fakeRepository,
        )

        advanceUntilIdle()

        if (BuildConfig.MAPS_API_KEY.isNotBlank()) {
            assertTrue(viewModel.uiState.value.isMapEnabled)
        } else {
            assertFalse(viewModel.uiState.value.isMapEnabled)
        }
    }

    private class FakeMovementLogRepository : MovementLogRepository {
        private val historySnapshot = MutableStateFlow(
            HistoryMapSnapshot(
                points = emptyList(),
                logCount = 0,
                lastLatitude = null,
                lastLongitude = null,
                lastUpdatedAtEpochMillis = null,
            ),
        )

        override val homeTrackingSnapshot: Flow<HomeTrackingSnapshot> =
            MutableStateFlow(
                HomeTrackingSnapshot(
                    isCollecting = false,
                    activityStatus = "停止中",
                    latitude = null,
                    longitude = null,
                    updatedAtEpochMillis = null,
                    logCount = 0,
                ),
            )
        override val historyMapSnapshot: Flow<HistoryMapSnapshot> = historySnapshot
        override val logTableSnapshot: Flow<LogTableSnapshot> =
            MutableStateFlow(
                LogTableSnapshot(
                    items = emptyList(),
                    displayedCount = 0,
                    totalCount = 0,
                ),
            )

        fun emitHistoryMapSnapshot(value: HistoryMapSnapshot) {
            historySnapshot.value = value
        }

        override suspend fun startCollecting() = Unit
        override suspend fun stopCollecting() = Unit
    }
}
