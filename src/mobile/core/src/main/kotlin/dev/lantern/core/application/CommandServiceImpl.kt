package dev.lantern.core.application

import dev.lantern.core.domain.CommandConstraints
import dev.lantern.core.domain.CommandRequester
import dev.lantern.core.domain.CommandType
import dev.lantern.core.domain.RemoteCommand
import dev.lantern.core.domain.RemoteResponse
import dev.lantern.core.ulid.Ulid
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CancellationException

class CommandServiceImpl(
    private val transport: BridgeTransport,
    private val queue: LocalCommandQueue,
    private val clock: InstantClock,
    private val requester: () -> CommandRequester,
    private val ulid: () -> String = { Ulid.generate() },
    private val expiryWindow: Duration = 4.hours,
) : CommandService {

    override suspend fun send(
        type: CommandType,
        conversationId: String,
        text: String?,
        constraints: CommandConstraints?,
    ): RemoteCommand {
        val now = clock.now()
        val command = RemoteCommand(
            schemaVersion = 1,
            commandId = ulid(),
            conversationId = conversationId,
            type = type,
            createdUtc = now,
            expiresUtc = now + expiryWindow,
            requestedBy = requester(),
            text = text,
            constraints = constraints ?: CommandConstraints(allowSensitiveActions = false),
        )
        queue.put(command)
        enqueueBestEffort(command)
        return command
    }

    override suspend fun getResponse(commandId: String): RemoteResponse? =
        transport.readResponse(commandId)

    override suspend fun flushPending(): Int {
        val now = clock.now()
        var delivered = 0
        for (command in queue.all().filterNot { it.isExpired(now) }) {
            if (enqueueBestEffort(command)) {
                delivered++
            }
        }
        return delivered
    }

    override suspend fun resendAsNew(expiredCommandId: String): RemoteCommand {
        val original = queue.get(expiredCommandId)
            ?: error("Unknown command $expiredCommandId")
        return send(
            type = original.type,
            conversationId = original.conversationId,
            text = original.text,
            constraints = original.constraints,
        )
    }

    private suspend fun enqueueBestEffort(command: RemoteCommand): Boolean =
        try {
            transport.enqueueCommand(command)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
}
