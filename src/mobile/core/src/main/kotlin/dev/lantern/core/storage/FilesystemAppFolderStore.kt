package dev.lantern.core.storage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * Local-directory App Folder. Layout matches contracts/README.md. ETags are
 * SHA-256 of file bytes. Used by every offline test and the cross-language
 * smoke harness.
 */
class FilesystemAppFolderStore(root: Path) : AppFolderStore {
    val root: Path = root.toAbsolutePath().normalize()
    private val mutex = Mutex()

    init {
        Files.createDirectories(this.root)
    }

    override suspend fun get(path: String): StoredItem? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val full = map(path)
            if (!full.exists() || !full.isRegularFile()) {
                null
            } else {
                toItem(path, full, Files.readAllBytes(full))
            }
        }
    }

    override suspend fun read(path: String, knownEtag: String?): StoreReadResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val full = map(path)
            if (!full.exists() || !full.isRegularFile()) {
                StoreReadResult.Missing
            } else {
                val bytes = Files.readAllBytes(full)
                val etag = etagOf(bytes)
                if (knownEtag != null && etag == knownEtag) {
                    StoreReadResult.NotModified
                } else {
                    StoreReadResult.Found(toItem(path, full, bytes))
                }
            }
        }
    }

    override suspend fun readByItemId(itemId: String, knownEtag: String?): StoreReadResult =
        read(itemId, knownEtag)

    override suspend fun list(prefix: String): List<StoredItem> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val prefixNorm = normalize(prefix)
            enumerateFiles().mapNotNull { file ->
                val rel = relative(file)
                if (prefixNorm.isEmpty() ||
                    rel == prefixNorm ||
                    rel.startsWith(prefixNorm.trimEnd('/') + "/")
                ) {
                    toItem(rel, file, content = null)
                } else {
                    null
                }
            }
        }
    }

    override suspend fun write(path: String, content: ByteArray): StoredItem = mutex.withLock {
        withContext(Dispatchers.IO) {
            val full = map(path)
            Files.createDirectories(full.parent)
            Files.write(full, content)
            toItem(path, full, content)
        }
    }

    override suspend fun createIfAbsent(path: String, content: ByteArray): StoredItem? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val full = map(path)
            Files.createDirectories(full.parent)
            try {
                Files.write(full, content, StandardOpenOption.CREATE_NEW)
                toItem(path, full, content)
            } catch (_: java.nio.file.FileAlreadyExistsException) {
                null
            }
        }
    }

    override suspend fun updateIfMatch(path: String, content: ByteArray, etag: String): StoredItem =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val full = map(path)
                if (!full.exists()) {
                    throw java.nio.file.NoSuchFileException(full.toString())
                }
                val current = etagOf(full.readBytes())
                if (current != etag) {
                    throw ETagConflictException(path, etag, current)
                }
                Files.write(full, content)
                toItem(path, full, content)
            }
        }

    override suspend fun delete(path: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                Files.deleteIfExists(map(path))
            }
        }
    }

    override suspend fun listDelta(cursor: String?): DeltaPage = mutex.withLock {
        withContext(Dispatchers.IO) {
            val since = cursor?.toLongOrNull() ?: 0L
            var max = since
            val items = mutableListOf<StoredItem>()
            for (file in enumerateFiles()) {
                val ticks = file.getLastModifiedTime().toMillis()
                items += toItem(relative(file), file, content = null)
                if (ticks > max) {
                    max = ticks
                }
            }
            DeltaPage(items, max.toString(), completeSnapshot = true)
        }
    }

    private fun enumerateFiles(): List<Path> {
        if (!Files.isDirectory(root)) {
            return emptyList()
        }
        return Files.walk(root).use { stream ->
            stream.filter { it.isRegularFile() }.toList()
        }
    }

    private fun map(path: String): Path {
        val full = root.resolve(normalize(path)).normalize()
        if (!full.startsWith(root)) {
            throw IllegalArgumentException("Path escapes App Folder root: $path")
        }
        return full
    }

    private fun relative(full: Path): String = normalize(full.relativeTo(root).toString())

    private fun toItem(path: String, full: Path, content: ByteArray?): StoredItem {
        val bytes = content ?: full.readBytes()
        return StoredItem(
            path = normalize(path),
            etag = etagOf(bytes),
            itemId = normalize(path),
            lastModifiedUtc = Instant.fromEpochMilliseconds(full.getLastModifiedTime().toMillis()),
            content = content,
        )
    }

    companion object {
        fun normalize(path: String): String = path.replace('\\', '/').trimStart('/')

        fun etagOf(content: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(content)
            return digest.joinToString("") { b -> "%02x".format(b) }
        }
    }
}
