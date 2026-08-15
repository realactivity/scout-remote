package dev.lantern.core.application

import dev.lantern.core.domain.AgentStatus
import dev.lantern.core.domain.RemoteResponse
import kotlinx.datetime.Instant

/**
 * Sync orchestration (spec §16.2). Foreground order is protocol, status, active conversation,
 * activity delta. Background: lightweight status/response checks at OS-permitted intervals.
 * Uses ETags for mutable files (status, settings, in-flight responses) and stored delta
 * cursors to avoid re-listing the app folder.
 *
 * Includes the in-flight watch mode (spec §16.2): while foregrounded with a command in
 * flight, poll that command's response directly on a short interval with exponential
 * backoff; stop on terminal status or when backgrounded.
 */
interface BridgeSyncService {

    suspend fun refreshForeground(): SyncResult

    suspend fun refreshBackground(): SyncResult

    /**
     * Foreground in-flight watch (spec §16.2). Polls one command's response
     * starting at [initialDelay] and backing off exponentially toward
     * [maxDelay]. Stops on terminal status or when [isForeground] is false.
     */
    suspend fun watchInFlight(
        commandId: String,
        isForeground: () -> Boolean,
        initialDelay: kotlin.time.Duration = kotlin.time.Duration.parse("15s"),
        maxDelay: kotlin.time.Duration = kotlin.time.Duration.parse("60s"),
        delay: suspend (kotlin.time.Duration) -> Unit = { kotlinx.coroutines.delay(it) },
    ): RemoteResponse?
}

data class SyncResult(
    val syncedUtc: Instant,
    val status: AgentStatus? = null,
    val newResponses: List<RemoteResponse> = emptyList(),
    val protocolIncompatible: Boolean = false,
)
