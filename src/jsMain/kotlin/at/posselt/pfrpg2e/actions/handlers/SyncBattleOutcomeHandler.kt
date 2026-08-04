package at.posselt.pfrpg2e.actions.handlers

import at.posselt.pfrpg2e.actions.ActionDispatcher
import at.posselt.pfrpg2e.actions.ActionMessage
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.kingdom.data.RawArmyBattle
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import at.posselt.pfrpg2e.kingdom.data.WarThreatStatus
import at.posselt.pfrpg2e.kingdom.recalculateWarPressure
import at.posselt.pfrpg2e.data.armies.BattleStatus
import at.posselt.pfrpg2e.data.armies.shouldOfferArmyLevelUp
import at.posselt.pfrpg2e.data.armies.xpThresholdForLevel
import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import at.posselt.pfrpg2e.utils.setAppFlag
import at.posselt.pfrpg2e.utils.getAppFlag
import at.posselt.pfrpg2e.utils.postChatTemplate
import com.foundryvtt.pf2e.actor.PF2EArmy
import com.foundryvtt.core.Game
import js.objects.recordOf
import kotlinx.js.JsPlainObject
import kotlinx.coroutines.await

@JsPlainObject
external interface SyncBattleOutcomeAction {
    val battle: RawArmyBattle
    val kingdomActorUuid: String
}

class SyncBattleOutcomeHandler(
    private val game: Game,
) : ActionHandler("syncBattleOutcome", originatorPolicy = OriginatorPolicy.GM_ONLY) {
    override suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher) {
        val data = action.data.unsafeCast<SyncBattleOutcomeAction>()
        val kingdomActor = fromUuidTypeSafe<KingdomActor>(data.kingdomActorUuid) ?: return
        val kingdom = kingdomActor.getKingdom() ?: return
        val battle = data.battle

        // 1. Sync battle outcome to active battles in kingdom data
        kingdom.activeBattles = (kingdom.activeBattles ?: emptyArray())
            .map { if (it.id == battle.id) battle else it }
            .toTypedArray()

        if (battle.status == BattleStatus.VICTORY.value) {
            kingdom.warThreats = (kingdom.warThreats ?: emptyArray())
                .map {
                    if (it.id == battle.threatId) {
                        RawWarThreat.copy(it, status = WarThreatStatus.DEFEATED.value)
                    } else {
                        it
                    }
                }
                .toTypedArray()
            kingdom.warPressure = recalculateWarPressure(
                kingdom.warThreats ?: emptyArray(),
                kingdom.armyDeployments ?: emptyArray(),
                kingdom.warPressure,
            )
        }

        kingdomActor.setKingdom(kingdom)

        // 2. Sync to participating PF2EArmy actor sheets
        val participants = battle.attackers + battle.defenders
        for (participant in participants) {
            val uuid = participant.armyActorUuid
            if (uuid.isNotBlank()) {
                val armyActor = fromUuidTypeSafe<PF2EArmy>(uuid)
                if (armyActor != null) {
                    // Update HP on sheet dynamically (avoiding js() template string constraint)
                    val updateData = js("{}")
                    updateData["system.attributes.hp.value"] = participant.currentHp
                    armyActor.update(updateData.unsafeCast<com.foundryvtt.core.AnyObject>()).await()

                    // Persist conditions flag
                    armyActor.setAppFlag("conditions", participant.conditions)

                    // Persist cumulative XP flag
                    val oldXp: Int = armyActor.getAppFlag<PF2EArmy, Int>("xp") ?: 0
                    armyActor.setAppFlag("xp", participant.xp)

                    // Post GM level-up offer card if XP threshold is crossed
                    val currentLevel = armyActor.system.details.level.value
                    val threshold = xpThresholdForLevel(currentLevel)
                    if (shouldOfferArmyLevelUp(oldXp = oldXp, newXp = participant.xp, currentLevel = currentLevel)) {
                        val nextLevel = currentLevel + 1
                        val offerId = "levelup-${armyActor.id}-${nextLevel}"
                        postChatTemplate(
                            templatePath = "chatmessages/army-levelup-offer.hbs",
                            templateContext = recordOf(
                                "armyName" to participant.name,
                                "armyUuid" to uuid,
                                "currentXp" to participant.xp,
                                "xpThreshold" to threshold,
                                "nextLevel" to nextLevel,
                                "offerId" to offerId,
                            )
                        )
                    }
                }
            }
        }
    }
}
