package com.yamichi77.movement_log.ui.logtable

import android.app.Application
import com.yamichi77.movement_log.MainDispatcherRule
import com.yamichi77.movement_log.data.repository.HistoryMapSnapshot
import com.yamichi77.movement_log.data.repository.HomeTrackingSnapshot
import com.yamichi77.movement_log.data.repository.LogTableItem
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
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class LogTableViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun mapsSnapshotToUiState_formatsRowsAndCounts() = runTest {
        val fakeRepository = FakeMovementLogRepository()
        val viewModel = LogTableViewModel(
            application = Application(),
            repository = fakeRepository,
        )
        val collectJob = backgroundScope.launch {
            viewModel.uiState.collect {}
        }
        val epochMillis = 1_700_000_456_000L

        fakeRepository.emitLogTableSnapshot(
            LogTableSnapshot(
                items = listOf(
                    LogTableItem(
                        id = 42L,
                        recordedAtEpochMillis = epochMillis,
                        latitude = 35.123456,
                        longitude = 139.987654,
                        activityStatus = "徒歩",
                    ),
                ),
                displayedCount = 1,
                totalCount = 51,
            ),
        )
        advanceUntilIdle()

        val expectedDateTime = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd HH:mm:ss",
            Locale.JAPAN,
        ).format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
        val item = viewModel.uiState.value.items.first()
        assertEquals(1, viewModel.uiState.value.items.size)
        assertEquals(42L, item.stableId)
        assertEquals(expectedDateTime, item.timeText)
        assertEquals("35.1235", item.latitudeText)
        assertEquals("139.9877", item.longitudeText)
        assertEquals("徒歩", item.activityStatusText)
        assertEquals(1, viewModel.uiState.value.displayedCount)
        assertEquals(51, viewModel.uiState.value.totalCount)

        collectJob.cancel()
    }

    private class FakeMovementLogRepository : MovementLogRepository {
        private val logTable = MutableStateFlow(
            LogTableSnapshot(
                items = emptyList(),
                displayedCount = 0,
                totalCount = 0,
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
        override val historyMapSnapshot: Flow<HistoryMapSnapshot> =
            MutableStateFlow(
                HistoryMapSnapshot(
                    points = emptyList(),
                    logCount = 0,
                    lastLatitude = null,
                    lastLongitude = null,
                    lastUpdatedAtEpochMillis = null,
                ),
            )
        override val logTableSnapshot: Flow<LogTableSnapshot> = logTable

        fun emitLogTableSnapshot(value: LogTableSnapshot) {
            logTable.value = value
        }

        override suspend fun startCollecting() = Unit
        override suspend fun stopCollecting() = Unit
    }
}
