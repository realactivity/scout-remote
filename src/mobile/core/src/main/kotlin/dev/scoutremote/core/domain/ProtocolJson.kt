package dev.scoutremote.core.domain

import kotlinx.serialization.json.Json

/**
 * The single Json configuration for all protocol I/O.
 *
 * - explicitNulls = false. Absent optional fields stay absent on the wire. The schemas
 *   type optional fields (e.g. `targetCommandId`) as non-null strings, so encoding
 *   explicit nulls would produce schema-invalid JSON.
 * - encodeDefaults = false: only values actually set are written.
 * - Unknown keys are rejected (default): the schemas declare additionalProperties: false,
 *   and the security posture is validate-schema-first (spec §11).
 */
object ProtocolJson {
    val json: Json = Json {
        explicitNulls = false
        encodeDefaults = false
    }
}
