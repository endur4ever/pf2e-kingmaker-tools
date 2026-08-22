package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawGroup
import at.posselt.pfrpg2e.kingdom.dialogs.CheckType
import at.posselt.pfrpg2e.kingdom.dialogs.CleanseItemPreparation
import at.posselt.pfrpg2e.kingdom.dialogs.kingdomCheckDialog
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import js.objects.recordOf

/**
 * Rolls a prepared Cleanse Item attempt and offers its cost.
 *
 * The DC comes from the item, not the kingdom, so it is passed as an override. The luxuries are
 * never deducted here: the cost is posted as a card the GM confirms, matching how every other
 * resource change in this module is offered rather than applied behind the table's back.
 */
suspend fun rollCleanseItem(
    game: Game,
    actor: KingdomActor,
    kingdom: KingdomData,
    activity: RawActivity,
    preparation: CleanseItemPreparation,
    groups: Array<RawGroup>,
    events: List<OngoingEvent>,
) {
    kingdomCheckDialog(
        game = game,
        kingdom = kingdom,
        kingdomActor = actor,
        check = CheckType.PerformActivity(activity),
        overrideDc = preparation.dc,
        selectedLeader = game.getActiveLeader(),
        groups = groups,
        events = events,
        rollOptions = setOf("cleanse-item"),
        afterRoll = { degree ->
            actor.recordActivityPerformed(activity.id)
            val charged = cleanseItemLuxuryCharge(preparation.luxuryCost, degree)
            postChatTemplate(
                templatePath = "chatmessages/cleanse-item-cost.hbs",
                templateContext = recordOf(
                    "itemName" to preparation.itemName,
                    "itemLevel" to preparation.itemLevel,
                    "settlementName" to preparation.settlement.name,
                    "counteractLevel" to preparation.counteractLevel,
                    "luxuries" to charged,
                    // A critical success consumed only half, which is worth saying out loud so the
                    // number on the button does not look like a mistake.
                    "halved" to (charged < preparation.luxuryCost),
                    "available" to kingdom.commodities.now.luxuries,
                    "affordable" to (kingdom.commodities.now.luxuries >= charged),
                    "actorUuid" to actor.uuid,
                ),
            )
        },
    )
}
