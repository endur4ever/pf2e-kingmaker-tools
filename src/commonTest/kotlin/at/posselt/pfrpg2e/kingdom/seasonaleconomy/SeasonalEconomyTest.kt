package at.posselt.pfrpg2e.kingdom.seasonaleconomy

import at.posselt.pfrpg2e.data.regions.Season
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the §3.2 modifier table and the integer-ledger rounding it depends on. */
class SeasonalEconomyTest {
    @Test
    fun theClosedGateIsNeutralForEverySeason() {
        // Default off: a kingdom that never opts in must be arithmetically untouched.
        Season.entries.forEach { season ->
            assertEquals(SeasonalEconomyModifiers.none(), seasonalModifiers(season, enabled = false), "$season")
        }
    }

    @Test
    fun springAndSummerAreNeutralEvenWhenEnabled() {
        // spring's MATH is neutral; its flood OFFER flag is the season's one consequence and is
        // covered by SpringFloodOfferTest, so it is masked here to keep this a statement about math
        // Spring's flood is an offer, not a multiplier; summer's war season is flavour only.
        assertEquals(SeasonalEconomyModifiers.none(), seasonalModifiers(Season.SPRING, enabled = true).copy(springFloodOffer = false))
        assertEquals(SeasonalEconomyModifiers.none(), seasonalModifiers(Season.SUMMER, enabled = true))
    }

    @Test
    fun autumnBoostsFarmlandAndNothingElse() {
        val fall = seasonalModifiers(Season.FALL, enabled = true)
        assertEquals(1.25, fall.farmlandFoodMultiplier)
        assertEquals(1.0, fall.commodityWorksiteMultiplier)
        assertEquals(0, fall.foodConsumptionDelta)
        assertEquals(0, fall.caravanRaidDcDelta)
        assertEquals(0, fall.riverCrossingCostDelta)
    }

    @Test
    fun winterCarriesAllFourPenaltiesAndLeavesFarmlandAlone() {
        val winter = seasonalModifiers(Season.WINTER, enabled = true)
        assertEquals(1.0, winter.farmlandFoodMultiplier)
        assertEquals(0.9, winter.commodityWorksiteMultiplier)
        assertEquals(1, winter.foodConsumptionDelta)
        assertEquals(2, winter.caravanRaidDcDelta)
        assertEquals(-1, winter.riverCrossingCostDelta)
    }

    @Test
    fun theMultiplierRoundsRatherThanTruncates() {
        // 6 food at x1.25 is 7.5: the plan's harvest arithmetic depends on 8, not a floored 7.
        assertEquals(8, applyWorksiteMultiplier(6, 1.25))
        assertEquals(9, applyWorksiteMultiplier(10, 0.9))
        assertEquals(12, applyWorksiteMultiplier(12, 1.0))
        assertEquals(0, applyWorksiteMultiplier(0, 1.25))
    }

    @Test
    fun noMultiplierProducesNegativeGoods() {
        assertEquals(0, applyWorksiteMultiplier(-4, 1.25))
    }
}
