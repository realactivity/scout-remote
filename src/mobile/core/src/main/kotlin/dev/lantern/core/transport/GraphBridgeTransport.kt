package dev.lantern.core.transport

import dev.lantern.core.application.BridgeTransport
import dev.lantern.core.application.DeltaApplyResult
import dev.lantern.core.application.IncrementalBridgeTransport
import dev.lantern.core.application.StatefulBridgeTransport
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
import dev.lantern.core.storage.StoreReadResult
import dev.lantern.core.sync.CachedFile
import dev.lantern.core.sync.InMemorySyncStateStore
import dev.lantern.core.sync.SyncStateStore
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
    override val syncState: SyncStateStore = InMemorySyncStateStore(),
    private val clock: () -> Instant = { Clock.System.now() },
) : BridgeTransport, IncrementalBridgeTransport, StatefulBridgeTransport {

    private val json = ProtocolJson.json

    override suspend fun readProtocol(): ProtocolManifest? {
        return readJson(ProtocolPaths.PROTOCOL)
    }

    override suspend fun readStatus(agentId: String): AgentStatus? {
        return readJson(ProtocolPaths.status(agentId))
    }

    override suspend fun enqueueCommand(command: RemoteCommand) {
        val bytes = json.encodeToString(command).encodeToByteArray()
        store.createIfAbsent(ProtocolPaths.inboxCommand(command.commandId), bytes)
    }

    override suspend fun readResponse(commandId: String): RemoteResponse? {
        return readJson(ProtocolPaths.response(commandId))
    }

    override suspend fun readConversationEvents(conversationId: String): List<ConversationEvent> {
        val prefix = ProtocolPaths.conversationFolder(conversationId)
        return cachedOrListed(prefix)
            .filter { it.path.endsWith(".json") }
            .mapNotNull { item -> readImmutableJson<ConversationEvent>(item) }
            .sortedBy { it.eventId }
    }

    override suspend fun readActivity(sinceUtc: Instant): List<ActivityEvent> {
        val now = clock()
        val effectiveSince = maxOf(sinceUtc, now - 30.days)
        val start = ProtocolPaths.activityDay(effectiveSince)
        val end = ProtocolPaths.activityDay(now)
        val cursorPresent = syncState.deltaCursor() != null
        val cachedActivity = syncState.cachedFiles(ProtocolPaths.ACTIVITY)
        // #region agent log
        java.io.File("/opt/cursor/logs/debug.log").appendText("""{"hypothesisId":"A,B","location":"GraphBridgeTransport.kt:readActivity-entry","message":"activity cache before selection","data":{"cursorPresent":$cursorPresent,"cachedCount":${cachedActivity.size}},"timestamp":${System.currentTimeMillis()}}""" + "\n")
        // #endregion
        var candidates = cachedActivity
            .filter { it.lastModifiedUtc >= effectiveSince }
        val fallbackToListing = candidates.isEmpty() && !cursorPresent
        // #region agent log
        java.io.File("/opt/cursor/logs/debug.log").appendText("""{"hypothesisId":"A,B","location":"GraphBridgeTransport.kt:readActivity-branch","message":"activity candidate branch","data":{"filteredCount":${candidates.size},"fallbackToListing":$fallbackToListing},"timestamp":${System.currentTimeMillis()}}""" + "\n")
        // #endregion
        if (fallbackToListing) {
            candidates = daysInclusive(start, end)
                .flatMap { day -> listedAsCache(ProtocolPaths.activityFolder(day)) }
        }
        val decoded = candidates
            .filter { it.path.endsWith(".json") }
            .mapNotNull { item -> readImmutableJson<ActivityEvent>(item) }
            .filter { it.occurredUtc >= effectiveSince }
            .sortedBy { it.eventId }
        // #region agent log
        java.io.File("/opt/cursor/logs/debug.log").appendText("""{"hypothesisId":"B,D","location":"GraphBridgeTransport.kt:readActivity-exit","message":"activity decode result","data":{"candidateCount":${candidates.size},"decodedCount":${decoded.size}},"timestamp":${System.currentTimeMillis()}}""" + "\n")
        // #endregion
        return decoded
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

    override suspend fun applyDelta(): DeltaApplyResult {
        val existingCursor = syncState.deltaCursor()
        // #region agent log
        java.io.File("/opt/cursor/logs/debug.log").appendText("""{"hypothesisId":"A","location":"GraphBridgeTransport.kt:applyDelta-entry","message":"delta request","data":{"cursorPresent":${existingCursor != null}},"timestamp":${System.currentTimeMillis()}}""" + "\n")
        // #endregion
        val page = store.listDelta(existingCursor)
        // #region agent log
        java.io.File("/opt/cursor/logs/debug.log").appendText("""{"hypothesisId":"A,C","location":"GraphBridgeTransport.kt:applyDelta-page","message":"delta page received","data":{"itemCount":${page.items.size},"activityItemCount":${page.items.count { it.path.startsWith(ProtocolPaths.ACTIVITY + "/") }},"deletionCount":${page.deletions.size},"reset":${page.reset},"completeSnapshot":${page.completeSnapshot}},"timestamp":${System.currentTimeMillis()}}""" + "\n")
        // #endregion
        if (page.reset) {
            syncState.clearCachedFiles()
        }
        if (page.completeSnapshot) {
            val currentPaths = page.items.map { it.path }.toSet()
            syncState.cachedFiles("")
                .filterNot { it.path in currentPaths }
                .forEach { syncState.removeCachedFile(it.path) }
        }
        for (deleted in page.deletions) {
            syncState.removeCachedFileByItemId(deleted.itemId)
            deleted.path?.let { syncState.removeCachedFile(it) }
        }
        for (item in page.items) {
            syncState.putCachedFile(item.toCache())
        }
        syncState.setDeltaCursor(page.cursor)
        // #region agent log
        java.io.File("/opt/cursor/logs/debug.log").appendText("""{"hypothesisId":"A","location":"GraphBridgeTransport.kt:applyDelta-exit","message":"delta cache updated","data":{"cachedCount":${syncState.cachedFiles("").size},"activityCachedCount":${syncState.cachedFiles(ProtocolPaths.ACTIVITY).size},"cursorPresent":${page.cursor != null}},"timestamp":${System.currentTimeMillis()}}""" + "\n")
        // #endregion
        return DeltaApplyResult(page.items.size, page.deletions.size, page.reset)
    }

    private suspend fun cachedOrListed(prefix: String): List<CachedFile> {
        val cached = syncState.cachedFiles(prefix)
        return if (syncState.deltaCursor() != null) cached else cached.ifEmpty { listedAsCache(prefix) }
    }

    private suspend fun listedAsCache(prefix: String): List<CachedFile> {
        val listed = store.list(prefix).map { it.toCache() }
        listed.forEach { syncState.putCachedFile(it) }
        return listed
    }

    private suspend inline fun <reified T> readJson(path: String): T? {
        val cached = syncState.cachedFile(path)
        val knownEtag = cached?.etag?.takeIf { cached.content != null }
        val result = if (cached?.itemId != null) {
            store.readByItemId(cached.itemId, knownEtag)
        } else {
            store.read(path, knownEtag)
        }
        val file = when (result) {
            is StoreReadResult.Found -> {
                val normalized = result.item.copy(path = path)
                syncState.putCachedFile(normalized.toCache())
                normalized
            }
            StoreReadResult.NotModified -> cached?.toStoredItem()
            StoreReadResult.Missing -> {
                syncState.removeCachedFile(path)
                null
            }
        } ?: return null
        val text = file.content?.decodeToString() ?: return null
        return runCatching { json.decodeFromString<T>(text) }.getOrNull()
    }

    private suspend inline fun <reified T> readImmutableJson(file: CachedFile): T? {
        val content = file.content
        if (content != null) {
            return runCatching { json.decodeFromString<T>(content.decodeToString()) }.getOrNull()
        }
        return readJson(file.path)
    }

    private fun dev.lantern.core.storage.StoredItem.toCache(): CachedFile =
        CachedFile(path, itemId, etag, lastModifiedUtc, content)

    private fun CachedFile.toStoredItem(): dev.lantern.core.storage.StoredItem =
        dev.lantern.core.storage.StoredItem(path, etag, itemId, lastModifiedUtc, content)

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
