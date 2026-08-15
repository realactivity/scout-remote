package dev.lantern.core.application

import dev.lantern.core.domain.CommandStatus
import dev.lantern.core.domain.RemoteResponse
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
) : BridgeSyncService {

    private var lastActivitySince = clock.now() - 1.minutes
    private val refreshMutex = Mutex()
    private val watchMutex = Mutex()

    suspend fun watch(commandId: String) {
        watchMutex.withLock { watchedCommandIds += commandId }
    }

    override suspend fun refreshForeground(): SyncResult = refreshMutex.withLock {
        val protocol = transport.readProtocol()
        if (protocol != null && !protocol.isCompatible) {
            return@withLock SyncResult(syncedUtc = clock.now(), protocolIncompatible = true)
        }
        val status = transport.readStatus(protocol?.agentId ?: agentId)
        val conversationId = activeConversationId
        if (conversationId != null) {
            transport.readConversationEvents(conversationId)
        }
        val activity = transport.readActivity(lastActivitySince)
        if (activity.isNotEmpty()) {
            lastActivitySince = activity.maxOf { it.occurredUtc }
        }
        val responses = readWatchedResponses()
        return@withLock SyncResult(
            syncedUtc = clock.now(),
            status = status,
            newResponses = responses,
            protocolIncompatible = false,
        )
    }

    override suspend fun refreshBackground(): SyncResult = refreshMutex.withLock {
        val protocol = transport.readProtocol()
        if (protocol != null && !protocol.isCompatible) {
            return@withLock SyncResult(syncedUtc = clock.now(), protocolIncompatible = true)
        }
        val status = transport.readStatus(protocol?.agentId ?: agentId)
        val responses = readWatchedResponses()
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
                watchedCommandIds.remove(commandId)
                return response
            }
            wait = (wait * 2).coerceAtMost(maxDelay)
        }
        return transport.readResponse(commandId)
    }

    private suspend fun readWatchedResponses(): List<RemoteResponse> {
        val commandIds = watchMutex.withLock { watchedCommandIds.toList() }
        val responses = commandIds.mapNotNull { transport.readResponse(it) }
        val terminal = responses.filter { it.status.isTerminal() }.map { it.commandId }.toSet()
        if (terminal.isNotEmpty()) {
            watchMutex.withLock { watchedCommandIds.removeAll(terminal) }
        }
        return responses
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
