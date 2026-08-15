package dev.lantern.core.application

import dev.lantern.core.domain.CommandStatus
import dev.lantern.core.domain.RemoteResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class BridgeSyncServiceImpl(
    private val transport: BridgeTransport,
    private val clock: InstantClock,
    private val agentId: String = "primary",
    private val activeConversationId: String? = null,
    private val watchedCommandIds: MutableSet<String> = linkedSetOf(),
) : BridgeSyncService {

    @Volatile
    private var lastActivitySince = clock.now() - 1.minutes

    fun watch(commandId: String) {
        watchedCommandIds += commandId
    }

    override suspend fun refreshForeground(): SyncResult {
        val protocol = transport.readProtocol()
        if (protocol != null && !protocol.isCompatible) {
            return SyncResult(syncedUtc = clock.now(), protocolIncompatible = true)
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
        val responses = watchedCommandIds.mapNotNull { transport.readResponse(it) }
        responses.filter { it.status.isTerminal() }.forEach { watchedCommandIds.remove(it.commandId) }
        return SyncResult(
            syncedUtc = clock.now(),
            status = status,
            newResponses = responses,
            protocolIncompatible = false,
        )
    }

    override suspend fun refreshBackground(): SyncResult {
        val protocol = transport.readProtocol()
        if (protocol != null && !protocol.isCompatible) {
            return SyncResult(syncedUtc = clock.now(), protocolIncompatible = true)
        }
        val status = transport.readStatus(protocol?.agentId ?: agentId)
        val responses = watchedCommandIds.mapNotNull { transport.readResponse(it) }
        return SyncResult(
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
        delay: suspend (Duration) -> Unit,
    ): RemoteResponse? {
        watch(commandId)
        var wait = initialDelay
        while (isForeground()) {
            delay(wait)
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
