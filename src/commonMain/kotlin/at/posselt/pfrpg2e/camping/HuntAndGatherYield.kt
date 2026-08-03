package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.regions.Season
import at.posselt.pfrpg2e.data.regions.Terrain
import at.posselt.pfrpg2e.data.regions.WeatherType

/**
 * Whether a forage came out ahead, behind, or about even versus the flat baseline — drives the
 * flavor fragment on the Hunt & Gather chat card.
 */
enum class ForagingTrend { BOUNTIFUL, LEAN, NEUTRAL }

/**
 * Result of [foragingYieldModifier]: a [multiplier] applied to the SUCCESS/critical-success basic
 * and special ingredient yields, plus a [trend] classification for chat flavor.
 */
data class ForagingModifier(
    val multiplier: Double,
    val trend: ForagingTrend,
) {
    companion object {
        /** No modification — used when the feature toggle is off or inputs are unknown. */
        val NEUTRAL = ForagingModifier(1.0, ForagingTrend.NEUTRAL)
    }
}

/**
 * Pure foraging-yield modifier from the party's surroundings. Forest/swamp/aquatic hexes forage
 * richer; desert/mountain hexes forage leaner; high summer helps and deep winter hurts; wet/cold/
 * snowy weather progressively hurts (snow is the "severe weather" penalty). Factors combine
 * multiplicatively and the result is clamped to ±50% so a single bad day can never zero out a forage
 * nor a good one balloon it.
 *
 * All inputs are nullable: a null terrain/season/weather contributes a neutral factor, so callers
 * that can't resolve one dimension (no calendar, unknown region terrain) still get a sensible result.
 */
fun foragingYieldModifier(
    terrain: Terrain?,
    season: Season?,
    weather: WeatherType?,
): ForagingModifier {
    val terrainFactor = when (terrain) {
        Terrain.FOREST, Terrain.SWAMP, Terrain.AQUATIC -> 1.25
        Terrain.DESERT, Terrain.MOUNTAIN -> 0.75
        else -> 1.0
    }
    val seasonFactor = when (season) {
        Season.SUMMER -> 1.1
        Season.WINTER -> 0.8
        else -> 1.0
    }
    val weatherFactor = when (weather) {
        WeatherType.SNOWY -> 0.75   // severe-weather penalty
        WeatherType.COLD -> 0.85
        WeatherType.RAINY -> 0.9
        else -> 1.0
    }
    val raw = terrainFactor * seasonFactor * weatherFactor
    val multiplier = raw.coerceIn(0.5, 1.5)
    val trend = when {
        multiplier >= 1.1 -> ForagingTrend.BOUNTIFUL
        multiplier <= 0.9 -> ForagingTrend.LEAN
        else -> ForagingTrend.NEUTRAL
    }
    return ForagingModifier(multiplier, trend)
}

/**
 * Applies the foraging [multiplier][ForagingModifier.multiplier] to a raw ingredient [yield],
 * rounding to the nearest whole ingredient (a positive yield never rounds down to zero, so a rich
 * hex can't accidentally erase a small forage).
 */
fun applyForagingModifier(yield: Int, modifier: ForagingModifier): Int {
    if (yield <= 0 || modifier.multiplier == 1.0) return yield
    val scaled = yield * modifier.multiplier
    // Round half up (kotlin.math.round is half-to-even, which would surprise on e.g. 12.5).
    val rounded = kotlin.math.floor(scaled + 0.5).toInt()
    return maxOf(1, rounded)
}
