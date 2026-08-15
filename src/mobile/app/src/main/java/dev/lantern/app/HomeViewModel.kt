package dev.lantern.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.lantern.core.application.BridgeSyncService
import dev.lantern.core.application.CommandService
import dev.lantern.core.application.InstantClock
import dev.lantern.core.application.SyncResult
import dev.lantern.core.brand.StatusToken
import dev.lantern.core.brand.paneStatus
import dev.lantern.core.domain.AgentState
import dev.lantern.core.domain.AgentStatus
import dev.lantern.core.domain.CommandConstraints
import dev.lantern.core.domain.CommandType
import dev.lantern.core.domain.RemoteResponse
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val status: AgentStatus? = null,
    val lastSeenUtc: String? = null,
    val stale: Boolean = true,
    val draft: String = "",
    val lastResponse: RemoteResponse? = null,
    val message: String? = null,
    val sending: Boolean = false,
    val protocolIncompatible: Boolean = false,
) {
    val pane: StatusToken
        get() = paneStatus(status?.state ?: AgentState.UNKNOWN, stale)
}

class HomeViewModel(
    private val commands: CommandService,
    private val sync: BridgeSyncService,
    private val clock: InstantClock,
    private val staleThreshold: kotlin.time.Duration = 30.minutes,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state
    @Volatile
    private var foreground = false

    fun onDraftChange(value: String) {
        _state.value = _state.value.copy(draft = value)
    }

    fun refresh() {
        viewModelScope.launch {
            refreshNow()
        }
    }

    fun onForegroundStart() {
        if (foreground) {
            return
        }
        foreground = true
        refresh()
    }

    fun onForegroundStop() {
        foreground = false
    }

    fun send() {
        val text = _state.value.draft.trim()
        if (text.isEmpty() || _state.value.sending || _state.value.protocolIncompatible) {
            return
        }
        viewModelScope.launch {
            sendAndWatch(text)
        }
    }

    internal suspend fun refreshNow() {
        try {
            val initial = sync.refreshForeground()
            applySync(initial)
            if (!initial.protocolIncompatible && commands.flushPending() > 0) {
                applySync(sync.refreshForeground())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _state.value = _state.value.copy(
                message = "sync unavailable; queued commands remain local",
            )
        }
    }

    internal suspend fun sendAndWatch(text: String) {
        _state.value = _state.value.copy(sending = true, message = null)
        try {
            val command = commands.send(
                type = CommandType.MESSAGE,
                conversationId = "remote-home",
                text = text,
                constraints = CommandConstraints(allowSensitiveActions = false),
            )
            _state.value = _state.value.copy(
                sending = false,
                draft = "",
                message = "queued ${command.commandId}",
            )
            val response = sync.watchInFlight(
                commandId = command.commandId,
                isForeground = { foreground },
            )
            if (response != null) {
                applyStatus(_state.value.status, listOf(response))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _state.value = _state.value.copy(
                sending = false,
                message = "send unavailable; command remains in the local queue",
            )
        }
    }

    fun applyStatus(status: AgentStatus?, responses: List<RemoteResponse> = emptyList()) {
        val now = clock.now()
        _state.value = _state.value.copy(
            status = status,
            lastSeenUtc = status?.lastSeenUtc?.toString(),
            stale = status?.isStale(staleThreshold, now) ?: true,
            lastResponse = responses.lastOrNull() ?: _state.value.lastResponse,
        )
    }

    private fun applySync(result: SyncResult) {
        if (result.protocolIncompatible) {
            _state.value = _state.value.copy(
                protocolIncompatible = true,
                message = "protocol upgrade required before commands can be sent",
            )
            return
        }
        _state.value = _state.value.copy(protocolIncompatible = false)
        applyStatus(result.status, result.newResponses)
    }
}
