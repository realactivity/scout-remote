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
import dev.lantern.core.domain.CommandType
import dev.lantern.core.domain.RemoteCommand
import dev.lantern.core.domain.RemoteResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

    private object UnusedCommands : CommandService {
        override suspend fun send(
            type: CommandType,
            conversationId: String,
            text: String?,
            constraints: CommandConstraints?,
        ): RemoteCommand = error("unused")

        override suspend fun getResponse(commandId: String): RemoteResponse? = error("unused")

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
            delay: suspend (kotlin.time.Duration) -> Unit,
        ): RemoteResponse? = error("unused")
    }
}
