package dev.lantern.core.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Append-only bridge-owned remote-thread event
 * (conversations/{conversationId}/{eventId}.json, schemaVersion 1).
 * Immutable once written. Command/response events reference their protocol files by
 * [commandId] rather than duplicating content, so the thread is reproducible from
 * protocol data (spec §8.1). Contract: contracts/schemas/conversation-event.schema.json.
 */
@Serializable
data class ConversationEvent(
    val schemaVersion: Int,

    /** ULID. Unique and time-sortable. The stream is ordered by eventId. */
    val eventId: String,

    val conversationId: String,
    val type: ConversationEventType,
    val occurredUtc: Instant,

    /** For COMMAND/RESPONSE events: the protocol file holding the content. */
    val commandId: String? = null,

    /** For ARTIFACT events. */
    val artifact: ArtifactReference? = null,

    /** For ATTENTION events: exactly what the user must do on the desktop. */
    val attentionReason: String? = null,
)

@Serializable
enum class ConversationEventType {
    @SerialName("command") COMMAND,
    @SerialName("response") RESPONSE,
    @SerialName("artifact") ARTIFACT,
    @SerialName("attention") ATTENTION,
}
