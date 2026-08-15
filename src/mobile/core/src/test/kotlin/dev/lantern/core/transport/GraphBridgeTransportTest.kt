package dev.lantern.core.transport

import dev.lantern.core.domain.ActivityCategory
import dev.lantern.core.domain.ActivityEvent
import dev.lantern.core.domain.ProtocolJson
import dev.lantern.core.storage.FilesystemAppFolderStore
import dev.lantern.core.storage.ProtocolPaths
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
}
