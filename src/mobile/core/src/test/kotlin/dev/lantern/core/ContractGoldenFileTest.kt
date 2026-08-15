package dev.lantern.core

import dev.lantern.core.domain.ActivityCategory
import dev.lantern.core.domain.ActivityEvent
import dev.lantern.core.domain.AgentState
import dev.lantern.core.domain.AgentStatus
import dev.lantern.core.domain.CommandStatus
import dev.lantern.core.domain.CommandType
import dev.lantern.core.domain.ConversationEvent
import dev.lantern.core.domain.ConversationEventType
import dev.lantern.core.domain.ProtocolJson
import dev.lantern.core.domain.ProtocolManifest
import dev.lantern.core.domain.RemoteCommand
import dev.lantern.core.domain.RemoteResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString

/**
 * Golden-file contract tests: every canonical payload in contracts/examples must decode
 * into the domain model and survive a decode, encode, decode round trip unchanged.
 * The bridge runs the same fixtures against the .NET domain model (contracts/README.md).
 */
class ContractGoldenFileTest {

    private val json = ProtocolJson.json

    private val examplesDir: Path by lazy {
        var dir = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (!Files.isDirectory(dir.resolve("contracts/examples"))) {
            dir = dir.parent ?: error("contracts/examples not found above ${System.getProperty("user.dir")}")
        }
        dir.resolve("contracts/examples")
    }

    private fun read(name: String): String = Files.readString(examplesDir.resolve(name))

    private inline fun <reified T> roundTrip(name: String): T {
        val first = json.decodeFromString<T>(read(name))
        val second = json.decodeFromString<T>(json.encodeToString(first))
        assertEquals(first, second, "round trip of $name must be lossless")
        return first
    }

    @Test
    fun `protocol handshake decodes and is compatible`() {
        val protocol = roundTrip<ProtocolManifest>("protocol.json")
        assertEquals(ProtocolManifest.PROTOCOL_NAME, protocol.protocol)
        assertTrue(protocol.isCompatible)
        assertFalse(protocol.remoteApprovals, "no remote approvals in MVP (spec §11)")
        assertEquals(5, protocol.capabilities.size)
    }

    @Test
    fun `incompatible major version is rejected`() {
        val protocol = roundTrip<ProtocolManifest>("protocol.json")
        assertFalse(protocol.copy(version = "2.0").isCompatible)
        assertFalse(protocol.copy(protocol = "other").isCompatible)
    }

    @Test
    fun `agent status decodes with derived staleness`() {
        val status = roundTrip<AgentStatus>("status.json")
        assertEquals(AgentState.WORKING, status.state)
        assertEquals("Reviewing architecture notes", status.currentTask?.title)
        assertFalse(status.attention.required)

        val threshold = kotlin.time.Duration.parse("30m")
        assertFalse(status.isStale(threshold, status.lastSeenUtc + kotlin.time.Duration.parse("29m")))
        assertTrue(status.isStale(threshold, status.lastSeenUtc + kotlin.time.Duration.parse("31m")))
    }

    @Test
    fun `message command decodes with expiry semantics`() {
        val command = roundTrip<RemoteCommand>("command.message.json")
        assertEquals(CommandType.MESSAGE, command.type)
        assertEquals(1, command.schemaVersion)
        assertEquals(false, command.constraints?.allowSensitiveActions)

        assertFalse(command.isExpired(command.expiresUtc - kotlin.time.Duration.parse("1s")))
        assertTrue(command.isExpired(command.expiresUtc), "expiry boundary is inclusive")
    }

    @Test
    fun `completed response decodes with artifacts and usage`() {
        val response = roundTrip<RemoteResponse>("response.completed.json")
        assertEquals(CommandStatus.COMPLETED, response.status)
        assertEquals(1, response.artifacts.size)
        assertEquals("architecture-review.docx", response.artifacts[0].name)
        assertEquals(0, response.usage?.workIqCalls)
        assertEquals(null, response.usage?.modelTokens)
    }

    @Test
    fun `conversation event decodes and references its command`() {
        val event = roundTrip<ConversationEvent>("conversation-event.response.json")
        assertEquals(ConversationEventType.RESPONSE, event.type)
        val command = json.decodeFromString<RemoteCommand>(read("command.message.json"))
        assertEquals(command.commandId, event.commandId, "event must reference the command protocol file")
        assertEquals(command.conversationId, event.conversationId)
    }

    @Test
    fun `activity event decodes with wire category names`() {
        val event = roundTrip<ActivityEvent>("activity-event.work.json")
        assertEquals(ActivityCategory.WORK, event.category)
        assertTrue(event.summary.isNotBlank())
    }

    @Test
    fun `enums use snake_case wire names`() {
        assertEquals("\"waiting_for_desktop\"", json.encodeToString(AgentState.WAITING_FOR_DESKTOP))
        assertEquals("\"waiting_for_desktop_approval\"", json.encodeToString(CommandStatus.WAITING_FOR_DESKTOP_APPROVAL))
        assertEquals("\"cancel_request\"", json.encodeToString(CommandType.CANCEL_REQUEST))
        assertEquals("\"work_iq\"", json.encodeToString(ActivityCategory.WORK_IQ))
    }

    @Test
    fun `optional absent fields stay absent on encode`() {
        val command = json.decodeFromString<RemoteCommand>(read("command.message.json"))
        val encoded = json.encodeToString(command)
        assertFalse(encoded.contains("targetCommandId"), "explicit nulls would violate the schema")
    }

    @Test
    fun `timestamps round trip as UTC instants`() {
        val status = json.decodeFromString<AgentStatus>(read("status.json"))
        assertEquals(Instant.parse("2026-08-13T22:32:10Z"), status.lastSeenUtc)
    }
}
