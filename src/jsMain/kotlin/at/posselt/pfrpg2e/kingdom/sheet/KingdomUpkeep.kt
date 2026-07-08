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
import js.objects.recordOf
import kotlin.math.abs

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
        increaseGainedLuxuries = chosenFeats.sumOf { it.feat.increaseGainedLuxuriesOncePerTurnBy ?: 0 },
        settlements = settlements.allSettlements,
        expressionContext = kingdom.createSimpleContext(settlements),
        modifiers = kingdom.createModifiers(settlements),
        suppressChat = suppressChat,
    )
    kingdom.resourcePoints.now = resources.resourcePoints
    kingdom.resourceDice.now = resources.resourceDice
    kingdom.commodities.now.lumber = resources.lumber
    kingdom.commodities.now.luxuries = resources.luxuries
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
