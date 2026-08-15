package dev.lantern.core.transport

import dev.lantern.core.domain.ActivityCategory
import dev.lantern.core.domain.ActivityEvent
import dev.lantern.core.domain.ProtocolManifest
import dev.lantern.core.domain.ProtocolJson
import dev.lantern.core.storage.AppFolderStore
import dev.lantern.core.storage.DeltaPage
import dev.lantern.core.storage.DeletedItem
import dev.lantern.core.storage.FilesystemAppFolderStore
import dev.lantern.core.storage.ProtocolPaths
import dev.lantern.core.storage.StoreReadResult
import dev.lantern.core.storage.StoredItem
import dev.lantern.core.sync.InMemorySyncStateStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString

class GraphBridgeTransportTest {

    private val json = ProtocolJson.json

    @Test
    fun `activity read walks day folders and filters by sinceUtc`() = runBlocking {
        val dir = Files.createTempDirectory("lantern-activity-")
        try {
            val store = FilesystemAppFolderStore(dir)
            val older = ActivityEvent(
                schemaVersion = 1,
                eventId = "01KZYMGHG0NPD8AF5A96MP2YFA",
                category = ActivityCategory.WORK,
                occurredUtc = Instant.parse("2026-08-14T12:00:00Z"),
                summary = "older",
            )
            val newer = ActivityEvent(
                schemaVersion = 1,
                eventId = "01KZYMGHG0NPD8AF5A96MP2YFB",
                category = ActivityCategory.WORK,
                occurredUtc = Instant.parse("2026-08-15T03:00:00Z"),
                summary = "newer",
            )
            store.write(
                ProtocolPaths.activityEvent("2026-08-14", older.eventId),
                json.encodeToString(older).encodeToByteArray(),
            )
            store.write(
                ProtocolPaths.activityEvent("2026-08-15", newer.eventId),
                json.encodeToString(newer).encodeToByteArray(),
            )
            val transport = GraphBridgeTransport(store) { Instant.parse("2026-08-15T04:00:00Z") }
            val events = transport.readActivity(Instant.parse("2026-08-15T00:00:00Z"))
            assertEquals(listOf(newer.eventId), events.map { it.eventId })
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `daysInclusive covers both endpoints`() {
        assertEquals(
            listOf("2026-08-14", "2026-08-15"),
            GraphBridgeTransport.daysInclusive("2026-08-14", "2026-08-15"),
        )
        assertEquals(emptyList(), GraphBridgeTransport.daysInclusive("2026-08-16", "2026-08-15"))
    }

    @Test
    fun `second protocol read uses item id and cached content on not modified`() = runBlocking {
        val protocol = ProtocolManifest(
            protocol = "lantern",
            version = "1.0",
            agentId = "primary",
            bridgeVersion = "0.2.1",
            capabilities = emptyList(),
        )
        val content = json.encodeToString(protocol).encodeToByteArray()
        val store = ConditionalStore(
            StoredItem(
                path = ProtocolPaths.PROTOCOL,
                etag = "etag-1",
                itemId = "item-protocol",
                lastModifiedUtc = Instant.parse("2026-08-15T05:00:00Z"),
                content = content,
            ),
        )
        val state = InMemorySyncStateStore()
        val transport = GraphBridgeTransport(store, syncState = state)

        assertEquals("lantern", transport.readProtocol()?.protocol)
        assertEquals("lantern", transport.readProtocol()?.protocol)

        assertEquals(1, store.pathReads)
        assertEquals(1, store.itemIdReads)
        assertEquals("etag-1", store.lastKnownEtag)
    }

    @Test
    fun `delta cursor and nested metadata survive transport recreation`() = runBlocking {
        val state = InMemorySyncStateStore()
        val metadata = StoredItem(
            path = "agents/primary/status.json",
            etag = "etag-status",
            itemId = "item-status",
            lastModifiedUtc = Instant.parse("2026-08-15T05:00:00Z"),
        )
        val firstStore = ConditionalStore(metadata, DeltaPage(listOf(metadata), "delta-1"))
        GraphBridgeTransport(firstStore, syncState = state).applyDelta()

        val secondStore = ConditionalStore(metadata, DeltaPage(emptyList(), "delta-2"))
        GraphBridgeTransport(secondStore, syncState = state).applyDelta()

        assertEquals("delta-1", secondStore.requestedCursor)
        assertEquals("delta-2", state.deltaCursor())
        assertEquals("item-status", state.cachedFile(metadata.path)?.itemId)
    }

    @Test
    fun `delta tombstone without path evicts cache by item id`() = runBlocking {
        val now = Instant.parse("2026-08-15T05:00:00Z")
        val cached = StoredItem("responses/old.json", "etag-1", "item-old", now, "{}".encodeToByteArray())
        val state = InMemorySyncStateStore()
        state.putCachedFile(
            dev.lantern.core.sync.CachedFile(
                cached.path,
                cached.itemId,
                cached.etag,
                cached.lastModifiedUtc,
                cached.content,
            ),
        )
        val store = ConditionalStore(
            cached,
            DeltaPage(
                items = emptyList(),
                cursor = "delta-2",
                deletions = listOf(DeletedItem("item-old", path = null)),
            ),
        )

        GraphBridgeTransport(store, syncState = state).applyDelta()

        assertEquals(null, state.cachedFile(cached.path))
    }

    @Test
    fun `complete snapshot removes files absent from filesystem delta`() = runBlocking {
        val now = Instant.parse("2026-08-15T05:00:00Z")
        val retained = StoredItem("protocol.json", "etag-1", "protocol.json", now)
        val state = InMemorySyncStateStore()
        state.putCachedFile(
            dev.lantern.core.sync.CachedFile(
                "activity/2026-08-14/old.json",
                "old",
                "etag-old",
                now,
                "{}".encodeToByteArray(),
            ),
        )
        val store = ConditionalStore(
            retained,
            DeltaPage(listOf(retained), "snapshot-2", completeSnapshot = true),
        )

        GraphBridgeTransport(store, syncState = state).applyDelta()

        assertEquals(null, state.cachedFile("activity/2026-08-14/old.json"))
        assertEquals("protocol.json", state.cachedFile("protocol.json")?.itemId)
    }

    @Test
    fun `immutable activity cache avoids list and read calls`() = runBlocking {
        val occurred = Instant.parse("2026-08-15T03:00:00Z")
        val event = ActivityEvent(
            schemaVersion = 1,
            eventId = "01KZYMGHG0NPD8AF5A96MP2YFB",
            category = ActivityCategory.WORK,
            occurredUtc = occurred,
            summary = "cached",
        )
        val item = StoredItem(
            path = ProtocolPaths.activityEvent("2026-08-15", event.eventId),
            etag = "etag-activity",
            itemId = "item-activity",
            lastModifiedUtc = occurred,
            content = json.encodeToString(event).encodeToByteArray(),
        )
        val state = InMemorySyncStateStore()
        state.putCachedFile(
            dev.lantern.core.sync.CachedFile(
                item.path,
                item.itemId,
                item.etag,
                item.lastModifiedUtc,
                item.content,
            ),
        )
        state.setDeltaCursor("delta-ready")
        val store = ConditionalStore(item)
        val transport = GraphBridgeTransport(
            store = store,
            syncState = state,
            clock = { Instant.parse("2026-08-15T04:00:00Z") },
        )

        val activity = transport.readActivity(Instant.parse("2026-08-15T00:00:00Z"))

        assertEquals(listOf(event.eventId), activity.map { it.eventId })
        assertEquals(0, store.pathReads)
        assertEquals(0, store.itemIdReads)
        assertEquals(0, store.listCalls)
    }

    private class ConditionalStore(
        private val item: StoredItem,
        private val delta: DeltaPage = DeltaPage(emptyList(), "delta"),
    ) : AppFolderStore {
        var pathReads = 0
        var itemIdReads = 0
        var lastKnownEtag: String? = null
        var requestedCursor: String? = null
        var listCalls = 0

        override suspend fun get(path: String): StoredItem? = item

        override suspend fun read(path: String, knownEtag: String?): StoreReadResult {
            pathReads++
            return StoreReadResult.Found(item)
        }

        override suspend fun readByItemId(itemId: String, knownEtag: String?): StoreReadResult {
            itemIdReads++
            lastKnownEtag = knownEtag
            return if (knownEtag == item.etag) {
                StoreReadResult.NotModified
            } else {
                StoreReadResult.Found(item)
            }
        }

        override suspend fun list(prefix: String): List<StoredItem> {
            listCalls++
            return listOf(item)
        }
        override suspend fun write(path: String, content: ByteArray): StoredItem = error("unused")
        override suspend fun createIfAbsent(path: String, content: ByteArray): StoredItem? = error("unused")
        override suspend fun updateIfMatch(path: String, content: ByteArray, etag: String): StoredItem = error("unused")
        override suspend fun delete(path: String) = error("unused")

        override suspend fun listDelta(cursor: String?): DeltaPage {
            requestedCursor = cursor
            return delta
        }
    }
}
