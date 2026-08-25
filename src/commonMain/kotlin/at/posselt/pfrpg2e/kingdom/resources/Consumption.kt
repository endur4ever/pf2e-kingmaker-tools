package at.posselt.pfrpg2e.kingdom.resources

import at.posselt.pfrpg2e.data.kingdom.RealmData
import at.posselt.pfrpg2e.kingdom.seasonaleconomy.SeasonalEconomyModifiers
import at.posselt.pfrpg2e.kingdom.seasonaleconomy.applyWorksiteMultiplier
import at.posselt.pfrpg2e.data.kingdom.settlements.Settlement
import at.posselt.pfrpg2e.kingdom.modifiers.Modifier
import at.posselt.pfrpg2e.kingdom.modifiers.ModifierSelector
import at.posselt.pfrpg2e.kingdom.modifiers.evaluation.evaluateModifiers
import at.posselt.pfrpg2e.kingdom.modifiers.evaluation.filterModifiersAndUpdateContext
import at.posselt.pfrpg2e.kingdom.modifiers.expressions.ExpressionContext

data class Consumption(
    val now: Int,
    val total: Int,
    val settlementSurplus: Int,
    val farmlands: Int,
    val food: Int,
    val armies: Int,
    val settlements: Int,
    val resourcesSurplus: Int,
    val modifierConsumption: Int,
)

fun calculateConsumption(
    settlements: List<Settlement>,
    realmData: RealmData,
    armyConsumption: Int,
    now: Int,
    expressionContext: ExpressionContext,
    modifiers: List<Modifier>,
    seasonal: SeasonalEconomyModifiers = SeasonalEconomyModifiers.none(),
): Consumption {
    val modifiers = filterModifiersAndUpdateContext(modifiers, expressionContext, ModifierSelector.CONSUMPTION)
    val result = evaluateModifiers(modifiers)
    val modifierConsumption = result.total
    val farmlands = realmData.worksites.farmlands.quantity
    // Autumn scales what farmland FOOD is worth (§3.2 harvest); winter adds one flat consumer.
    // Both are neutral unless the profile gate is open.
    val food = applyWorksiteMultiplier(realmData.worksites.farmlands.resources, seasonal.farmlandFoodMultiplier)
    val settlementConsumption = settlements.sumOf { it.consumption }
    val settlementSurplus = settlements.sumOf { it.consumptionSurplus }
    val consumers = now + settlementConsumption + armyConsumption + modifierConsumption +
        seasonal.foodConsumptionDelta
    val total = (consumers - food - farmlands).coerceIn(0, Int.MAX_VALUE)
    val resourcesSurplus = (farmlands + food - consumers).coerceIn(0, Int.MAX_VALUE)
    return Consumption(
        total = total,
        resourcesSurplus = resourcesSurplus,
        settlementSurplus = settlementSurplus,
        farmlands = farmlands,
        food = food,
        armies = armyConsumption,
        settlements = settlementConsumption,
        now = now,
        modifierConsumption = modifierConsumption,
    )
}