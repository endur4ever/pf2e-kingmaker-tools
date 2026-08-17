package at.posselt.pfrpg2e.camping

/**
 * Represents the result of a food forecast calculation.
 *
 * @param daysOfFood Number of full days the party can sustain at current consumption.
 *                   Only counts [rations] as durable stock; [provisions] are wiped on rest
 *                   and only cover tonight's meal.
 * @param tonightCovered Whether tonight's meal is covered by current rations + provisions.
 */
data class FoodForecast(
    val daysOfFood: Int,
    val tonightCovered: Boolean,
)

/**
 * Input parameters for food forecasting.
 *
 * @param rations Total rations carried by the party (durable stock, not wiped on rest).
 * @param provisions Total provisions carried by the party (wiped on rest, tonight-only).
 * @param dailyConsumers Number of actors who will eat each day (party members + companions present).
 * @param basicIngredients Basic ingredients carried (durable, like rations).
 * @param specialIngredients Special ingredients carried (durable, like rations).
 * @param mealCostRations Rations per consumer per day. 1 is the RAW plain-meal baseline.
 * @param mealCostBasicIngredients Basic ingredients per consumer per day, from the planned meals.
 * @param mealCostSpecialIngredients Special ingredients per consumer per day, from the planned meals.
 */
data class FoodForecastInput(
    val rations: Int,
    val provisions: Int,
    val dailyConsumers: Int,
    val basicIngredients: Int = 0,
    val specialIngredients: Int = 0,
    val mealCostRations: Int = 1,
    val mealCostBasicIngredients: Int = 0,
    val mealCostSpecialIngredients: Int = 0,
    /**
     * Exact daily totals from the actual meal plan, when the caller has them.
     *
     * The per-consumer costs above assume everyone eats the same thing; the plan allows each
     * character a different recipe, so a caller holding the real plan passes the summed cost here
     * instead of an average that would be wrong for every mixed camp.
     */
    val dailyRationsTotal: Int? = null,
    val dailyBasicIngredientsTotal: Int? = null,
    val dailySpecialIngredientsTotal: Int? = null,
) {
    /** Total rations consumed per day by all consumers. */
    val dailyRationConsumption: Int
        get() = dailyRationsTotal ?: (dailyConsumers * mealCostRations)

    /** Total basic ingredients consumed per day by all consumers. */
    val dailyBasicIngredientsConsumption: Int
        get() = dailyBasicIngredientsTotal ?: (dailyConsumers * mealCostBasicIngredients)

    /** Total special ingredients consumed per day by all consumers. */
    val dailySpecialIngredientsConsumption: Int
        get() = dailySpecialIngredientsTotal ?: (dailyConsumers * mealCostSpecialIngredients)
}

/**
 * Computes a food forecast from the given inputs.
 *
 * Rules (per RAW):
 * - [provisions] are wiped every rest (see [removeProvisions]), so they only count for tonight.
 * - [rations] are the durable stock and determine how many full days the party can sustain.
 * - A "day of food" requires enough rations for all [dailyConsumers] at the current [mealCostRations].
 *
 * @param input Forecast inputs including stock and consumption rates.
 * @return [FoodForecast] with days of food and whether tonight is covered.
 */
fun computeFoodForecast(input: FoodForecastInput): FoodForecast {
    val dailyRationNeed = input.dailyRationConsumption
    // Days are limited by whichever stock runs out FIRST. Cooking a recipe spends ingredients
    // instead of rations, so a forecast that only counted rations would promise days the pantry
    // cannot actually deliver. A resource with no daily need never binds.
    val limits = listOfNotNull(
        dailyRationNeed.takeIf { it > 0 }?.let { input.rations / it },
        input.dailyBasicIngredientsConsumption.takeIf { it > 0 }?.let { input.basicIngredients / it },
        input.dailySpecialIngredientsConsumption.takeIf { it > 0 }?.let { input.specialIngredients / it },
    )
    val daysOfFood = limits.minOrNull() ?: Int.MAX_VALUE // nothing consumed = infinite days

    // Tonight is covered if rations + provisions >= tonight's meal cost for all consumers
    val tonightRationNeed = dailyRationNeed
    val tonightCovered = (input.rations + input.provisions) >= tonightRationNeed

    return FoodForecast(
        daysOfFood = daysOfFood,
        tonightCovered = tonightCovered,
    )
}