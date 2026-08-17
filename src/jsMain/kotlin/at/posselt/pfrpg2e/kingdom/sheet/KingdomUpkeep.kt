package at.posselt.pfrpg2e.kingdom.sheet

import com.foundryvtt.core.Game
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.getChosenFeatures
import at.posselt.pfrpg2e.kingdom.getExplodedFeatures
import at.posselt.pfrpg2e.kingdom.data.getChosenFeats
import at.posselt.pfrpg2e.kingdom.resources.Income
import at.posselt.pfrpg2e.kingdom.getAllSettlements
import at.posselt.pfrpg2e.kingdom.getRealmData
import at.posselt.pfrpg2e.kingdom.createSimpleContext
import at.posselt.pfrpg2e.kingdom.createModifiers
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.getAppFlag
import at.posselt.pfrpg2e.utils.setAppFlag
import js.objects.recordOf
import kotlin.math.abs

/**
 * The four upkeep steps are tracked once-per-turn on the turn-wizard checklist flag. Both the
 * Run Upkeep batch and the individual sheet buttons consult it so neither double-applies a step
 * the other (or itself) already ran this turn. performEndTurn clears the flag, so it resets per turn.
 * A GM can re-run a step by un-checking it in the Turn Wizard (which removes it from the checklist).
 */
private const val TURN_WIZARD_STATE_FLAG = "turn-wizard-state"

suspend fun KingdomActor.isUpkeepStepDone(id: String): Boolean {
    val state = getAppFlag<KingdomActor, dynamic>(TURN_WIZARD_STATE_FLAG) ?: return false
    val checklist = state.checklist ?: return false
    return id in checklist.unsafeCast<Array<String>>()
}

suspend fun KingdomActor.markUpkeepStepDone(id: String) {
    var state = getAppFlag<KingdomActor, dynamic>(TURN_WIZARD_STATE_FLAG)
    if (state == null) {
        state = js("{ checklist: [], showPreview: false }")
    }
    if (state.checklist == null) {
        state.checklist = emptyArray<String>()
    }
    val arr = state.checklist.unsafeCast<Array<String>>()
    if (id in arr) return
    state.checklist = (arr.toList() + id).toTypedArray()
    setAppFlag(TURN_WIZARD_STATE_FLAG, state)
}

suspend fun KingdomActor.upkeepGainFame(kingdom: KingdomData, suppressChat: Boolean = false): Int {
    val oldFame = kingdom.fame.now
    val newFame = (oldFame + 1).coerceIn(0, kingdom.settings.maximumFamePoints)
    kingdom.fame.now = newFame
    if (!suppressChat) {
        postChatMessage(t("kingdom.gaining1Fame"))
    }
    return newFame - oldFame
}

suspend fun KingdomActor.upkeepAdjustUnrest(game: Game, kingdom: KingdomData, suppressChat: Boolean = false): Int {
    val settlements = kingdom.getAllSettlements(game)
    val allFeatures = kingdom.getExplodedFeatures()
    val chosenFeatures = kingdom.getChosenFeatures(allFeatures)
    val chosenFeats = kingdom.getChosenFeats(chosenFeatures)
    val oldUnrest = kingdom.unrest
    val newUnrest = adjustUnrest(
        kingdom = kingdom,
        settlements = settlements.allSettlements,
        chosenFeats = chosenFeats,
        suppressChat = suppressChat,
    )
    kingdom.unrest = newUnrest
    return newUnrest - oldUnrest
}

suspend fun KingdomActor.upkeepCollectResources(game: Game, kingdom: KingdomData, suppressChat: Boolean = false): Income {
    val realm = game.getRealmData(this, kingdom)
    val settlements = kingdom.getAllSettlements(game)
    val allFeatures = kingdom.getExplodedFeatures()
    val chosenFeatures = kingdom.getChosenFeatures(allFeatures)
    val chosenFeats = kingdom.getChosenFeats(chosenFeatures)
    val resources = collectResources(
        kingdomData = kingdom,
        realmData = realm,
        resourceDice = kingdom.getResourceDiceAmount(
            chosenFeats,
            settlements.allSettlements,
            kingdomLevel = kingdom.level,
        ),
        // Zero once the turn's single luxury bonus has already been spent elsewhere -- the feat
        // says "the first time you gain Luxury Commodities in a Kingdom turn", not "during Upkeep".
        increaseGainedLuxuries = if (kingdom.luxuryBonusUsedThisTurn == true) {
            0
        } else {
            chosenFeats.sumOf { it.feat.increaseGainedLuxuriesOncePerTurnBy ?: 0 }
        },
        settlements = settlements.allSettlements,
        expressionContext = kingdom.createSimpleContext(settlements),
        modifiers = kingdom.createModifiers(settlements),
        suppressChat = suppressChat,
    )
    kingdom.resourcePoints.now = resources.resourcePoints
    kingdom.resourceDice.now = resources.resourceDice
    kingdom.commodities.now.lumber = resources.lumber
    kingdom.commodities.now.luxuries = resources.luxuries
    // calculateIncome applies the bonus only when luxuries were actually gained, so that is the
    // condition under which the turn's one use is spent.
    if (resources.luxuries > 0 && chosenFeats.any { (it.feat.increaseGainedLuxuriesOncePerTurnBy ?: 0) > 0 }) {
        kingdom.luxuryBonusUsedThisTurn = true
    }
    kingdom.commodities.now.stone = resources.stone
    kingdom.commodities.now.ore = resources.ore
    return resources
}

suspend fun KingdomActor.upkeepPayConsumption(game: Game, kingdom: KingdomData, suppressChat: Boolean = false): Int {
    val realm = game.getRealmData(this, kingdom)
    val settlements = kingdom.getAllSettlements(game)
    val oldFood = kingdom.commodities.now.food
    val newFood = payConsumption(
        kingdomActor = this,
        settlements = settlements.allSettlements,
        realmData = realm,
        armyConsumption = kingdom.consumption.armies,
        availableFood = oldFood,
        now = kingdom.consumption.now,
        expressionContext = kingdom.createSimpleContext(settlements),
        modifiers = kingdom.createModifiers(settlements),
        suppressChat = suppressChat,
    )
    kingdom.commodities.now.food = newFood
    return oldFood - newFood
}
