package dev.lantern.app.platform

import dev.lantern.core.metrics.GaugeMetric
import dev.lantern.core.metrics.MetricGauge
import dev.lantern.core.metrics.MetricOperation
import dev.lantern.core.metrics.MetricResult
import dev.lantern.core.metrics.OperationMetric
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LogcatLanternMetricsTest {
    @Test
    fun `log sink emits typed content-free values only`() {
        ShadowLog.clear()
        val metrics = LogcatLanternMetrics()

        metrics.recordOperation(
            OperationMetric(
                MetricOperation.GRAPH_READ,
                MetricResult.NOT_MODIFIED,
                durationMs = 12,
                retryCount = 1,
            ),
        )
        metrics.recordGauge(GaugeMetric(MetricGauge.SYNC_AGE_MS, 500))

        val text = ShadowLog.getLogsForTag("lantern.metrics").joinToString("\n") { it.msg }
        assertTrue(text.contains("operation=GRAPH_READ result=NOT_MODIFIED"))
        assertTrue(text.contains("gauge=SYNC_AGE_MS value=500"))
        assertFalse(text.contains("commandId"))
        assertFalse(text.contains("Bearer"))
        assertFalse(text.contains("/responses/"))
    }
}
