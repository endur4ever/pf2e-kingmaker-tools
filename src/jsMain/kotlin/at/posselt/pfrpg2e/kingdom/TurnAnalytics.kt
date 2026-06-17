package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord

data class GraphicPoint(
    val turn: Int,
    val value: Double,
    val x: Double,
    val y: Double,
)

data class MetricSummary(
    val min: Double,
    val max: Double,
    val current: Double,
    val mean: Double,
    val deltaFromStart: Double,
)

fun extractSeries(history: Array<RawTurnRecord>, metric: String, limit: Int?): List<Pair<Int, Double>> {
    val windowed = if (limit != null && history.size > limit) {
        history.sliceArray((history.size - limit) until history.size)
    } else {
        history
    }
    return windowed.mapNotNull { record ->
        val value = when (metric) {
            "unrest" -> record.unrest.toDouble()
            "resourcePoints" -> record.resourcePoints.toDouble()
            "consumption" -> record.consumption.toDouble()
            "fame" -> record.fame.toDouble()
            "xpAwarded" -> record.xpAwarded?.toDouble()
            "warPressure" -> record.warPressure?.toDouble()
            "level" -> record.level?.toDouble()
            "size" -> record.size?.toDouble()
            "ruinCorruption" -> record.ruinCorruption?.toDouble()
            "ruinCrime" -> record.ruinCrime?.toDouble()
            "ruinDecay" -> record.ruinDecay?.toDouble()
            "ruinStrife" -> record.ruinStrife?.toDouble()
            else -> null
        }
        if (value != null) {
            record.turn to value
        } else {
            null
        }
    }
}

fun summarizeSeries(series: List<Pair<Int, Double>>): MetricSummary? {
    if (series.isEmpty()) return null
    val values = series.map { it.second }
    val min = values.minOrNull() ?: 0.0
    val max = values.maxOrNull() ?: 0.0
    val current = values.last()
    val mean = values.average()
    val deltaFromStart = current - values.first()
    return MetricSummary(
        min = min,
        max = max,
        current = current,
        mean = mean,
        deltaFromStart = deltaFromStart,
    )
}

fun mapSeriesToCoordinates(
    series: List<Pair<Int, Double>>,
    width: Double,
    height: Double,
    padding: Double = 10.0
): List<GraphicPoint> {
    if (series.isEmpty()) return emptyList()
    
    val minVal = series.minOf { it.second }
    val maxVal = series.maxOf { it.second }
    val valRange = maxVal - minVal

    val minTurn = series.minOf { it.first }
    val maxTurn = series.maxOf { it.first }
    val turnRange = (maxTurn - minTurn).toDouble()

    val innerWidth = width - 2 * padding
    val innerHeight = height - 2 * padding

    return series.map { (turn, value) ->
        val x = padding + if (turnRange > 0.0) {
            ((turn - minTurn) / turnRange) * innerWidth
        } else {
            innerWidth / 2.0
        }
        
        // y=0 is top in SVG, so maxVal should map to padding (highest up), minVal to padding + innerHeight (lowest down)
        val y = padding + innerHeight - if (valRange > 0.0) {
            ((value - minVal) / valRange) * innerHeight
        } else {
            // If the series is flat, draw the line in the vertical center of the chart
            innerHeight / 2.0
        }
        GraphicPoint(turn, value, x, y)
    }
}
