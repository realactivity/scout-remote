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
}

data class SyncResult(
    val syncedUtc: Instant,
    val status: AgentStatus? = null,
    val newResponses: List<RemoteResponse> = emptyList(),
    val protocolIncompatible: Boolean = false,
)
