package com.yamichi77.movement_log.data.auth

import com.yamichi77.movement_log.data.sync.AuthKeepAliveScheduler
import com.yamichi77.movement_log.data.sync.LogSyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ManagedSessionCoordinatorTest {
    @Test
    fun establishManagedSession_marksSessionEstablishedAndStartsSchedulers() = runTest {
        val statusRepository = FakeAuthSessionStatusRepository()
        val authKeepAliveScheduler = FakeAuthKeepAliveScheduler()
        val logSyncScheduler = FakeLogSyncScheduler()

        establishManagedSession(
            authSessionStatusRepository = statusRepository,
            authKeepAliveScheduler = authKeepAliveScheduler,
            logSyncScheduler = logSyncScheduler,
        )

        assertEquals(1, statusRepository.markSessionEstablishedCalls)
        assertEquals(1, authKeepAliveScheduler.startCalls)
        assertEquals(1, logSyncScheduler.startCalls)
    }

    @Test
    fun stopManagedSessionSchedulers_stopsBothSchedulers() {
        val authKeepAliveScheduler = FakeAuthKeepAliveScheduler()
        val logSyncScheduler = FakeLogSyncScheduler()

        stopManagedSessionSchedulers(
            authKeepAliveScheduler = authKeepAliveScheduler,
            logSyncScheduler = logSyncScheduler,
        )

        assertEquals(1, authKeepAliveScheduler.stopCalls)
        assertEquals(1, logSyncScheduler.stopCalls)
    }

    private class FakeAuthSessionStatusRepository : AuthSessionStatusRepository {
        override val status: Flow<AuthSessionStatus> = emptyFlow()
        var markSessionEstablishedCalls: Int = 0

        override suspend fun markSessionEstablished() {
            markSessionEstablishedCalls += 1
        }

        override suspend fun markRefreshSucceeded() = Unit

        override suspend fun clearSession() = Unit

        override suspend fun markReauthRequired(
            reason: AuthErrorCode,
            detectedAtEpochMillis: Long,
        ) = Unit

        override suspend fun markReauthNotificationSent(notifiedAtEpochMillis: Long) = Unit
    }

    private class FakeAuthKeepAliveScheduler : AuthKeepAliveScheduler {
        var startCalls: Int = 0
        var stopCalls: Int = 0

        override fun start() {
            startCalls += 1
        }

        override fun stop() {
            stopCalls += 1
        }
    }

    private class FakeLogSyncScheduler : LogSyncScheduler {
        var startCalls: Int = 0
        var stopCalls: Int = 0

        override fun start() {
            startCalls += 1
        }

        override fun stop() {
            stopCalls += 1
        }
    }
}
