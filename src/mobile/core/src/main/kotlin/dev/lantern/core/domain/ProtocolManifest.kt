package dev.lantern.core.domain

import kotlinx.serialization.Serializable

/**
 * Bridge-published protocol handshake (protocol.json).
 * Contract: contracts/schemas/protocol.schema.json.
 */
@Serializable
data class ProtocolManifest(
    val protocol: String,
    val version: String,
    val agentId: String,
    val bridgeVersion: String,
    val capabilities: List<String>,
    val nativeScoutChatMirror: Boolean = false,
    val remoteApprovals: Boolean = false,
) {
    /** Stop processing an incompatible major version (spec §15). */
    val isCompatible: Boolean
        get() = protocol == PROTOCOL_NAME &&
            version.substringBefore('.').toIntOrNull() == SUPPORTED_MAJOR_VERSION

    companion object {
        const val PROTOCOL_NAME: String = "lantern"

        /** Protocol major version this client implements. */
        const val SUPPORTED_MAJOR_VERSION: Int = 1
    }
}
