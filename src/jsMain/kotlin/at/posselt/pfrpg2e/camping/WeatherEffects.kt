package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.weather.getCurrentWeatherType
import com.foundryvtt.core.Game

/**
 * Whether weather applies mechanical effects. Defaults ON: the deltas are deliberately small, and
 * weather that never bites is the problem this feature exists to solve.
 */
fun CampingData.isWeatherEffectsEnabled(): Boolean = enableWeatherEffects ?: true

/**
 * Today's weather modifiers for this camping sheet, or neutral when the feature is off.
 *
 * Single entry point so the hexploration budget, the encounter DC and the camping check all read
 * the same weather on the same day, and one toggle silences all three.
 */
fun Game.currentWeatherModifiers(camping: CampingData): WeatherModifiers =
    weatherModifiersFor(getCurrentWeatherType(), enabled = camping.isWeatherEffectsEnabled())
