package dev.lantern.core.application

import dev.lantern.core.domain.ProtocolJson
import dev.lantern.core.domain.RemoteCommand
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

/**
 * Durable local command queue. Drafts survive offline (spec §5, §15).
 */
interface LocalCommandQueue {
    suspend fun put(command: RemoteCommand)
    suspend fun get(commandId: String): RemoteCommand?
    suspend fun all(): List<RemoteCommand>
}

class InMemoryCommandQueue : LocalCommandQueue {
    private val mutex = Mutex()
    private val items = LinkedHashMap<String, RemoteCommand>()

    override suspend fun put(command: RemoteCommand) {
        mutex.withLock { items[command.commandId] = command }
    }

    override suspend fun get(commandId: String): RemoteCommand? =
        mutex.withLock { items[commandId] }

    override suspend fun all(): List<RemoteCommand> =
        mutex.withLock { items.values.toList() }
}

class FileCommandQueue(private val directory: Path) : LocalCommandQueue {
    private val mutex = Mutex()
    private val json = ProtocolJson.json

    init {
        Files.createDirectories(directory)
    }

    override suspend fun put(command: RemoteCommand) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val path = directory.resolve("${command.commandId}.json")
                writeAtomically(path, json.encodeToString(command))
            }
        }
    }

    override suspend fun get(commandId: String): RemoteCommand? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val path = directory.resolve("$commandId.json")
            if (!Files.isRegularFile(path)) {
                null
            } else {
                json.decodeFromString<RemoteCommand>(Files.readString(path))
            }
        }
    }

    override suspend fun all(): List<RemoteCommand> = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!Files.isDirectory(directory)) {
                return@withContext emptyList()
            }
            Files.list(directory).use { stream ->
                stream.filter { it.fileName.toString().endsWith(".json") }
                    .sorted()
                    .map { json.decodeFromString<RemoteCommand>(Files.readString(it)) }
                    .toList()
            }
        }
    }

    private fun writeAtomically(path: Path, content: String) {
        val temp = directory.resolve("${path.fileName}.${System.nanoTime()}.tmp")
        try {
            Files.writeString(temp, content)
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
