package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.ChosenFeat
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import js.objects.recordOf

const val LIQUIDATE_RESOURCES_FEAT = "liquidate-resources"

/**
 * Whether Liquidate Resources has already been used this Kingdom turn.
 *
 * The next-turn Resource Dice penalty doubles as the once-per-turn marker: it is set the moment the
 * feat is used and cleared at End Turn when the penalty is spent, so within a turn its presence
 * means exactly "already liquidated". That avoids a second persisted field for the same fact.
 */
fun KingdomData.liquidateUsedThisTurn(): Boolean = liquidateResourcesPenaltyNextTurn == true

/**
 * Offers Liquidate Resources when a forced expense has just emptied the treasury.
 *
 * The feat reads: "The first time during a Kingdom turn in which you are forced to spend RP … and
 * that expense reduces you to 0 RP, you MAY instead reduce your RP to 1 and treat the expense as if
 * it were paid in full. At the start of your next Kingdom turn, roll 4 fewer Resource Dice."
 *
 * "May" is why this is an offer rather than an automatic rescue — and the RP is already at 0 by the
 * time it posts, so declining costs nothing and needs no undo.
 */
suspend fun offerLiquidateResources(
    game: Game,
    actor: KingdomActor,
    kingdom: KingdomData,
    previousRp: Int,
    chosenFeats: List<ChosenFeat>,
) {
    if (previousRp <= 0) return
    if (kingdom.resourcePoints.now > 0) return
    if (kingdom.liquidateUsedThisTurn()) return
    if (chosenFeats.none { it.feat.id == LIQUIDATE_RESOURCES_FEAT }) return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return
    postChatTemplate(
        templatePath = "chatmessages/liquidate-resources-offer.hbs",
        templateContext = recordOf(
            "actorUuid" to actor.uuid,
            "body" to t("kingdom.liquidate.body", recordOf("spent" to previousRp)),
            "label" to t(
                "kingdom.liquidate.accept",
                recordOf(
                    "rp" to liquidatedRp().toString(),
                    "dice" to LIQUIDATE_RESOURCES_NEXT_TURN_RD_PENALTY.toString(),
                ),
            ),
        ),
        whisper = gmUserIds,
    )
}
