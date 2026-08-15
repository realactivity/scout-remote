package dev.lantern.core.storage

import java.nio.file.Files
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class FilesystemAppFolderStoreTest {

    @Test
    fun `create-if-absent does not overwrite`() = runBlocking {
        val store = FilesystemAppFolderStore(Files.createTempDirectory("lantern-store"))
        val first = store.createIfAbsent("commands/inbox/a.json", "one".toByteArray())
        val second = store.createIfAbsent("commands/inbox/a.json", "two".toByteArray())
        assertTrue(first != null)
        assertNull(second)
        assertEquals("one", store.get("commands/inbox/a.json")!!.content!!.decodeToString())
    }

    @Test
    fun `etag update rejects a stale writer`() = runBlocking {
        val store = FilesystemAppFolderStore(Files.createTempDirectory("lantern-store"))
        val written = store.write("agents/primary/status.json", "v1".toByteArray())
        store.updateIfMatch("agents/primary/status.json", "v2".toByteArray(), written.etag)
        assertFailsWith<ETagConflictException> {
            store.updateIfMatch("agents/primary/status.json", "v3".toByteArray(), written.etag)
        }
    }

    @Test
    fun `path escape is refused`() = runBlocking {
        val store = FilesystemAppFolderStore(Files.createTempDirectory("lantern-store"))
        assertFailsWith<IllegalArgumentException> { store.get("../secret.json") }
    }

    @Test
    fun `Graph retry delay accepts seconds and HTTP date`() {
        val now = java.time.Instant.parse("2026-08-15T03:00:00Z")
        val retryAt = DateTimeFormatter.RFC_1123_DATE_TIME.format(now.plusSeconds(10).atZone(ZoneOffset.UTC))
        assertEquals(5_000, GraphAppFolderStore.retryDelayMillis("5", now))
        assertEquals(10_000, GraphAppFolderStore.retryDelayMillis(retryAt, now))
    }
}
