package dev.lantern.app.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.lantern.core.platform.SecureStorage
import dev.lantern.core.sync.CachedFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SqliteSyncStateStoreTest {
    private lateinit var context: Context
    private lateinit var secureStorage: FakeSecureStorage

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("lantern_sync.db")
        secureStorage = FakeSecureStorage()
    }

    @AfterTest
    fun tearDown() {
        context.deleteDatabase("lantern_sync.db")
    }

    @Test
    fun `state survives store recreation while payload remains protected`() = runBlocking {
        val now = Instant.parse("2026-08-15T05:00:00Z")
        val first = SqliteSyncStateStore(context, secureStorage)
        first.setDeltaCursor("delta-token")
        first.setLastActivitySince(now)
        first.setCommandWatched("01KZYMGHG0NPD8AF5A96MP2YFW", true)
        first.putCachedFile(
            CachedFile(
                path = "agents/primary/status.json",
                itemId = "item-1",
                etag = "etag-1",
                lastModifiedUtc = now,
                content = "secret-body".encodeToByteArray(),
            ),
        )
        first.close()

        val second = SqliteSyncStateStore(context, secureStorage)
        val cached = second.cachedFile("agents/primary/status.json")

        assertEquals("delta-token", second.deltaCursor())
        assertEquals(now, second.lastActivitySince())
        assertEquals(setOf("01KZYMGHG0NPD8AF5A96MP2YFW"), second.watchedCommandIds())
        assertContentEquals("secret-body".encodeToByteArray(), cached?.content)
        assertTrue(secureStorage.values.isNotEmpty())
        val databaseBytes = context.getDatabasePath("lantern_sync.db").readBytes()
        assertFalse(databaseBytes.toString(Charsets.ISO_8859_1).contains("secret-body"))
        second.close()
    }

    @Test
    fun `etag change invalidates stale protected payload`() = runBlocking {
        val now = Instant.parse("2026-08-15T05:00:00Z")
        val store = SqliteSyncStateStore(context, secureStorage)
        store.putCachedFile(CachedFile("protocol.json", "item-1", "etag-1", now, "{}".encodeToByteArray()))
        store.putCachedFile(CachedFile("protocol.json", "item-1", "etag-2", now, content = null))

        assertNull(store.cachedFile("protocol.json")?.content)
        store.close()
    }

    @Test
    fun `item id tombstone removes metadata and protected payload`() = runBlocking {
        val now = Instant.parse("2026-08-15T05:00:00Z")
        val store = SqliteSyncStateStore(context, secureStorage)
        store.putCachedFile(
            CachedFile(
                "responses/old.json",
                "item-old",
                "etag-old",
                now,
                "payload".encodeToByteArray(),
            ),
        )

        store.removeCachedFileByItemId("item-old")

        assertNull(store.cachedFile("responses/old.json"))
        assertTrue(secureStorage.values.isEmpty())
        store.close()
    }

    private class FakeSecureStorage : SecureStorage {
        val values = mutableMapOf<String, ByteArray>()

        override suspend fun get(key: String): ByteArray? = values[key]?.copyOf()

        override suspend fun set(key: String, value: ByteArray) {
            values[key] = value.copyOf()
        }

        override suspend fun remove(key: String) {
            values.remove(key)
        }
    }
}
