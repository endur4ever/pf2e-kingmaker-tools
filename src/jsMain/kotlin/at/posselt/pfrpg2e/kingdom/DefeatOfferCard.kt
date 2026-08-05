package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.armies.ArmyCondition
import at.posselt.pfrpg2e.kingdom.data.RawArmyBattle
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import js.objects.recordOf

/**
 * Posts the GM-confirmed defeat-consequence offer for a battle that resolved as DEFEAT.
 *
 * Each button on the card applies exactly one labelled delta via the `km-offer-battle-defeat`
 * chat button; nothing is applied here. Consequences already applied for this battle are omitted,
 * so a re-posted card never re-offers them.
 *
 * GM-whispered, matching the other km-offer-* cards — the fallout is the GM's call and the
 * numbers would spoil the threat's escalation state for players.
 *
 * @param kingdom the CALLER's kingdom clone, already persisted; this function only reads it
 * @param battle the resolved battle, whose threatId links the escalation/arrival consequences
 */
suspend fun offerDefeatConsequences(
    game: Game,
    actor: KingdomActor,
    kingdom: KingdomData,
    battle: RawArmyBattle,
) {
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return

    val threat = kingdom.warThreats?.find { it.id == battle.threatId }
    // Attackers are the kingdom's deployed armies (defenders are the threat's), so the kingdom's
    // losses are the destroyed attackers.
    val armiesLost = battle.attackers.count { ArmyCondition.DESTROYED.value in it.conditions }
    val consequences = calculateDefeatConsequences(
        threatEscalation = threat?.escalationLevel ?: 0,
        maxEscalation = threat?.maxEscalation ?: 1,
        armiesLost = armiesLost,
        settlementTargeted = threat?.targetSettlementSceneId != null,
    )

    val applied = (battle.defeatConsequencesApplied ?: emptyArray()).toSet()
    val offers = defeatOffers(consequences, applied)
        // Escalation and arrival need a threat to act on; without one the buttons would only be
        // able to report failure, so do not offer them at all.
        .filter { threat != null || (it.key != DEFEAT_OFFER_ESCALATION && it.key != DEFEAT_OFFER_ARRIVAL) }
    if (offers.isEmpty()) return

    val context = js("{}")
    context.actorUuid = actor.uuid
    context.battleId = battle.id
    context.offers = offers.map { offer ->
        val row = js("{}")
        row.key = offer.key
        row.amount = offer.amount
        row.label = t("chatMessages.battleDefeat.${offer.key}", recordOf("amount" to offer.amount))
        row
    }.toTypedArray()

    postChatTemplate(
        templatePath = "chatmessages/battle-defeat-offer.hbs",
        templateContext = context,
        whisper = gmUserIds,
    )
}
