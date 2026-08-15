package dev.lantern.core.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bridge-to-mobile response (responses/{commandId}.json, schemaVersion 1).
 * Mutable while in flight (the bridge updates it through ACKNOWLEDGED/RUNNING); immutable once
 * it reaches a terminal status. Corrections are new events referencing the original command.
 * Contract: contracts/schemas/response.schema.json.
 */
@Serializable
data class RemoteResponse(
    val schemaVersion: Int,
    val commandId: String,
    val status: CommandStatus,
    val startedUtc: Instant? = null,
    val completedUtc: Instant? = null,
    val message: String? = null,

    /** When status is WAITING_FOR_DESKTOP_APPROVAL: exactly what the user must do on the desktop. */
    val attentionReason: String? = null,

    val artifacts: List<ArtifactReference> = emptyList(),
    val usage: UsageInfo? = null,
)

/**
 * Wire command status (spec Appendix A.1, response.schema.json).
 * The client-local "draft" state (an unsent command cached on-device, spec §5, §15)
 * deliberately does not appear here. Drafts never reach the wire.
 */
@Serializable
enum class CommandStatus {
    @SerialName("queued") QUEUED,
    @SerialName("acknowledged") ACKNOWLEDGED,
    @SerialName("running") RUNNING,
    @SerialName("completed") COMPLETED,
    @SerialName("failed") FAILED,
    @SerialName("expired") EXPIRED,
    @SerialName("waiting_for_desktop_approval") WAITING_FOR_DESKTOP_APPROVAL,

    /** Only when cancellation is confirmed by the bridge. Never claimed optimistically. */
    @SerialName("canceled") CANCELED,
}

/** Artifacts are OneDrive items referenced by ID; content is never duplicated into protocol JSON (spec §11.1). */
@Serializable
data class ArtifactReference(
    val name: String,
    val driveItemId: String,
)

@Serializable
data class UsageInfo(
    val workIqCalls: Int = 0,
    val modelTokens: Long? = null,
)
