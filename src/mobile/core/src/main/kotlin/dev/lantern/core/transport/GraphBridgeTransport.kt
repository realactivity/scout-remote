package dev.lantern.core.transport

import dev.lantern.core.application.BridgeTransport
import dev.lantern.core.domain.ActivityEvent
import dev.lantern.core.domain.AgentStatus
import dev.lantern.core.domain.ArtifactDetails
import dev.lantern.core.domain.ConversationEvent
import dev.lantern.core.domain.ProtocolJson
import dev.lantern.core.domain.ProtocolManifest
import dev.lantern.core.domain.RemoteCommand
import dev.lantern.core.domain.RemoteResponse
import dev.lantern.core.storage.AppFolderStore
import dev.lantern.core.storage.GraphAppFolderStore
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString

/**
 * [BridgeTransport] over an [AppFolderStore] (Graph or filesystem harness).
 *
 * - ETags/If-Match for mutable files: status and in-flight responses (spec §15.1).
 * - Enqueue uses create-if-absent keyed on commandId so retries cannot duplicate.
 * - Protocol handshake is read before other files when present.
 */
class GraphBridgeTransport(
    private val store: AppFolderStore,
) : BridgeTransport {

    private val json = ProtocolJson.json

    override suspend fun readProtocol(): ProtocolManifest? {
        val item = store.get(PROTOCOL) ?: return null
        val text = item.content?.decodeToString() ?: return null
        return json.decodeFromString<ProtocolManifest>(text)
    }

    override suspend fun readStatus(agentId: String): AgentStatus? {
        val item = store.get("agents/$agentId/status.json") ?: return null
        val text = item.content?.decodeToString() ?: return null
        return json.decodeFromString<AgentStatus>(text)
    }

    override suspend fun enqueueCommand(command: RemoteCommand) {
        val path = "commands/inbox/${command.commandId}.json"
        val bytes = json.encodeToString(command).encodeToByteArray()
        store.createIfAbsent(path, bytes)
    }

    override suspend fun readResponse(commandId: String): RemoteResponse? {
        val item = store.get("responses/$commandId.json") ?: return null
        val text = item.content?.decodeToString() ?: return null
        return json.decodeFromString<RemoteResponse>(text)
    }

    override suspend fun readConversationEvents(conversationId: String): List<ConversationEvent> {
        val listed = store.list("conversations/$conversationId")
        return listed
            .filter { it.path.endsWith(".json") }
            .mapNotNull { item ->
                val file = store.get(item.path) ?: return@mapNotNull null
                val text = file.content?.decodeToString() ?: return@mapNotNull null
                runCatching { json.decodeFromString<ConversationEvent>(text) }.getOrNull()
            }
            .sortedBy { it.eventId }
    }

    override suspend fun readActivity(sinceUtc: Instant): List<ActivityEvent> {
        val listed = store.list("activity")
        return listed
            .filter { it.path.endsWith(".json") }
            .mapNotNull { item ->
                val file = store.get(item.path) ?: return@mapNotNull null
                val text = file.content?.decodeToString() ?: return@mapNotNull null
                runCatching { json.decodeFromString<ActivityEvent>(text) }.getOrNull()
            }
            .filter { it.occurredUtc >= sinceUtc }
            .sortedBy { it.eventId }
    }

    override suspend fun resolveArtifact(driveItemId: String): ArtifactDetails? {
        val graph = store as? GraphAppFolderStore
        if (graph != null) {
            val resolved = graph.resolveByItemId(driveItemId) ?: return null
            return ArtifactDetails(
                driveItemId = resolved.driveItemId,
                name = resolved.name,
                sizeBytes = resolved.sizeBytes,
                createdUtc = resolved.createdUtc,
                webUrl = resolved.webUrl,
            )
        }
        val listed = store.list("")
        val match = listed.firstOrNull { it.itemId == driveItemId || it.path == driveItemId }
            ?: return null
        return ArtifactDetails(
            driveItemId = match.itemId,
            name = match.path.substringAfterLast('/'),
            createdUtc = match.lastModifiedUtc,
        )
    }

    companion object {
        const val PROTOCOL: String = "protocol.json"
    }
}
