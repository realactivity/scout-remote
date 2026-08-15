package dev.lantern.core.metrics

import java.util.Collections

/** Content-free operation names. Never add paths, IDs, text, or account data. */
enum class MetricOperation {
    GRAPH_READ,
    GRAPH_WRITE,
    GRAPH_DELTA,
    GRAPH_DELETE,
    SYNC_FOREGROUND,
    SYNC_BACKGROUND,
    COMMAND_ENQUEUE,
    COMMAND_FLUSH,
}

enum class MetricResult {
    SUCCESS,
    REDIRECT,
    NOT_MODIFIED,
    NOT_FOUND,
    CONFLICT,
    THROTTLED,
    UNAVAILABLE,
    CLIENT_ERROR,
    SERVER_ERROR,
    CANCELLED,
    OFFLINE_QUEUED,
    PROTOCOL_INCOMPATIBLE,
}

data class OperationMetric(
    val operation: MetricOperation,
    val result: MetricResult,
    val durationMs: Long,
    val retryCount: Int = 0,
)

enum class MetricGauge {
    SYNC_AGE_MS,
    UNEXPIRED_COMMAND_CACHE_ENTRIES,
}

data class GaugeMetric(
    val gauge: MetricGauge,
    val value: Long,
)

interface LanternMetrics {
    fun recordOperation(metric: OperationMetric)
    fun recordGauge(metric: GaugeMetric)
}

object NoOpLanternMetrics : LanternMetrics {
    override fun recordOperation(metric: OperationMetric) = Unit
    override fun recordGauge(metric: GaugeMetric) = Unit
}

class InMemoryLanternMetrics : LanternMetrics {
    val operations: MutableList<OperationMetric> = Collections.synchronizedList(mutableListOf())
    val gauges: MutableList<GaugeMetric> = Collections.synchronizedList(mutableListOf())

    override fun recordOperation(metric: OperationMetric) {
        operations += metric
    }

    override fun recordGauge(metric: GaugeMetric) {
        gauges += metric
    }
}
