package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.regions.WeatherType

/**
 * Pure weather -> mechanics table: how the day's weather nudges the hexploration-activity budget, the
 * random-encounter DC, and outdoor camping-activity checks. Weather is derived today but only flavors
 * the forecast; these are the modest, deterministic deltas the camping/hexploration flows should
 * apply. Wiring them into the hexploration budget, the encounter roll, and the camping-check
 * breakdown (gated by a `enableWeatherEffects` setting, default on) is the deferred jsMain work.
 *
 * Magnitudes are intentionally small: a -1 activity in a blizzard is noticeable but not crippling, a
 * +1 encounter DC nudges odds without dominating, and a -1/-2 check penalty mirrors existing
 * condition penalties (Fatigued = -1).
 *
 * See card t_07449410.
 */
data class WeatherModifiers(
    /** Change to the hexploration activities available that day (e.g. -1 in snow). */
    val hexplorationActivityDelta: Double = 0.0,
    /** Shift to the random-encounter DC (e.g. +1 in poor visibility). */
    val encounterDcDelta: Int = 0,
    /** Circumstance penalty on outdoor camping-activity checks. */
    val campingCheckPenalty: Int = 0,
)

/** Neutral modifiers — used for clear weather or when the weather-effects feature is disabled. */
val NEUTRAL_WEATHER = WeatherModifiers()

/** The mechanical modifiers for each [WeatherType]. Clear skies are neutral; snow is the harshest. */
val WEATHER_MODIFIER_TABLE: Map<WeatherType, WeatherModifiers> = mapOf(
    WeatherType.SUNNY to NEUTRAL_WEATHER,
    WeatherType.RAINY to WeatherModifiers(
        hexplorationActivityDelta = 0.0,
        encounterDcDelta = 1,          // rain cuts visibility
        campingCheckPenalty = -1,      // a wet camp is harder to keep
    ),
    WeatherType.COLD to WeatherModifiers(
        hexplorationActivityDelta = 0.0,
        encounterDcDelta = 0,
        campingCheckPenalty = -1,      // cold saps outdoor work
    ),
    WeatherType.SNOWY to WeatherModifiers(
        hexplorationActivityDelta = -1.0,  // trudging through snow costs a hexploration activity
        encounterDcDelta = 1,
        campingCheckPenalty = -2,          // the harshest conditions
    ),
)

/**
 * The modifiers to apply for [type]. When [enabled] is false (the feature is off) the result is
 * always [NEUTRAL_WEATHER] so weather stays purely cosmetic.
 */
fun weatherModifiersFor(type: WeatherType, enabled: Boolean = true): WeatherModifiers =
    if (!enabled) NEUTRAL_WEATHER else WEATHER_MODIFIER_TABLE[type] ?: NEUTRAL_WEATHER
