package dev.scoutremote.core.application

import dev.scoutremote.core.domain.AgentStatus
import dev.scoutremote.core.domain.RemoteResponse
import kotlinx.datetime.Instant

/**
 * Sync orchestration (spec §16.2). Foreground: protocol → status → active conversation →
 * activity delta. Background: lightweight status/response checks at OS-permitted intervals.
 * Uses ETags for mutable files (status, settings, in-flight responses) and stored delta
 * cursors to avoid re-listing the app folder.
 *
 * Includes the in-flight watch mode of the latency addendum
 * (docs/architecture-improvements.md §1): while foregrounded with a command in flight,
 * poll that command's response directly on a short interval with exponential backoff;
 * stop on terminal status or when backgrounded.
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
