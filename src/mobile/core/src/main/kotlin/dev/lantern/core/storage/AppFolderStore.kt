package dev.lantern.core.storage

/**
 * Port for the OneDrive App Folder mailbox (spec §6, §16.2). The Graph backend
 * and the local filesystem harness share this surface so offline tests and the
 * real tenant path use one protocol engine.
 */
interface AppFolderStore {
    suspend fun get(path: String): StoredItem?

    suspend fun read(path: String, knownEtag: String? = null): StoreReadResult {
        val item = get(path) ?: return StoreReadResult.Missing
        return if (knownEtag != null && item.etag == knownEtag) {
            StoreReadResult.NotModified
        } else {
            StoreReadResult.Found(item)
        }
    }

    suspend fun readByItemId(itemId: String, knownEtag: String? = null): StoreReadResult {
        val metadata = list("").firstOrNull { it.itemId == itemId }
            ?: return StoreReadResult.Missing
        if (knownEtag != null && metadata.etag == knownEtag) {
            return StoreReadResult.NotModified
        }
        return read(metadata.path, knownEtag = null)
    }

    suspend fun list(prefix: String): List<StoredItem>

    suspend fun write(path: String, content: ByteArray): StoredItem

    /**
     * Create only if the path is absent. Returns null when the file already
     * exists (create-if-absent enqueue keyed on commandId, spec §16.2).
     */
    suspend fun createIfAbsent(path: String, content: ByteArray): StoredItem?

    suspend fun updateIfMatch(path: String, content: ByteArray, etag: String): StoredItem

    suspend fun delete(path: String)

    suspend fun listDelta(cursor: String?): DeltaPage
}

sealed interface StoreReadResult {
    data class Found(val item: StoredItem) : StoreReadResult
    data object NotModified : StoreReadResult
    data object Missing : StoreReadResult
}

data class StoredItem(
    val path: String,
    val etag: String,
    val itemId: String,
    val lastModifiedUtc: kotlinx.datetime.Instant,
    val content: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoredItem) return false
        return path == other.path &&
            etag == other.etag &&
            itemId == other.itemId &&
            lastModifiedUtc == other.lastModifiedUtc &&
            content.contentEquals(other.content)
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + etag.hashCode()
        result = 31 * result + itemId.hashCode()
        result = 31 * result + lastModifiedUtc.hashCode()
        result = 31 * result + (content?.contentHashCode() ?: 0)
        return result
    }
}

data class DeltaPage(
    val items: List<StoredItem>,
    val cursor: String?,
    val deletions: List<DeletedItem> = emptyList(),
    val reset: Boolean = false,
    val completeSnapshot: Boolean = false,
)

data class DeletedItem(
    val itemId: String,
    val path: String?,
)

class ETagConflictException(
    val itemPath: String,
    val expected: String,
    val actual: String,
) : RuntimeException("ETag mismatch for $itemPath: expected $expected, actual $actual.")
