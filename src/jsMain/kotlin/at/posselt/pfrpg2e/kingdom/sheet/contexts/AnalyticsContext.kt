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
