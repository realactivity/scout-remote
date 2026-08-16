package dev.lantern.core.metrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LanternMetricsTest {
    @Test
    fun `in-memory metrics retain content-free operation values`() {
        val metrics = InMemoryLanternMetrics()
        metrics.recordOperation(
            OperationMetric(
                operation = MetricOperation.GRAPH_READ,
                result = MetricResult.NOT_MODIFIED,
                durationMs = 12,
                retryCount = 1,
            ),
        )
        metrics.recordGauge(GaugeMetric(MetricGauge.SYNC_AGE_MS, 500))

        assertEquals(1, metrics.operations.size)
        assertEquals(MetricResult.NOT_MODIFIED, metrics.operations.single().result)
        assertEquals(GaugeMetric(MetricGauge.SYNC_AGE_MS, 500), metrics.gauges.single())
    }
}
