package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.data.regions.Season
import at.posselt.pfrpg2e.data.regions.Terrain
import at.posselt.pfrpg2e.data.regions.WeatherType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HuntAndGatherYieldTest {
    @Test
    fun allNullInputsAreNeutral() {
        val m = foragingYieldModifier(null, null, null)
        assertEquals(1.0, m.multiplier)
        assertEquals(ForagingTrend.NEUTRAL, m.trend)
    }

    @Test
    fun richTerrainInSummerIsBountiful() {
        val m = foragingYieldModifier(Terrain.FOREST, Season.SUMMER, WeatherType.SUNNY)
        assertTrue(m.multiplier > 1.0, "forest+summer should exceed baseline")
        assertEquals(ForagingTrend.BOUNTIFUL, m.trend)
    }

    @Test
    fun poorTerrainInWinterSnowIsLeanAndClamped() {
        val m = foragingYieldModifier(Terrain.DESERT, Season.WINTER, WeatherType.SNOWY)
        // 0.75 * 0.8 * 0.75 = 0.45 -> clamped to the 0.5 floor.
        assertEquals(0.5, m.multiplier)
        assertEquals(ForagingTrend.LEAN, m.trend)
    }

    @Test
    fun neverExceedsFiftyPercentEitherWay() {
        for (terrain in Terrain.entries) {
            for (season in Season.entries) {
                for (weather in WeatherType.entries) {
                    val m = foragingYieldModifier(terrain, season, weather).multiplier
                    assertTrue(m in 0.5..1.5, "multiplier $m out of range for $terrain/$season/$weather")
                }
            }
        }
    }

    @Test
    fun neutralTerrainWeatherIsFlat() {
        val m = foragingYieldModifier(Terrain.PLAINS, Season.SPRING, WeatherType.SUNNY)
        assertEquals(1.0, m.multiplier)
        assertEquals(ForagingTrend.NEUTRAL, m.trend)
    }

    @Test
    fun snowIsHarsherThanRain() {
        val snow = foragingYieldModifier(Terrain.PLAINS, Season.FALL, WeatherType.SNOWY).multiplier
        val rain = foragingYieldModifier(Terrain.PLAINS, Season.FALL, WeatherType.RAINY).multiplier
        assertTrue(snow < rain, "snow ($snow) should forage worse than rain ($rain)")
    }

    @Test
    fun applyRoundsAndNeverZeroesAPositiveYield() {
        // 1 special ingredient in a lean 0.5 hex still yields at least 1.
        assertEquals(1, applyForagingModifier(1, ForagingModifier(0.5, ForagingTrend.LEAN)))
        // 10 basic * 1.25 = 12.5 -> 13 (round half up).
        assertEquals(13, applyForagingModifier(10, ForagingModifier(1.25, ForagingTrend.BOUNTIFUL)))
        // Zero stays zero.
        assertEquals(0, applyForagingModifier(0, ForagingModifier(1.25, ForagingTrend.BOUNTIFUL)))
        // Neutral is a no-op.
        assertEquals(7, applyForagingModifier(7, ForagingModifier.NEUTRAL))
    }

    @Test
    fun aSuccessfulForageNeverYieldsLessBasicThanAFailedOne() {
        // The inversion this rule exists to prevent. Failure's basic yield was a flat regionDc while
        // success was scaled, so on a lean day (clamped to 0.5x) a SUCCESS returned HALF of a
        // FAILURE -- a party on a snowy winter desert day was better off botching the check.
        // Table-driven over every terrain x season x weather combination, at several region DCs.
        for (terrain in Terrain.entries) {
            for (season in Season.entries) {
                for (weather in WeatherType.entries) {
                    val mod = foragingYieldModifier(terrain, season, weather)
                    for (dc in listOf(1, 4, 12, 20, 35)) {
                        val success = basicForageYield(DegreeOfSuccess.SUCCESS, dc, mod)
                        val failure = basicForageYield(DegreeOfSuccess.FAILURE, dc, mod)
                        assertTrue(
                            success >= failure,
                            "success ($success) < failure ($failure) for $terrain/$season/$weather at dc $dc",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun aCriticalSuccessAlwaysBeatsAPlainOne() {
        for (terrain in Terrain.entries) {
            for (weather in WeatherType.entries) {
                val mod = foragingYieldModifier(terrain, Season.WINTER, weather)
                for (dc in listOf(1, 12, 35)) {
                    assertTrue(
                        basicForageYield(DegreeOfSuccess.CRITICAL_SUCCESS, dc, mod) >=
                            basicForageYield(DegreeOfSuccess.SUCCESS, dc, mod),
                        "crit success below success for $terrain/$weather at dc $dc",
                    )
                }
            }
        }
    }

    @Test
    fun conditionsScaleEveryDegreeByTheSameRule() {
        // Basic yield is equal on success and failure by design; a success is distinguished by the
        // special ingredients it also returns. Any future change that scales one degree but not
        // another reintroduces the inversion.
        val lean = foragingYieldModifier(Terrain.DESERT, Season.WINTER, WeatherType.SNOWY)
        assertEquals(
            basicForageYield(DegreeOfSuccess.SUCCESS, 12, lean),
            basicForageYield(DegreeOfSuccess.FAILURE, 12, lean),
        )
    }
}
