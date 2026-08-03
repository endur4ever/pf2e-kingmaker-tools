package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.regions.WeatherType
import kotlin.test.Test
import kotlin.test.assertEquals

class WeatherModifiersTest {
    @Test
    fun sunnyIsNeutral() {
        assertEquals(NEUTRAL_WEATHER, weatherModifiersFor(WeatherType.SUNNY))
    }

    @Test
    fun snowIsTheHarshest() {
        val snow = weatherModifiersFor(WeatherType.SNOWY)
        assertEquals(-1.0, snow.hexplorationActivityDelta)
        assertEquals(1, snow.encounterDcDelta)
        assertEquals(-2, snow.campingCheckPenalty)
    }

    @Test
    fun rainCutsVisibilityAndDampensCamp() {
        val rain = weatherModifiersFor(WeatherType.RAINY)
        assertEquals(0.0, rain.hexplorationActivityDelta)
        assertEquals(1, rain.encounterDcDelta)
        assertEquals(-1, rain.campingCheckPenalty)
    }

    @Test
    fun coldOnlyPenalizesCampChecks() {
        val cold = weatherModifiersFor(WeatherType.COLD)
        assertEquals(0.0, cold.hexplorationActivityDelta)
        assertEquals(0, cold.encounterDcDelta)
        assertEquals(-1, cold.campingCheckPenalty)
    }

    @Test
    fun disablingTheFeatureAlwaysYieldsNeutral() {
        assertEquals(NEUTRAL_WEATHER, weatherModifiersFor(WeatherType.SNOWY, enabled = false))
        assertEquals(NEUTRAL_WEATHER, weatherModifiersFor(WeatherType.RAINY, enabled = false))
    }

    @Test
    fun everyWeatherTypeHasATableEntry() {
        WeatherType.entries.forEach { type ->
            // no exceptions, and disabled path is always neutral
            weatherModifiersFor(type)
        }
        assertEquals(WeatherType.entries.size, WEATHER_MODIFIER_TABLE.size)
    }
}
