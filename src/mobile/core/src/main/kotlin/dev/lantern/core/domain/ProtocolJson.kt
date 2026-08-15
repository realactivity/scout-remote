package dev.lantern.core.domain

import kotlinx.serialization.json.Json

/**
 * The single Json configuration for all protocol I/O on the mobile side.
 *
 * Compatibility policy (spec §15.2): tolerant reader, strict writer.
 *
 * - ignoreUnknownKeys = true. The mobile client only reads bridge-authored files
 *   (status, responses, events, usage, handshake), and a newer bridge may add optional
 *   fields in a minor revision. Unknown fields are ignored, never an error. Strictness
 *   is not a mobile-side security control; the strict gate is the bridge's command
 *   inbox, which rejects any command that fails full schema validation.
 * - explicitNulls = false. Absent optional fields stay absent on the wire. The schemas
 *   type optional fields (e.g. `targetCommandId`) as non-null strings, so encoding
 *   explicit nulls would produce schema-invalid JSON (writers are strict).
 * - encodeDefaults = false: only values actually set are written.
 */
object ProtocolJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }
}
