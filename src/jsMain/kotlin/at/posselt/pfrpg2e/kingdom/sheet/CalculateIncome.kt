package at.posselt.pfrpg2e.kingdom.sheet

import at.posselt.pfrpg2e.data.kingdom.RealmData
import at.posselt.pfrpg2e.data.kingdom.settlements.Settlement
import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementSizeType
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.ChosenFeat
import at.posselt.pfrpg2e.kingdom.modifiers.Modifier
import at.posselt.pfrpg2e.kingdom.modifiers.ModifierSelector
import at.posselt.pfrpg2e.kingdom.modifiers.evaluation.evaluateModifiers
import at.posselt.pfrpg2e.kingdom.modifiers.evaluation.filterModifiersAndUpdateContext
import at.posselt.pfrpg2e.kingdom.modifiers.expressions.ExpressionContext
import at.posselt.pfrpg2e.kingdom.resources.Income
import at.posselt.pfrpg2e.kingdom.resources.calculateIncome
import at.posselt.pfrpg2e.kingdom.resources.calculateStorage
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.roll
import at.posselt.pfrpg2e.utils.t
import kotlinx.js.JsPlainObject
import kotlin.math.max
import kotlin.math.min

@Suppress("unused")
@JsPlainObject
external interface CollectResources {
    val rp: Int
    val ore: Int
    val stone: Int
    val lumber: Int
    val luxuries: Int
}

/**
 * The automated resource gain for the following turn, including whether storage reduced any
 * commodity gain. [income] remains a delta: it is added to the current commodity totals at the
 * end of the turn.
 */
data class ProjectedResources(
    val income: Income,
    val oreCappedByStorage: Boolean,
    val stoneCappedByStorage: Boolean,
    val lumberCappedByStorage: Boolean,
    val luxuriesCappedByStorage: Boolean,
)

suspend fun collectResources(
    kingdomData: KingdomData,
    realmData: RealmData,
    resourceDice: Int,
    increaseGainedLuxuries: Int,
    settlements: List<Settlement>,
    expressionContext: ExpressionContext,
    modifiers: List<Modifier>,
    suppressChat: Boolean = false,
): Income {
    val income = calculateIncome(
        realmData = realmData,
        resourceDice = resourceDice,
        increaseGainedLuxuries = increaseGainedLuxuries,
    )
    val ore = calculateModifierResource(modifiers, expressionContext, ModifierSelector.ORE)
    val stone = calculateModifierResource(modifiers, expressionContext, ModifierSelector.STONE)
    val lumber = calculateModifierResource(modifiers, expressionContext, ModifierSelector.LUMBER)
    val rolledRp = roll(income.resourcePointsFormula, flavor = t("kingdom.gainingResourcePoints"), toChat = !suppressChat)
    if (!suppressChat) {
        postChatTemplate(
            templatePath = "chatmessages/collect-resources.hbs",
            templateContext = CollectResources(
                rp = rolledRp,
                ore = income.ore + ore,
                stone = income.stone + stone,
                lumber = income.lumber + lumber,
                luxuries = income.luxuries,
            ),
        )
    }
    return income
        .copy(
            resourcePoints = income.resourcePoints + rolledRp + kingdomData.resourcePoints.now,
            ore = income.ore + kingdomData.commodities.now.ore,
            lumber = income.lumber + kingdomData.commodities.now.lumber,
            luxuries = income.luxuries + kingdomData.commodities.now.luxuries,
            stone = income.stone + kingdomData.commodities.now.stone,
            resourceDice = 0,
        )
        .limitBy(calculateStorage(realmData, settlements))
}

private fun calculateModifierResource(
    modifiers: List<Modifier>,
    expressionContext: ExpressionContext,
    selector: ModifierSelector
): Int = evaluateModifiers(filterModifiersAndUpdateContext(modifiers, expressionContext, selector)).total

fun KingdomData.getResourceDiceAmount(
    allFeats: List<ChosenFeat>,
    settlements: List<Settlement>,
    kingdomLevel: Int,
    // The dice you currently hold are rolled and spent during Collect Resources, so a *next-turn*
    // projection must not fold them in — otherwise the carried-over value compounds each turn.
    includeCurrent: Boolean = true,
) = 4 +
        kingdomLevel +
        allFeats.sumOf { it.feat.resourceDice ?: 0 } +
        (if (includeCurrent) resourceDice.now else 0) +
        bonusResourceDice +
        if (settings.settlementsGenerateRd) {
            settlements.sumOf {
                when (it.size.type) {
                    SettlementSizeType.VILLAGE -> 0
                    SettlementSizeType.TOWN -> min(it.maximumCivicRdLimit, 1)
                    SettlementSizeType.CITY -> max(1, min(it.maximumCivicRdLimit, 2))
                    SettlementSizeType.METROPOLIS -> max(1, min(it.maximumCivicRdLimit, 4))
                }
            }
        } else {
            0
        }

fun calculateProjectedResources(
    kingdomData: KingdomData,
    realmData: RealmData,
    chosenFeats: List<ChosenFeat>,
    settlements: List<Settlement>,
    expressionContext: ExpressionContext,
    modifiers: List<Modifier>,
): ProjectedResources {
    val resourceDice = kingdomData.getResourceDiceAmount(
        chosenFeats,
        settlements,
        kingdomLevel = kingdomData.level,
        includeCurrent = false,
    )
    val increaseGainedLuxuries = chosenFeats.sumOf { it.feat.increaseGainedLuxuriesOncePerTurnBy ?: 0 }
    val baseIncome = calculateIncome(
        realmData = realmData,
        resourceDice = resourceDice,
        increaseGainedLuxuries = increaseGainedLuxuries,
    )
    val ore = calculateModifierResource(modifiers, expressionContext, ModifierSelector.ORE)
    val stone = calculateModifierResource(modifiers, expressionContext, ModifierSelector.STONE)
    val lumber = calculateModifierResource(modifiers, expressionContext, ModifierSelector.LUMBER)
    val uncappedIncome = baseIncome.copy(
        ore = baseIncome.ore + ore,
        stone = baseIncome.stone + stone,
        lumber = baseIncome.lumber + lumber,
    )
    val currentCommodities = kingdomData.commodities.now
    val cappedTotals = uncappedIncome.copy(
        ore = uncappedIncome.ore + currentCommodities.ore,
        stone = uncappedIncome.stone + currentCommodities.stone,
        lumber = uncappedIncome.lumber + currentCommodities.lumber,
        luxuries = uncappedIncome.luxuries + currentCommodities.luxuries,
    ).limitBy(calculateStorage(realmData, settlements))
    val cappedIncome = uncappedIncome.copy(
        ore = cappedTotals.ore - currentCommodities.ore,
        stone = cappedTotals.stone - currentCommodities.stone,
        lumber = cappedTotals.lumber - currentCommodities.lumber,
        luxuries = cappedTotals.luxuries - currentCommodities.luxuries,
    )

    return ProjectedResources(
        income = cappedIncome,
        oreCappedByStorage = cappedIncome.ore != uncappedIncome.ore,
        stoneCappedByStorage = cappedIncome.stone != uncappedIncome.stone,
        lumberCappedByStorage = cappedIncome.lumber != uncappedIncome.lumber,
        luxuriesCappedByStorage = cappedIncome.luxuries != uncappedIncome.luxuries,
    )
}
