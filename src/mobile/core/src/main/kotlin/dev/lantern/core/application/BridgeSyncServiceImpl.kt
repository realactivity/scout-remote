package dev.lantern.core.application

import dev.lantern.core.domain.CommandStatus
import dev.lantern.core.domain.RemoteResponse
import dev.lantern.core.metrics.GaugeMetric
import dev.lantern.core.metrics.LanternMetrics
import dev.lantern.core.metrics.MetricOperation
import dev.lantern.core.metrics.MetricGauge
import dev.lantern.core.metrics.MetricResult
import dev.lantern.core.metrics.NoOpLanternMetrics
import dev.lantern.core.metrics.OperationMetric
import dev.lantern.core.sync.InMemorySyncStateStore
import dev.lantern.core.sync.SyncStateStore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BridgeSyncServiceImpl(
    private val transport: BridgeTransport,
    private val clock: InstantClock,
    private val agentId: String = "primary",
    private val activeConversationId: String? = null,
    private val watchedCommandIds: MutableSet<String> = linkedSetOf(),
    private val syncState: SyncStateStore =
        (transport as? StatefulBridgeTransport)?.syncState ?: InMemorySyncStateStore(),
    private val metrics: LanternMetrics = NoOpLanternMetrics,
) : BridgeSyncService {

    private val refreshMutex = Mutex()
    private val watchMutex = Mutex()

    suspend fun watch(commandId: String) {
        watchMutex.withLock { watchedCommandIds += commandId }
        syncState.setCommandWatched(commandId, true)
    }

    override suspend fun refreshForeground(): SyncResult = refreshMutex.withLock {
        val started = System.nanoTime()
        (transport as? IncrementalBridgeTransport)?.applyDelta()
        val protocol = transport.readProtocol()
        if (protocol != null && !protocol.isCompatible) {
            recordSync(MetricOperation.SYNC_FOREGROUND, MetricResult.PROTOCOL_INCOMPATIBLE, started)
            return@withLock SyncResult(syncedUtc = clock.now(), protocolIncompatible = true)
        }
        val status = transport.readStatus(protocol?.agentId ?: agentId)
        val conversationId = activeConversationId
        if (conversationId != null) {
            transport.readConversationEvents(conversationId)
        }
        val activitySince = syncState.lastActivitySince() ?: clock.now() - 1.minutes
        val activity = transport.readActivity(activitySince)
        if (activity.isNotEmpty()) {
            syncState.setLastActivitySince(activity.maxOf { it.occurredUtc })
        }
        val responses = readWatchedResponses()
        status?.let {
            metrics.recordGauge(
                GaugeMetric(
                    MetricGauge.SYNC_AGE_MS,
                    (clock.now() - it.lastSeenUtc).inWholeMilliseconds.coerceAtLeast(0),
                ),
            )
        }
        recordSync(MetricOperation.SYNC_FOREGROUND, MetricResult.SUCCESS, started)
        return@withLock SyncResult(
            syncedUtc = clock.now(),
            status = status,
            newResponses = responses,
            protocolIncompatible = false,
        )
    }

    override suspend fun refreshBackground(): SyncResult = refreshMutex.withLock {
        val started = System.nanoTime()
        (transport as? IncrementalBridgeTransport)?.applyDelta()
        val protocol = transport.readProtocol()
        if (protocol != null && !protocol.isCompatible) {
            recordSync(MetricOperation.SYNC_BACKGROUND, MetricResult.PROTOCOL_INCOMPATIBLE, started)
            return@withLock SyncResult(syncedUtc = clock.now(), protocolIncompatible = true)
        }
        val status = transport.readStatus(protocol?.agentId ?: agentId)
        val responses = readWatchedResponses()
        status?.let {
            metrics.recordGauge(
                GaugeMetric(
                    MetricGauge.SYNC_AGE_MS,
                    (clock.now() - it.lastSeenUtc).inWholeMilliseconds.coerceAtLeast(0),
                ),
            )
        }
        recordSync(MetricOperation.SYNC_BACKGROUND, MetricResult.SUCCESS, started)
        return@withLock SyncResult(
            syncedUtc = clock.now(),
            status = status,
            newResponses = responses,
        )
    }

    override suspend fun watchInFlight(
        commandId: String,
        isForeground: () -> Boolean,
        initialDelay: Duration,
        maxDelay: Duration,
        maxWatchDuration: Duration,
        delay: suspend (Duration) -> Unit,
    ): RemoteResponse? {
        watch(commandId)
        var wait = initialDelay
        var elapsed = Duration.ZERO
        while (isForeground() && elapsed < maxWatchDuration) {
            val remaining = maxWatchDuration - elapsed
            val nextWait = wait.coerceAtMost(remaining)
            delay(nextWait)
            elapsed += nextWait
            if (!isForeground()) {
                return null
            }
            val response = transport.readResponse(commandId)
            if (response != null && response.status.isTerminal()) {
                watchMutex.withLock { watchedCommandIds.remove(commandId) }
                syncState.setCommandWatched(commandId, false)
                return response
            }
            wait = (wait * 2).coerceAtMost(maxDelay)
        }
        return transport.readResponse(commandId)
    }

    private suspend fun readWatchedResponses(): List<RemoteResponse> {
        val persisted = syncState.watchedCommandIds()
        val commandIds = watchMutex.withLock {
            watchedCommandIds += persisted
            watchedCommandIds.toList()
        }
        val responses = commandIds.mapNotNull { transport.readResponse(it) }
        val terminal = responses.filter { it.status.isTerminal() }.map { it.commandId }.toSet()
        if (terminal.isNotEmpty()) {
            watchMutex.withLock { watchedCommandIds.removeAll(terminal) }
            terminal.forEach { syncState.setCommandWatched(it, false) }
        }
        return responses
    }

    private fun recordSync(operation: MetricOperation, result: MetricResult, started: Long) {
        metrics.recordOperation(
            OperationMetric(
                operation = operation,
                result = result,
                durationMs = (System.nanoTime() - started) / 1_000_000,
            ),
        )
    }
}

fun CommandStatus.isTerminal(): Boolean = when (this) {
    CommandStatus.COMPLETED,
    CommandStatus.FAILED,
    CommandStatus.EXPIRED,
    CommandStatus.CANCELED,
    CommandStatus.WAITING_FOR_DESKTOP_APPROVAL,
    -> true
    CommandStatus.ACKNOWLEDGED,
    CommandStatus.RUNNING,
    -> false
}
