package com.yamichi77.movement_log.data.auth

import com.yamichi77.movement_log.data.sync.AuthKeepAliveScheduler
import com.yamichi77.movement_log.data.sync.LogSyncScheduler

internal suspend fun establishManagedSession(
    authSessionStatusRepository: AuthSessionStatusRepository,
    authKeepAliveScheduler: AuthKeepAliveScheduler,
    logSyncScheduler: LogSyncScheduler,
) {
    authSessionStatusRepository.markSessionEstablished()
    authKeepAliveScheduler.start()
    logSyncScheduler.start()
}

internal fun stopManagedSessionSchedulers(
    authKeepAliveScheduler: AuthKeepAliveScheduler,
    logSyncScheduler: LogSyncScheduler,
) {
    authKeepAliveScheduler.stop()
    logSyncScheduler.stop()
}
