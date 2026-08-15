package dev.scoutremote.core.application

import dev.scoutremote.core.domain.ActivityEvent
import dev.scoutremote.core.domain.AgentStatus
import dev.scoutremote.core.domain.ArtifactDetails
import dev.scoutremote.core.domain.ConversationEvent
import dev.scoutremote.core.domain.ProtocolManifest
import dev.scoutremote.core.domain.RemoteCommand
import dev.scoutremote.core.domain.RemoteResponse
import kotlinx.datetime.Instant

/**
 * Port for the bridge data plane. The MVP implementation is the Graph App Folder adapter;
 * if Microsoft ships a first-party Scout API, it becomes a new adapter behind this same
 * interface. That is the architecture invariant of spec §20.
 */
interface BridgeTransport {

    suspend fun readProtocol(): ProtocolManifest?

    suspend fun readStatus(agentId: String): AgentStatus?

    /**
     * Writes a command to the inbox. Must be idempotent per commandId: retries after a
     * transient failure never produce a duplicate command (spec §15).
     */
    suspend fun enqueueCommand(command: RemoteCommand)

    suspend fun readResponse(commandId: String): RemoteResponse?

    /**
     * Reads the append-only event stream for one bridge-owned conversation, ordered by
     * eventId (ULIDs sort chronologically). Chats screen, spec §8.1/§9.
     */
    suspend fun readConversationEvents(conversationId: String): List<ConversationEvent>

    /**
     * Reads activity events at or after [sinceUtc]. This is the foreground "activity delta"
     * of spec §16.2. Activity screen, spec §9.
     */
    suspend fun readActivity(sinceUtc: Instant): List<ActivityEvent>

    /**
     * Resolves an artifact reference to OneDrive item details for the Artifacts screen.
     * Returns null when the item was deleted or is no longer accessible. The caller marks
     * it unavailable rather than failing (spec §15).
     */
    suspend fun resolveArtifact(driveItemId: String): ArtifactDetails?
}
