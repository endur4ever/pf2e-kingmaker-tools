package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FoodForecastTest {
    @Test
    fun rationsDivideByDailyConsumptionForDaysOfFood() {
        // 4 consumers x 1 ration/meal = 4 rations/day; 12 rations -> 3 full days.
        val f = computeFoodForecast(FoodForecastInput(rations = 12, provisions = 0, dailyConsumers = 4))
        assertEquals(3, f.daysOfFood)
    }

    @Test
    fun provisionsDoNotExtendDaysButCoverTonight() {
        // Provisions are wiped on rest, so they never add durable days...
        val f = computeFoodForecast(FoodForecastInput(rations = 0, provisions = 4, dailyConsumers = 4))
        assertEquals(0, f.daysOfFood)
        // ...but they do cover tonight's meal.
        assertTrue(f.tonightCovered)
    }

    @Test
    fun tonightUncoveredWhenStockBelowNightlyNeed() {
        val f = computeFoodForecast(FoodForecastInput(rations = 2, provisions = 1, dailyConsumers = 4))
        assertFalse(f.tonightCovered)  // 2 + 1 = 3 < 4 needed
        assertEquals(0, f.daysOfFood)  // 2 / 4 = 0 full days
    }

    @Test
    fun integerDivisionRoundsDownPartialDays() {
        // 10 rations, 4 consumers -> 2 full days (8 used), remainder can't feed everyone a 3rd day.
        val f = computeFoodForecast(FoodForecastInput(rations = 10, provisions = 0, dailyConsumers = 4))
        assertEquals(2, f.daysOfFood)
    }

    @Test
    fun companionPresenceRaisesConsumptionAndShortensForecast() {
        val soloParty = computeFoodForecast(FoodForecastInput(rations = 12, provisions = 0, dailyConsumers = 3))
        val withCompanion = computeFoodForecast(FoodForecastInput(rations = 12, provisions = 0, dailyConsumers = 4))
        assertEquals(4, soloParty.daysOfFood)
        assertEquals(3, withCompanion.daysOfFood)
        assertTrue(withCompanion.daysOfFood < soloParty.daysOfFood)
    }

    @Test
    fun higherMealCostShortensForecast() {
        val f = computeFoodForecast(
            FoodForecastInput(rations = 12, provisions = 0, dailyConsumers = 2, mealCostRations = 2),
        )
        assertEquals(3, f.daysOfFood)  // 2 consumers x 2 = 4/day; 12/4 = 3
    }

    @Test
    fun noConsumersMeansNothingToForecast() {
        val f = computeFoodForecast(FoodForecastInput(rations = 5, provisions = 5, dailyConsumers = 0))
        assertEquals(Int.MAX_VALUE, f.daysOfFood)
        assertTrue(f.tonightCovered)
    }
}
