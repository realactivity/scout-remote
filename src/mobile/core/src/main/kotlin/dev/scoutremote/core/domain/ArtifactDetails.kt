package dev.scoutremote.core.domain

import kotlinx.datetime.Instant

/**
 * Resolved OneDrive artifact metadata for the Artifacts screen (name, type, size, created,
 * open link — spec §9). Not a protocol file: the transport resolves an [ArtifactReference]'s
 * driveItemId against Graph on demand. Content is never copied into protocol JSON (spec §11.1).
 */
data class ArtifactDetails(
    val driveItemId: String,
    val name: String,
    val sizeBytes: Long? = null,
    val createdUtc: Instant? = null,

    /** OneDrive web link used to open the artifact in the appropriate Microsoft 365 app. */
    val webUrl: String? = null,
)
