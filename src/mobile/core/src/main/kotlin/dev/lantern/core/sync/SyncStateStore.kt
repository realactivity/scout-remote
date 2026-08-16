package dev.lantern.core.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant

data class CachedFile(
    val path: String,
    val itemId: String,
    val etag: String,
    val lastModifiedUtc: Instant,
    val content: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is CachedFile &&
            path == other.path &&
            itemId == other.itemId &&
            etag == other.etag &&
            lastModifiedUtc == other.lastModifiedUtc &&
            content.contentEquals(other.content)

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + itemId.hashCode()
        result = 31 * result + etag.hashCode()
        result = 31 * result + lastModifiedUtc.hashCode()
        result = 31 * result + (content?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Durable, content-safe sync index. Android persists metadata in SQLite and
 * payload bytes in OS-protected storage. The pure-JVM harness uses memory.
 */
interface SyncStateStore {
    suspend fun deltaCursor(): String?
    suspend fun setDeltaCursor(cursor: String?)
    suspend fun cachedFile(path: String): CachedFile?
    suspend fun cachedFiles(prefix: String): List<CachedFile>
    suspend fun putCachedFile(file: CachedFile)
    suspend fun removeCachedFile(path: String)
    suspend fun removeCachedFileByItemId(itemId: String)
    suspend fun clearCachedFiles()
    suspend fun lastActivitySince(): Instant?
    suspend fun setLastActivitySince(value: Instant)
    suspend fun watchedCommandIds(): Set<String>
    suspend fun setCommandWatched(commandId: String, watched: Boolean)
}

class InMemorySyncStateStore : SyncStateStore {
    private val mutex = Mutex()
    private var cursor: String? = null
    private var activitySince: Instant? = null
    private val files = linkedMapOf<String, CachedFile>()
    private val watched = linkedSetOf<String>()

    override suspend fun deltaCursor(): String? = mutex.withLock { cursor }

    override suspend fun setDeltaCursor(cursor: String?) {
        mutex.withLock { this.cursor = cursor }
    }

    override suspend fun cachedFile(path: String): CachedFile? = mutex.withLock { files[path] }

    override suspend fun cachedFiles(prefix: String): List<CachedFile> = mutex.withLock {
        if (prefix.isEmpty()) {
            files.values.toList()
        } else {
            files.values.filter { it.path == prefix || it.path.startsWith(prefix.trimEnd('/') + "/") }
        }
    }

    override suspend fun putCachedFile(file: CachedFile) {
        mutex.withLock {
            val existing = files[file.path]
            files[file.path] = if (file.content == null && existing?.etag == file.etag) {
                file.copy(content = existing.content)
            } else {
                file
            }
        }
    }

    override suspend fun removeCachedFile(path: String) {
        mutex.withLock { files.remove(path) }
    }

    override suspend fun removeCachedFileByItemId(itemId: String) {
        mutex.withLock {
            files.entries.removeAll { it.value.itemId == itemId }
        }
    }

    override suspend fun clearCachedFiles() {
        mutex.withLock { files.clear() }
    }

    override suspend fun lastActivitySince(): Instant? = mutex.withLock { activitySince }

    override suspend fun setLastActivitySince(value: Instant) {
        mutex.withLock { activitySince = value }
    }

    override suspend fun watchedCommandIds(): Set<String> = mutex.withLock { watched.toSet() }

    override suspend fun setCommandWatched(commandId: String, watched: Boolean) {
        mutex.withLock {
            if (watched) {
                this.watched += commandId
            } else {
                this.watched -= commandId
            }
        }
    }
}
