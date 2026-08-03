package at.posselt.pfrpg2e.kingdom.sheet.contexts

import kotlinx.js.JsPlainObject

@JsPlainObject
external interface MetricPointContext {
    var turn: Int
    var value: Double
    var x: Double
    var y: Double
}

@JsPlainObject
external interface ThresholdLineContext {
    var label: String
    var value: Double
    var y: Double
    var textY: Double
}

@JsPlainObject
external interface MetricSeriesContext {
    var key: String
    var label: String
    var points: Array<MetricPointContext>
    var polylinePoints: String
    var min: Double
    var max: Double
    var current: Double
    var mean: Double
    var deltaFromStart: Double
    var hasThresholds: Boolean
    var thresholdLines: Array<ThresholdLineContext>?
}

@JsPlainObject
external interface AnalyticsContext {
    var series: Array<MetricSeriesContext>
    var windowSize: Int
    var hasData: Boolean
    var isGM: Boolean
}

/**
 * Metric keys visible to players (mirrors the player-safe Recent Turns timeline, commit 654d3b98).
 * GM-only series (consumption, ruin internals, war pressure, xp) are excluded for non-GMs.
 */
val analyticsPlayerSafeMetricKeys = setOf("unrest", "fame", "resourcePoints", "size", "level")

/** GM sees every metric; a non-GM sees only [analyticsPlayerSafeMetricKeys]. */
fun <T> filterAnalyticsMetricsForUser(
    allMetrics: List<Pair<String, T>>,
    isGM: Boolean,
): List<Pair<String, T>> =
    if (isGM) allMetrics else allMetrics.filter { (key, _) -> key in analyticsPlayerSafeMetricKeys }
