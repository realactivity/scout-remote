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
import dev.lantern.core.storage.ProtocolPaths
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.encodeToString
import kotlin.time.Duration.Companion.days

/**
 * [BridgeTransport] over an [AppFolderStore] (Graph or filesystem harness).
 *
 * - ETags/If-Match for mutable files: status and in-flight responses (spec §15.1).
 * - Enqueue uses create-if-absent keyed on commandId so retries cannot duplicate.
 * - Protocol handshake is read before other files when present.
 * - Activity reads walk `activity/{yyyy-MM-dd}/` day folders (contracts/README.md).
 */
class GraphBridgeTransport(
    private val store: AppFolderStore,
    private val clock: () -> Instant = { Clock.System.now() },
) : BridgeTransport {

    private val json = ProtocolJson.json

    override suspend fun readProtocol(): ProtocolManifest? {
        val item = store.get(ProtocolPaths.PROTOCOL) ?: return null
        val text = item.content?.decodeToString() ?: return null
        return json.decodeFromString<ProtocolManifest>(text)
    }

    override suspend fun readStatus(agentId: String): AgentStatus? {
        val item = store.get(ProtocolPaths.status(agentId)) ?: return null
        val text = item.content?.decodeToString() ?: return null
        return json.decodeFromString<AgentStatus>(text)
    }

    override suspend fun enqueueCommand(command: RemoteCommand) {
        val bytes = json.encodeToString(command).encodeToByteArray()
        store.createIfAbsent(ProtocolPaths.inboxCommand(command.commandId), bytes)
    }

    override suspend fun readResponse(commandId: String): RemoteResponse? {
        val item = store.get(ProtocolPaths.response(commandId)) ?: return null
        val text = item.content?.decodeToString() ?: return null
        return json.decodeFromString<RemoteResponse>(text)
    }

    override suspend fun readConversationEvents(conversationId: String): List<ConversationEvent> {
        val listed = store.list(ProtocolPaths.conversationFolder(conversationId))
        return listed
            .filter { it.path.endsWith(".json") }
            .mapNotNull { item -> decodeJson<ConversationEvent>(item.path) }
            .sortedBy { it.eventId }
    }

    override suspend fun readActivity(sinceUtc: Instant): List<ActivityEvent> {
        val now = clock()
        val effectiveSince = maxOf(sinceUtc, now - 30.days)
        val start = ProtocolPaths.activityDay(effectiveSince)
        val end = ProtocolPaths.activityDay(now)
        return daysInclusive(start, end)
            .flatMap { day -> store.list(ProtocolPaths.activityFolder(day)) }
            .filter { it.path.endsWith(".json") }
            .mapNotNull { item -> decodeJson<ActivityEvent>(item.path) }
            .filter { it.occurredUtc >= effectiveSince }
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

    private suspend inline fun <reified T> decodeJson(path: String): T? {
        val file = store.get(path) ?: return null
        val text = file.content?.decodeToString() ?: return null
        return runCatching { json.decodeFromString<T>(text) }.getOrNull()
    }

    companion object {
        const val PROTOCOL: String = ProtocolPaths.PROTOCOL

        internal fun daysInclusive(startDay: String, endDay: String): List<String> {
            val start = LocalDate.parse(startDay)
            val end = LocalDate.parse(endDay)
            if (end < start) {
                return emptyList()
            }
            val days = mutableListOf<String>()
            var current = start
            while (current <= end) {
                days += current.toString()
                current = current.plus(1, DateTimeUnit.DAY)
            }
            return days
        }
    }
}
