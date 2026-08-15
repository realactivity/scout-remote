package dev.scoutremote.core.application

import dev.scoutremote.core.domain.CommandConstraints
import dev.scoutremote.core.domain.CommandType
import dev.scoutremote.core.domain.RemoteCommand
import dev.scoutremote.core.domain.RemoteResponse

/**
 * Command lifecycle: draft → queued (durable) → observed response states.
 * Drafts survive offline (cached locally, retried with backoff); a command is a durable job —
 * the user can close the app immediately after sending (spec §5, §15).
 */
interface CommandService {

    /** Creates and enqueues a command with a fresh ULID and the configured expiry window. */
    suspend fun send(
        type: CommandType,
        conversationId: String,
        text: String?,
        constraints: CommandConstraints?,
    ): RemoteCommand

    suspend fun getResponse(commandId: String): RemoteResponse?

    /** Expired commands are resent as new commands with new IDs — never re-queued (spec §15). */
    suspend fun resendAsNew(expiredCommandId: String): RemoteCommand
}
