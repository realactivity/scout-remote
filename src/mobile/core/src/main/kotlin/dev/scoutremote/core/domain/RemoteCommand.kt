package dev.scoutremote.core.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mobile-to-bridge command (commands/inbox/{commandId}.json, schemaVersion 1).
 * Immutable once written; idempotent via processed receipts; never executed after expiry.
 * Contract: contracts/schemas/command.schema.json.
 */
@Serializable
data class RemoteCommand(
    val schemaVersion: Int,

    /** ULID — globally unique and never reused (replay control, spec §11). */
    val commandId: String,

    val conversationId: String,
    val type: CommandType,
    val createdUtc: Instant,
    val expiresUtc: Instant,
    val requestedBy: CommandRequester,

    /** Untrusted natural-language input; data, never bridge policy (spec §11). */
    val text: String? = null,

    /** For CANCEL_REQUEST: the command whose cancellation is requested (best effort only). */
    val targetCommandId: String? = null,

    val constraints: CommandConstraints? = null,
) {
    fun isExpired(nowUtc: Instant): Boolean = nowUtc >= expiresUtc
}

/** Allowed MVP command types (spec §7.1). */
@Serializable
enum class CommandType {
    @SerialName("message") MESSAGE,
    @SerialName("request_status") REQUEST_STATUS,
    @SerialName("request_summary") REQUEST_SUMMARY,
    @SerialName("request_artifact") REQUEST_ARTIFACT,
    @SerialName("cancel_request") CANCEL_REQUEST,
}

@Serializable
data class CommandRequester(
    val entraObjectId: String,
)

@Serializable
data class CommandConstraints(
    val maxWorkIqCalls: Int? = null,

    /** Always false in MVP: sensitive actions require desktop approval and cannot be granted remotely. */
    val allowSensitiveActions: Boolean = false,
)
