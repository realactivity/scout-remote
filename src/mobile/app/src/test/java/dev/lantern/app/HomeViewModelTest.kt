package dev.lantern.app

import dev.lantern.core.application.BridgeSyncService
import dev.lantern.core.application.CommandService
import dev.lantern.core.application.InstantClock
import dev.lantern.core.application.SyncResult
import dev.lantern.core.brand.StatusToken
import dev.lantern.core.domain.AgentState
import dev.lantern.core.domain.AgentStatus
import dev.lantern.core.domain.AttentionInfo
import dev.lantern.core.domain.CommandConstraints
import dev.lantern.core.domain.CommandRequester
import dev.lantern.core.domain.CommandStatus
import dev.lantern.core.domain.CommandType
import dev.lantern.core.domain.RemoteCommand
import dev.lantern.core.domain.RemoteResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant

class HomeViewModelTest {

    @Test
    fun `stale lastSeenUtc maps the pane to offline`() {
        val now = Instant.parse("2026-08-15T04:00:00Z")
        val vm = HomeViewModel(
            commands = UnusedCommands,
            sync = UnusedSync,
            clock = InstantClock { now },
        )
        vm.applyStatus(
            AgentStatus(
                schemaVersion = 1,
                agentId = "primary",
                state = AgentState.IDLE,
                lastSeenUtc = Instant.parse("2026-08-15T03:00:00Z"),
                attention = AttentionInfo(required = false),
            ),
        )
        assertTrue(vm.state.value.stale)
        assertEquals(StatusToken.OFFLINE, vm.state.value.pane)
        assertEquals("2026-08-15T03:00:00Z", vm.state.value.lastSeenUtc)
    }

    @Test
    fun `refresh blocks pending commands on incompatible protocol`() = runBlocking {
        val commands = FakeCommands()
        val sync = FakeSync(
            refreshResult = SyncResult(
                syncedUtc = NOW,
                protocolIncompatible = true,
            ),
        )
        val vm = HomeViewModel(commands, sync, InstantClock { NOW })

        vm.refreshNow()

        assertEquals(0, commands.flushes)
        assertTrue(vm.state.value.protocolIncompatible)
        assertTrue(vm.state.value.message.orEmpty().contains("upgrade required"))
    }

    @Test
    fun `send watches in-flight command and publishes terminal response`() = runBlocking {
        val commands = FakeCommands()
        val response = RemoteResponse(
            schemaVersion = 1,
            commandId = COMMAND_ID,
            status = CommandStatus.COMPLETED,
            message = "done",
        )
        val sync = FakeSync(watchResult = response)
        val vm = HomeViewModel(commands, sync, InstantClock { NOW })

        vm.sendAndWatch("hello")

        assertEquals(COMMAND_ID, sync.watchedCommandId)
        assertEquals(CommandStatus.COMPLETED, vm.state.value.lastResponse?.status)
        assertEquals("", vm.state.value.draft)
    }

    private object UnusedCommands : CommandService {
        override suspend fun send(
            type: CommandType,
            conversationId: String,
            text: String?,
            constraints: CommandConstraints?,
        ): RemoteCommand = error("unused")

        override suspend fun getResponse(commandId: String): RemoteResponse? = error("unused")

        override suspend fun flushPending(): Int = error("unused")

        override suspend fun resendAsNew(expiredCommandId: String): RemoteCommand = error("unused")
    }

    private object UnusedSync : BridgeSyncService {
        override suspend fun refreshForeground(): SyncResult = error("unused")

        override suspend fun refreshBackground(): SyncResult = error("unused")

        override suspend fun watchInFlight(
            commandId: String,
            isForeground: () -> Boolean,
            initialDelay: kotlin.time.Duration,
            maxDelay: kotlin.time.Duration,
            maxWatchDuration: kotlin.time.Duration,
            delay: suspend (kotlin.time.Duration) -> Unit,
        ): RemoteResponse? = error("unused")
    }

    private class FakeCommands : CommandService {
        var flushes = 0

        override suspend fun send(
            type: CommandType,
            conversationId: String,
            text: String?,
            constraints: CommandConstraints?,
        ): RemoteCommand = RemoteCommand(
            schemaVersion = 1,
            commandId = COMMAND_ID,
            conversationId = conversationId,
            type = type,
            createdUtc = NOW,
            expiresUtc = NOW + 4.hours,
            requestedBy = CommandRequester("00000000-0000-0000-0000-000000000000"),
            text = text,
            constraints = constraints,
        )

        override suspend fun getResponse(commandId: String): RemoteResponse? = null

        override suspend fun flushPending(): Int {
            flushes++
            return 1
        }

        override suspend fun resendAsNew(expiredCommandId: String): RemoteCommand = error("unused")
    }

    private class FakeSync(
        private val refreshResult: SyncResult = SyncResult(syncedUtc = NOW),
        private val watchResult: RemoteResponse? = null,
    ) : BridgeSyncService {
        var watchedCommandId: String? = null

        override suspend fun refreshForeground(): SyncResult = refreshResult

        override suspend fun refreshBackground(): SyncResult = refreshResult

        override suspend fun watchInFlight(
            commandId: String,
            isForeground: () -> Boolean,
            initialDelay: kotlin.time.Duration,
            maxDelay: kotlin.time.Duration,
            maxWatchDuration: kotlin.time.Duration,
            delay: suspend (kotlin.time.Duration) -> Unit,
        ): RemoteResponse? {
            watchedCommandId = commandId
            return watchResult
        }
    }

    companion object {
        private val NOW = Instant.parse("2026-08-15T04:00:00Z")
        private const val COMMAND_ID = "01KZYMGHG0NPD8AF5A96MP2YFW"
    }
}
