package at.posselt.pfrpg2e.kingdom.seasonaleconomy

import at.posselt.kingdom.modifiers.evaluation.defaultContext
import at.posselt.pfrpg2e.data.kingdom.RealmData
import at.posselt.pfrpg2e.data.regions.Season
import at.posselt.pfrpg2e.kingdom.resources.calculateConsumption
import at.posselt.pfrpg2e.kingdom.resources.calculateIncome
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the two commonMain seams the seasonal table threads through (plan §6 seams 1 and 2). The
 * default parameter is the guarantee that matters most: every caller that does not pass modifiers
 * gets EXACTLY the pre-seasonal arithmetic.
 */
class SeasonalSeamsTest {
    private fun realm(
        farmQuantity: Int = 2,
        farmFood: Int = 6,
        lumber: Int = 10,
        ore: Int = 4,
        stone: Int = 3,
    ) = RealmData(
        size = 10,
        worksites = RealmData.WorkSites(
            farmlands = RealmData.WorkSite(quantity = farmQuantity, resources = farmFood),
            lumberCamps = RealmData.WorkSite(quantity = lumber, resources = 0),
            mines = RealmData.WorkSite(quantity = ore, resources = 0),
            quarries = RealmData.WorkSite(quantity = stone, resources = 0),
            // 6, not 2: 2 x 0.9 rounds back to 2, so a mutant that slows luxuries too would
            // survive a small fixture. 6 x 0.9 = 5.4 rounds to 5 and is distinguishable.
            luxurySources = RealmData.WorkSite(quantity = 6, resources = 0),
        ),
    )

    @Test
    fun theDefaultSeasonalParameterLeavesIncomeExactlyAsBefore() {
        val without = calculateIncome(realm(), resourceDice = 5, increaseGainedLuxuries = 0)
        val explicit = calculateIncome(
            realm(), resourceDice = 5, increaseGainedLuxuries = 0,
            seasonal = SeasonalEconomyModifiers.none(),
        )
        assertEquals(without, explicit)
        assertEquals(10, without.lumber)
        assertEquals(4, without.ore)
        assertEquals(3, without.stone)
    }

    @Test
    fun winterSlowsExtractionWorksitesAndLeavesLuxuriesAlone() {
        val winter = seasonalModifiers(Season.WINTER, enabled = true)
        val income = calculateIncome(realm(), resourceDice = 5, increaseGainedLuxuries = 0, seasonal = winter)
        assertEquals(9, income.lumber, "10 lumber x 0.9 rounds to 9")
        assertEquals(4, income.ore, "4 x 0.9 = 3.6 rounds to 4")
        assertEquals(3, income.stone, "3 x 0.9 = 2.7 rounds to 3")
        assertEquals(6, income.luxuries, "traders keep working in winter")
    }

    @Test
    fun autumnDoesNotTouchExtractionIncome() {
        val fall = seasonalModifiers(Season.FALL, enabled = true)
        val income = calculateIncome(realm(), resourceDice = 5, increaseGainedLuxuries = 0, seasonal = fall)
        assertEquals(10, income.lumber)
    }

    @Test
    fun autumnHarvestScalesFarmlandFoodInConsumption() {
        // 6 food x 1.25 = 7.5 rounds to 8: two fewer net consumption than the neutral season.
        val fall = seasonalModifiers(Season.FALL, enabled = true)
        val neutral = calculateConsumption(
            emptyList(), realm(), armyConsumption = 0, now = 12,
            expressionContext = defaultContext, modifiers = emptyList(),
        )
        val harvest = calculateConsumption(
            emptyList(), realm(), armyConsumption = 0, now = 12,
            expressionContext = defaultContext, modifiers = emptyList(), seasonal = fall,
        )
        assertEquals(8, harvest.food)
        assertEquals(neutral.total - 2, harvest.total)
    }

    @Test
    fun winterAddsOneFlatConsumer() {
        val winter = seasonalModifiers(Season.WINTER, enabled = true)
        val neutral = calculateConsumption(
            emptyList(), realm(), armyConsumption = 0, now = 12,
            expressionContext = defaultContext, modifiers = emptyList(),
        )
        val cold = calculateConsumption(
            emptyList(), realm(), armyConsumption = 0, now = 12,
            expressionContext = defaultContext, modifiers = emptyList(), seasonal = winter,
        )
        assertEquals(neutral.total + 1, cold.total)
        assertEquals(6, cold.food, "winter does not touch farmland food")
    }

    @Test
    fun aDisabledGateIsNeutralThroughBothSeams() {
        val off = seasonalModifiers(Season.WINTER, enabled = false)
        assertEquals(
            calculateIncome(realm(), 5, 0),
            calculateIncome(realm(), 5, 0, seasonal = off),
        )
        assertEquals(
            calculateConsumption(emptyList(), realm(), 0, 12, defaultContext, emptyList()),
            calculateConsumption(emptyList(), realm(), 0, 12, defaultContext, emptyList(), seasonal = off),
        )
    }
}
