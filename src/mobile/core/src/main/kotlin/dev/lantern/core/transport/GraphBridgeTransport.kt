package dev.lantern.core.transport

import dev.lantern.core.application.BridgeTransport
import dev.lantern.core.domain.ActivityEvent
import dev.lantern.core.domain.AgentStatus
import dev.lantern.core.domain.ArtifactDetails
import dev.lantern.core.domain.ConversationEvent
import dev.lantern.core.domain.ProtocolManifest
import dev.lantern.core.domain.RemoteCommand
import dev.lantern.core.domain.RemoteResponse
import kotlinx.datetime.Instant

/**
 * [BridgeTransport] over the OneDrive App Folder (/me/drive/special/approot), spec §6 and §16.2.
 * Implementation notes for Phase 0:
 *  - ETags/If-Match for mutable files: status, settings, and in-flight responses (a response
 *    file is updated through acknowledged/running and becomes immutable only at a terminal
 *    status, spec §15.1). Commands and conversation/activity events are immutable once written.
 *  - Store driveItem IDs and delta cursors; never re-list the whole app folder per refresh.
 *  - Validate protocol.json (schema + major version) before touching any other file.
 *  - Enqueue uses create-if-absent semantics keyed on commandId so retries cannot duplicate.
 */
class GraphBridgeTransport : BridgeTransport {

    override suspend fun readProtocol(): ProtocolManifest? =
        TODO("Phase 0: implement against Microsoft Graph App Folder.")

    override suspend fun readStatus(agentId: String): AgentStatus? =
        TODO("Phase 0: implement against Microsoft Graph App Folder.")

    override suspend fun enqueueCommand(command: RemoteCommand): Unit =
        TODO("Phase 0: implement against Microsoft Graph App Folder.")

    override suspend fun readResponse(commandId: String): RemoteResponse? =
        TODO("Phase 0: implement against Microsoft Graph App Folder.")

    override suspend fun readConversationEvents(conversationId: String): List<ConversationEvent> =
        TODO("Phase 1: implement against Microsoft Graph App Folder.")

    override suspend fun readActivity(sinceUtc: Instant): List<ActivityEvent> =
        TODO("Phase 1: implement against Microsoft Graph App Folder.")

    override suspend fun resolveArtifact(driveItemId: String): ArtifactDetails? =
        TODO("Phase 1: implement against Microsoft Graph App Folder.")
}
