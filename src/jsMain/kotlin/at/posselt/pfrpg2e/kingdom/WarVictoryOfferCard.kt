package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.armies.BattleStatus
import at.posselt.pfrpg2e.kingdom.data.RawArmyBattle
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import js.objects.recordOf

/** Buttons the victory card can carry, recorded in [RawArmyBattle.victoryConsequencesApplied]. */
const val VICTORY_OFFER_STANDING = "standing"
const val VICTORY_OFFER_SIGN_PEACE = "signPeace"
const val VICTORY_OFFER_DEMAND_TRIBUTE = "demandTribute"

/**
 * Posts the GM-confirmed offer for a battle that resolved as VICTORY against a faction-linked war.
 *
 * Winning used to mark the threat DEFEATED and recompute pressure, and stop there: the enemy
 * faction's standing never moved, `atWar` stayed set forever, and no peace was ever on the table.
 * This card is the way back out of a war.
 *
 * It carries the standing gain always, and the peace terms only once [peaceEligible] says the
 * faction has no live threats left — beating one army of three does not end a war.
 *
 * Nothing is applied here; every button is confirmed by the GM. Whispered to GMs because the terms
 * are the GM's call and the threat roster is not all player-visible.
 */
suspend fun offerWarVictory(
    game: Game,
    actor: KingdomActor,
    kingdom: KingdomData,
    battle: RawArmyBattle,
) {
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return

    val threat = kingdom.warThreats?.find { it.id == battle.threatId } ?: return
    // Unlinked threats — a wandering horde — have no diplomacy to resolve, so no card.
    val faction = threat.enemyFactionName?.takeIf { name -> kingdom.groups.any { it.name == name } } ?: return

    val applied = (battle.victoryConsequencesApplied ?: emptyArray()).toSet()
    val standingGain = warStandingDelta(BattleStatus.VICTORY)
    val peaceOffered = peaceEligible(kingdom.threatStates(), faction)

    val showStanding = VICTORY_OFFER_STANDING !in applied && standingGain != 0
    val showPeace = peaceOffered &&
        VICTORY_OFFER_SIGN_PEACE !in applied &&
        VICTORY_OFFER_DEMAND_TRIBUTE !in applied
    if (!showStanding && !showPeace) return

    val context = js("{}")
    context.actorUuid = actor.uuid
    context.battleId = battle.id
    context.faction = faction
    context.showStanding = showStanding
    context.standingAmount = standingGain
    context.standingLabel = t(
        "chatMessages.warVictory.standing",
        recordOf("faction" to faction, "amount" to standingGain),
    )
    context.showPeace = showPeace
    context.peaceBody = t("chatMessages.warVictory.peaceBody", recordOf("faction" to faction))
    context.signPeaceLabel = t(
        "chatMessages.warVictory.signPeace",
        recordOf("floor" to kingdom.settings.peaceStandingFloorOrDefault()),
    )
    context.demandTributeLabel = t(
        "chatMessages.warVictory.demandTribute",
        recordOf("rp" to kingdom.settings.peaceTributeRpOrDefault()),
    )
    context.body = t("chatMessages.warVictory.body", recordOf("faction" to faction))

    postChatTemplate(
        templatePath = "chatmessages/war-victory-offer.hbs",
        templateContext = context,
        whisper = gmUserIds,
    )
}
