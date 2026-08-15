package dev.lantern.core

import dev.lantern.core.application.CommandServiceImpl
import dev.lantern.core.application.BridgeTransport
import dev.lantern.core.application.InMemoryCommandQueue
import dev.lantern.core.application.InstantClock
import dev.lantern.core.domain.ActivityEvent
import dev.lantern.core.domain.AgentStatus
import dev.lantern.core.domain.ArtifactDetails
import dev.lantern.core.domain.CommandRequester
import dev.lantern.core.domain.CommandType
import dev.lantern.core.domain.ConversationEvent
import dev.lantern.core.domain.ProtocolManifest
import dev.lantern.core.domain.RemoteCommand
import dev.lantern.core.domain.RemoteResponse
import dev.lantern.core.storage.FilesystemAppFolderStore
import dev.lantern.core.transport.GraphBridgeTransport
import dev.lantern.core.ulid.Ulid
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant

class CommandServiceTest {

    private val now = Instant.parse("2026-08-15T03:00:00Z")

    @Test
    fun `send writes a unique ULID command with configured expiry`() = runBlocking {
        val dir = Files.createTempDirectory("lantern-core-")
        try {
            val store = FilesystemAppFolderStore(dir)
            val transport = GraphBridgeTransport(store)
            val service = CommandServiceImpl(
                transport = transport,
                queue = InMemoryCommandQueue(),
                clock = InstantClock { now },
                requester = { CommandRequester("00000000-0000-0000-0000-000000000000") },
                expiryWindow = 4.hours,
            )
            val command = service.send(CommandType.MESSAGE, "remote-test", "hello", null)
            assertTrue(Ulid.isValid(command.commandId))
            assertEquals(now + 4.hours, command.expiresUtc)
            assertTrue(command.isExpired(command.expiresUtc), "expiry boundary is inclusive")
            val stored = store.get("commands/inbox/${command.commandId}.json")
            assertTrue(stored != null)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `resendAsNew allocates a new commandId`() = runBlocking {
        val dir = Files.createTempDirectory("lantern-core-")
        try {
            val store = FilesystemAppFolderStore(dir)
            val transport = GraphBridgeTransport(store)
            val service = CommandServiceImpl(
                transport = transport,
                queue = InMemoryCommandQueue(),
                clock = InstantClock { now },
                requester = { CommandRequester("00000000-0000-0000-0000-000000000000") },
            )
            val first = service.send(CommandType.MESSAGE, "remote-test", "hello", null)
            val second = service.resendAsNew(first.commandId)
            assertNotEquals(first.commandId, second.commandId)
            assertEquals(first.text, second.text)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `enqueue is create-if-absent and does not duplicate`() = runBlocking {
        val dir = Files.createTempDirectory("lantern-core-")
        try {
            val store = FilesystemAppFolderStore(dir)
            val transport = GraphBridgeTransport(store)
            val service = CommandServiceImpl(
                transport = transport,
                queue = InMemoryCommandQueue(),
                clock = InstantClock { now },
                requester = { CommandRequester("00000000-0000-0000-0000-000000000000") },
                ulid = { "01KZYMGHG0NPD8AF5A96MP2YFW" },
            )
            val first = service.send(CommandType.MESSAGE, "remote-test", "one", null)
            service.send(CommandType.MESSAGE, "remote-test", "two", null)
            val stored = store.get("commands/inbox/${first.commandId}.json")
            val text = stored!!.content!!.decodeToString()
            assertTrue(text.contains("one"))
            assertTrue(!text.contains("\"text\":\"two\""))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `offline send remains durable and flushes after reconnect`() = runBlocking {
        val queue = InMemoryCommandQueue()
        val transport = FlakyTransport()
        val service = CommandServiceImpl(
            transport = transport,
            queue = queue,
            clock = InstantClock { now },
            requester = { CommandRequester("00000000-0000-0000-0000-000000000000") },
            ulid = { "01KZYMGHG0NPD8AF5A96MP2YFW" },
        )

        val command = service.send(CommandType.MESSAGE, "remote-test", "offline", null)
        assertEquals(command, queue.get(command.commandId))
        assertTrue(transport.enqueued.isEmpty())

        transport.available = true
        assertEquals(1, service.flushPending())
        assertEquals(listOf(command.commandId), transport.enqueued.map { it.commandId })
    }

    private class FlakyTransport : BridgeTransport {
        var available = false
        val enqueued = mutableListOf<RemoteCommand>()

        override suspend fun enqueueCommand(command: RemoteCommand) {
            check(available) { "offline" }
            if (enqueued.none { it.commandId == command.commandId }) {
                enqueued += command
            }
        }

        override suspend fun readProtocol(): ProtocolManifest? = null
        override suspend fun readStatus(agentId: String): AgentStatus? = null
        override suspend fun readResponse(commandId: String): RemoteResponse? = null
        override suspend fun readConversationEvents(conversationId: String): List<ConversationEvent> = emptyList()
        override suspend fun readActivity(sinceUtc: Instant): List<ActivityEvent> = emptyList()
        override suspend fun resolveArtifact(driveItemId: String): ArtifactDetails? = null
    }
}
