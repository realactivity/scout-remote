package dev.lantern.core.domain

import kotlin.time.Duration
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bridge-published agent status snapshot (agents/{agentId}/status.json, schemaVersion 1).
 * Contract: contracts/schemas/agent-status.schema.json.
 */
@Serializable
data class AgentStatus(
    val schemaVersion: Int,
    val agentId: String,
    val state: AgentState,
    val lastSeenUtc: Instant,
    val currentTask: CurrentTask? = null,
    val attention: AttentionInfo = AttentionInfo(),
) {
    /**
     * Presence is evidence-based: stale/offline is derived from [lastSeenUtc] against a
     * configurable threshold, never reported by the bridge itself (spec Appendix A.2).
     */
    fun isStale(staleThreshold: Duration, nowUtc: Instant): Boolean =
        nowUtc - lastSeenUtc > staleThreshold
}

@Serializable
data class CurrentTask(
    val title: String,
    val startedUtc: Instant? = null,
    val progressText: String? = null,
)

@Serializable
data class AttentionInfo(
    val required: Boolean = false,
    val reason: String? = null,
)

/** Bridge-reported state; "stale/offline" is a client-derived overlay, not a wire value. */
@Serializable
enum class AgentState {
    @SerialName("unknown") UNKNOWN,
    @SerialName("idle") IDLE,
    @SerialName("working") WORKING,
    @SerialName("waiting_for_desktop") WAITING_FOR_DESKTOP,
}
