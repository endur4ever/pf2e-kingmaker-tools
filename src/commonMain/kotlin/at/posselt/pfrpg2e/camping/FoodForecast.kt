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
 * @param mealCostRations Rations cost per meal per consumer (from [RecipeData.cookingCost]).
 * @param mealCostBasicIngredients Basic ingredients cost per meal per consumer.
 * @param mealCostSpecialIngredients Special ingredients cost per meal per consumer.
 */
data class FoodForecastInput(
    val rations: Int,
    val provisions: Int,
    val dailyConsumers: Int,
    val mealCostRations: Int = 1,
    val mealCostBasicIngredients: Int = 0,
    val mealCostSpecialIngredients: Int = 0,
) {
    /** Total rations consumed per day by all consumers. */
    val dailyRationConsumption: Int
        get() = dailyConsumers * mealCostRations

    /** Total basic ingredients consumed per day by all consumers. */
    val dailyBasicIngredientsConsumption: Int
        get() = dailyConsumers * mealCostBasicIngredients

    /** Total special ingredients consumed per day by all consumers. */
    val dailySpecialIngredientsConsumption: Int
        get() = dailyConsumers * mealCostSpecialIngredients
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
    val daysOfFood = if (dailyRationNeed > 0) {
        input.rations / dailyRationNeed
    } else {
        Int.MAX_VALUE // No consumption = infinite days
    }

    // Tonight is covered if rations + provisions >= tonight's meal cost for all consumers
    val tonightRationNeed = dailyRationNeed
    val tonightCovered = (input.rations + input.provisions) >= tonightRationNeed

    return FoodForecast(
        daysOfFood = daysOfFood,
        tonightCovered = tonightCovered,
    )
}