package dev.lantern.app.platform

import dev.lantern.core.platform.BackgroundScheduler
import kotlin.time.Duration

/**
 * Phase 0 stub. Records the requested cadence. Does not claim a WorkManager
 * refresh ran. Phase 1 attaches a real worker inside Doze constraints.
 */
class WorkManagerBackgroundScheduler : BackgroundScheduler {
    @Volatile
    var lastRequestedInterval: Duration? = null
        private set

    override suspend fun scheduleStatusRefresh(interval: Duration) {
        lastRequestedInterval = interval
    }

    override suspend fun cancelScheduledWork() {
        lastRequestedInterval = null
    }
}
