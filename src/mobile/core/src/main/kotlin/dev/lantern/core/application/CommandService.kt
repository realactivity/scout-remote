package dev.lantern.core.application

import dev.lantern.core.domain.CommandConstraints
import dev.lantern.core.domain.CommandType
import dev.lantern.core.domain.RemoteCommand
import dev.lantern.core.domain.RemoteResponse

/**
 * Command lifecycle. Draft, then queued (durable), then observed response states.
 * Drafts survive offline (cached locally, retried with backoff). A command is a durable job.
 * The user can close the app immediately after sending (spec §5, §15).
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

    /**
     * Replays unexpired commands from the durable local queue. Transport enqueue
     * is create-if-absent, so reconnect retries cannot duplicate remote commands.
     * Returns the number delivered or already present remotely.
     */
    suspend fun flushPending(): Int

    /** Expired commands are resent as new commands with new IDs. Never re-queued (spec §15). */
    suspend fun resendAsNew(expiredCommandId: String): RemoteCommand
}
