package dev.lantern.core.application

import dev.lantern.core.domain.CommandConstraints
import dev.lantern.core.domain.CommandRequester
import dev.lantern.core.domain.CommandType
import dev.lantern.core.domain.RemoteCommand
import dev.lantern.core.domain.RemoteResponse
import dev.lantern.core.metrics.GaugeMetric
import dev.lantern.core.metrics.LanternMetrics
import dev.lantern.core.metrics.MetricOperation
import dev.lantern.core.metrics.MetricGauge
import dev.lantern.core.metrics.MetricResult
import dev.lantern.core.metrics.NoOpLanternMetrics
import dev.lantern.core.metrics.OperationMetric
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
    private val metrics: LanternMetrics = NoOpLanternMetrics,
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
        val started = System.nanoTime()
        val delivered = enqueueBestEffort(command)
        metrics.recordOperation(
            OperationMetric(
                MetricOperation.COMMAND_ENQUEUE,
                if (delivered) MetricResult.SUCCESS else MetricResult.OFFLINE_QUEUED,
                (System.nanoTime() - started) / 1_000_000,
            ),
        )
        metrics.recordGauge(GaugeMetric(MetricGauge.UNEXPIRED_COMMAND_CACHE_ENTRIES, pendingCount()))
        return command
    }

    override suspend fun getResponse(commandId: String): RemoteResponse? =
        transport.readResponse(commandId)

    override suspend fun flushPending(): Int {
        val started = System.nanoTime()
        val now = clock.now()
        var delivered = 0
        val pending = queue.all().filterNot { it.isExpired(now) }
        for (command in pending) {
            if (enqueueBestEffort(command)) {
                delivered++
            }
        }
        metrics.recordOperation(
            OperationMetric(
                MetricOperation.COMMAND_FLUSH,
                if (delivered == pending.size) MetricResult.SUCCESS else MetricResult.OFFLINE_QUEUED,
                (System.nanoTime() - started) / 1_000_000,
            ),
        )
        metrics.recordGauge(GaugeMetric(MetricGauge.UNEXPIRED_COMMAND_CACHE_ENTRIES, pendingCount()))
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

    private suspend fun pendingCount(): Long {
        val now = clock.now()
        return queue.all().count { !it.isExpired(now) }.toLong()
    }
}
