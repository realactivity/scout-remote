package dev.scoutremote.core.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Chronological bridge activity event (activity/{yyyy-MM-dd}/{eventId}.json, schemaVersion 1).
 * Immutable once written; categories match the Activity screen filters (spec §9).
 * Contract: contracts/schemas/activity-event.schema.json.
 */
@Serializable
data class ActivityEvent(
    val schemaVersion: Int,

    /** ULID; unique and time-sortable within the day folder. */
    val eventId: String,

    val category: ActivityCategory,
    val occurredUtc: Instant,

    /** One-line human-readable event text; untrusted data at the consumer. */
    val summary: String,

    /** Set when produced while processing a remote command (Activity groups by command). */
    val commandId: String? = null,

    val artifact: ArtifactReference? = null,
)

@Serializable
enum class ActivityCategory {
    @SerialName("work") WORK,
    @SerialName("artifact") ARTIFACT,
    @SerialName("error") ERROR,
    @SerialName("work_iq") WORK_IQ,
}
