package dev.lantern.app.platform

import android.util.Log
import dev.lantern.core.metrics.GaugeMetric
import dev.lantern.core.metrics.LanternMetrics
import dev.lantern.core.metrics.OperationMetric

/**
 * Content-free local metrics. Values contain enums, counts, and durations only.
 * No paths, IDs, protocol text, account data, or credentials are accepted.
 */
class LogcatLanternMetrics : LanternMetrics {
    override fun recordOperation(metric: OperationMetric) {
        Log.i(
            TAG,
            "operation=${metric.operation.name} result=${metric.result.name} "
                + "durationMs=${metric.durationMs} retries=${metric.retryCount}",
        )
    }

    override fun recordGauge(metric: GaugeMetric) {
        Log.i(TAG, "gauge=${metric.gauge.name} value=${metric.value}")
    }

    companion object {
        private const val TAG = "lantern.metrics"
    }
}
