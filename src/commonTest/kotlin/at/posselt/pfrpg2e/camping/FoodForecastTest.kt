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

    @Test
    fun ingredientsCanBindBeforeRationsDo() {
        // A camp cooking recipes burns ingredients, not rations. Counting only rations would
        // promise days the pantry cannot deliver -- the exact starvation-mid-route this warns about.
        val f = computeFoodForecast(
            FoodForecastInput(
                rations = 100,
                provisions = 0,
                dailyConsumers = 4,
                basicIngredients = 6,
                specialIngredients = 99,
                mealCostRations = 0,
                mealCostBasicIngredients = 2,
            )
        )
        // 6 basic / (4 consumers x 2) = 0 full days, despite 100 rations sitting there.
        assertEquals(0, f.daysOfFood)
    }

    @Test
    fun theScarcestStockDecidesTheForecast() {
        val f = computeFoodForecast(
            FoodForecastInput(
                rations = 40, provisions = 0, dailyConsumers = 2,
                basicIngredients = 20, specialIngredients = 3,
                mealCostRations = 1, mealCostBasicIngredients = 1, mealCostSpecialIngredients = 1,
            )
        )
        // rations 40/2=20, basic 20/2=10, special 3/2=1 -> special binds.
        assertEquals(1, f.daysOfFood)
    }

    @Test
    fun aStockWithNoDailyNeedNeverBinds() {
        // Zero special ingredients must not read as "zero days" when no meal uses them.
        val f = computeFoodForecast(
            FoodForecastInput(
                rations = 10, provisions = 0, dailyConsumers = 2,
                basicIngredients = 0, specialIngredients = 0,
                mealCostRations = 1,
            )
        )
        assertEquals(5, f.daysOfFood)
    }

    @Test
    fun anExactPlanTotalOverridesThePerConsumerEstimate() {
        // Characters may each eat something different, so a caller holding the real plan passes the
        // summed cost rather than an average that would be wrong for every mixed camp.
        val f = computeFoodForecast(
            FoodForecastInput(
                rations = 0, provisions = 0, dailyConsumers = 4,
                basicIngredients = 12,
                mealCostRations = 1,
                dailyRationsTotal = 0,
                dailyBasicIngredientsTotal = 3,
            )
        )
        // Rations need 0 (nobody eats plain), basic 12/3 = 4 days.
        assertEquals(4, f.daysOfFood)
    }
}
