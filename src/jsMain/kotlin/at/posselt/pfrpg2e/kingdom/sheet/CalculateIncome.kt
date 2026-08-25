package at.posselt.pfrpg2e.kingdom.sheet

import at.posselt.pfrpg2e.data.kingdom.RealmData
import at.posselt.pfrpg2e.data.kingdom.settlements.Settlement
import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementSizeType
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.seasonaleconomy.SeasonalEconomyModifiers
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
    seasonal: SeasonalEconomyModifiers = SeasonalEconomyModifiers.none(),
): Income {
    val income = calculateIncome(
        realmData = realmData,
        resourceDice = resourceDice,
        increaseGainedLuxuries = increaseGainedLuxuries,
        seasonal = seasonal,
    )
    val withModifiers = income.plusModifierCommodities(modifiers, expressionContext)
    val rolledRp = roll(income.resourcePointsFormula, flavor = t("kingdom.gainingResourcePoints"), toChat = !suppressChat)
    if (!suppressChat) {
        postChatTemplate(
            templatePath = "chatmessages/collect-resources.hbs",
            templateContext = CollectResources(
                rp = rolledRp,
                ore = withModifiers.ore,
                stone = withModifiers.stone,
                lumber = withModifiers.lumber,
                luxuries = withModifiers.luxuries,
            ),
        )
    }
    // The modifier-sourced commodities computed above must be GRANTED, not just announced. They
    // were folded into the chat card only, so a structure or feat granting "+2 ore per turn"
    // reported the gain and reported it in the Turn-tab projection (which does add them), while
    // the kingdom received nothing — and the projection could never match the real collection.
    return withModifiers
        .copy(
            resourcePoints = withModifiers.resourcePoints + rolledRp + kingdomData.resourcePoints.now,
            ore = withModifiers.ore + kingdomData.commodities.now.ore,
            lumber = withModifiers.lumber + kingdomData.commodities.now.lumber,
            luxuries = withModifiers.luxuries + kingdomData.commodities.now.luxuries,
            stone = withModifiers.stone + kingdomData.commodities.now.stone,
            resourceDice = 0,
        )
        .limitBy(calculateStorage(realmData, settlements))
}

/**
 * Adds the modifier-sourced commodity income (structures, feats, and anything else selecting
 * ORE/STONE/LUMBER) onto a base [Income].
 *
 * Shared by the real collection and the Turn-tab projection ON PURPOSE: the two used to compute
 * these separately, and the collection folded them into its chat card only, so the projection
 * promised modifier commodities the kingdom never actually received. Routing both through one
 * function makes that divergence impossible rather than merely tested-against.
 */
fun Income.plusModifierCommodities(
    modifiers: List<Modifier>,
    expressionContext: ExpressionContext,
): Income = copy(
    ore = ore + calculateModifierResource(modifiers, expressionContext, ModifierSelector.ORE),
    stone = stone + calculateModifierResource(modifiers, expressionContext, ModifierSelector.STONE),
    lumber = lumber + calculateModifierResource(modifiers, expressionContext, ModifierSelector.LUMBER),
)

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
    seasonal: SeasonalEconomyModifiers = SeasonalEconomyModifiers.none(),
): ProjectedResources {
    val resourceDice = kingdomData.getResourceDiceAmount(
        chosenFeats,
        settlements,
        kingdomLevel = kingdomData.level,
        includeCurrent = false,
    )
    // Mirrors the collection path's gate so the forecast does not promise a bonus already spent.
    val increaseGainedLuxuries = if (kingdomData.luxuryBonusUsedThisTurn == true) {
        0
    } else {
        chosenFeats.sumOf { it.feat.increaseGainedLuxuriesOncePerTurnBy ?: 0 }
    }
    val baseIncome = calculateIncome(
        realmData = realmData,
        resourceDice = resourceDice,
        increaseGainedLuxuries = increaseGainedLuxuries,
        seasonal = seasonal,
    )
    val uncappedIncome = baseIncome.plusModifierCommodities(modifiers, expressionContext)
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
