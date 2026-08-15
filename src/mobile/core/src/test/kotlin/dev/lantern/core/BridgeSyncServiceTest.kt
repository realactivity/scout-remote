package dev.lantern.core

import dev.lantern.core.application.BridgeSyncServiceImpl
import dev.lantern.core.application.InstantClock
import dev.lantern.core.application.isTerminal
import dev.lantern.core.domain.AgentState
import dev.lantern.core.domain.AgentStatus
import dev.lantern.core.domain.AttentionInfo
import dev.lantern.core.domain.CommandStatus
import dev.lantern.core.domain.ProtocolJson
import dev.lantern.core.domain.ProtocolManifest
import dev.lantern.core.domain.RemoteResponse
import dev.lantern.core.storage.FilesystemAppFolderStore
import dev.lantern.core.transport.GraphBridgeTransport
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString

class BridgeSyncServiceTest {

    private val now = Instant.parse("2026-08-15T03:00:00Z")
    private val json = ProtocolJson.json

    @Test
    fun `foreground refresh gates incompatible protocol major`() = runBlocking {
        val dir = Files.createTempDirectory("lantern-sync-")
        try {
            val store = FilesystemAppFolderStore(dir)
            val protocol = ProtocolManifest(
                protocol = "lantern",
                version = "2.0",
                agentId = "primary",
                bridgeVersion = "0.2.0",
                capabilities = listOf("status.read"),
            )
            store.write("protocol.json", json.encodeToString(protocol).encodeToByteArray())
            val sync = BridgeSyncServiceImpl(GraphBridgeTransport(store), InstantClock { now })
            val result = sync.refreshForeground()
            assertTrue(result.protocolIncompatible)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `foreground refresh reads status after compatible handshake`() = runBlocking {
        val dir = Files.createTempDirectory("lantern-sync-")
        try {
            val store = FilesystemAppFolderStore(dir)
            val protocol = ProtocolManifest(
                protocol = "lantern",
                version = "1.0",
                agentId = "primary",
                bridgeVersion = "0.2.0",
                capabilities = listOf("status.read", "activity.read", "message.send", "summary.request", "artifacts.read"),
            )
            store.write("protocol.json", json.encodeToString(protocol).encodeToByteArray())
            val status = AgentStatus(
                schemaVersion = 1,
                agentId = "primary",
                state = AgentState.IDLE,
                lastSeenUtc = now,
                attention = AttentionInfo(required = false),
            )
            store.write("agents/primary/status.json", json.encodeToString(status).encodeToByteArray())
            val sync = BridgeSyncServiceImpl(GraphBridgeTransport(store), InstantClock { now })
            val result = sync.refreshForeground()
            assertFalse(result.protocolIncompatible)
            assertEquals(AgentState.IDLE, result.status?.state)
            assertFalse(result.status!!.isStale(kotlin.time.Duration.parse("30m"), now))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `in-flight watch backs off and stops on terminal status`() = runBlocking {
        val dir = Files.createTempDirectory("lantern-watch-")
        try {
            val store = FilesystemAppFolderStore(dir)
            val transport = GraphBridgeTransport(store)
            val delays = mutableListOf<kotlin.time.Duration>()
            val sync = BridgeSyncServiceImpl(transport, InstantClock { now })
            val commandId = "01KZYMGHG0NPD8AF5A96MP2YFW"
            var polls = 0
            val response = sync.watchInFlight(
                commandId = commandId,
                isForeground = { true },
                initialDelay = 15.milliseconds,
                maxDelay = 60.milliseconds,
                delay = { wait ->
                    delays += wait
                    polls++
                    if (polls == 2) {
                        val completed = RemoteResponse(
                            schemaVersion = 1,
                            commandId = commandId,
                            status = CommandStatus.COMPLETED,
                            message = "done",
                        )
                        store.write("responses/$commandId.json", json.encodeToString(completed).encodeToByteArray())
                    }
                },
            )
            assertEquals(CommandStatus.COMPLETED, response?.status)
            assertTrue(response!!.status.isTerminal())
            assertTrue(delays.size >= 2)
            assertTrue(delays[1] >= delays[0])
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `in-flight watch stops at its duration budget`() = runBlocking {
        val dir = Files.createTempDirectory("lantern-watch-budget-")
        try {
            val sync = BridgeSyncServiceImpl(
                GraphBridgeTransport(FilesystemAppFolderStore(dir)),
                InstantClock { now },
            )
            val delays = mutableListOf<kotlin.time.Duration>()

            val response = sync.watchInFlight(
                commandId = "01KZYMGHG0NPD8AF5A96MP2YFW",
                isForeground = { true },
                initialDelay = 15.seconds,
                maxDelay = 60.seconds,
                maxWatchDuration = 45.seconds,
                delay = { delays += it },
            )

            assertEquals(null, response)
            assertEquals(45.seconds, delays.fold(kotlin.time.Duration.ZERO) { total, item -> total + item })
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
