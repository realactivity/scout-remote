package dev.lantern.core.application

import dev.lantern.core.domain.ProtocolJson
import dev.lantern.core.domain.RemoteCommand
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
            val path = directory.resolve("${command.commandId}.json")
            Files.writeString(path, json.encodeToString(command))
        }
    }

    override suspend fun get(commandId: String): RemoteCommand? = mutex.withLock {
        val path = directory.resolve("$commandId.json")
        if (!Files.isRegularFile(path)) {
            null
        } else {
            json.decodeFromString<RemoteCommand>(Files.readString(path))
        }
    }

    override suspend fun all(): List<RemoteCommand> = mutex.withLock {
        if (!Files.isDirectory(directory)) {
            return@withLock emptyList()
        }
        Files.list(directory).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".json") }
                .map { json.decodeFromString<RemoteCommand>(Files.readString(it)) }
                .toList()
        }
    }
}
