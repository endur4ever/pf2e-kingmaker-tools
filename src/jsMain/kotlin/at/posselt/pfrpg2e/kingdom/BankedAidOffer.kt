package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.tpl
import js.objects.recordOf

/**
 * The "Spend banked aid" button for a failed check result, or an empty string.
 *
 * Offered only when the kingdom holds an unexpired banked bonus AND spending it would actually
 * lift the degree: a +2 that leaves a failure a failure is a trap rather than a choice. The re-check
 * runs again at confirm time, because the card can sit in chat while other checks spend the bank.
 */
suspend fun buildBankedAidButton(
    actor: KingdomActor,
    meta: UpgradeMetaContext,
    degree: DegreeOfSuccess,
): String {
    if (degree.succeeded()) return ""
    val dc = meta.dc ?: return ""
    val total = meta.total ?: return ""
    val dieValue = meta.dieValue ?: return ""
    val kingdom = actor.getKingdom() ?: return ""
    val (bonusId, bonus) = kingdom.bestSpendableBonus(kingdom.currentTurn ?: 0) ?: return ""
    if (!aidWouldImprove(dc = dc, total = total, dieValue = dieValue, bonus = bonus.value)) return ""
    return tpl(
        path = "chatmessages/banked-aid-offer.hbs",
        ctx = recordOf(
            "actorUuid" to actor.uuid,
            "bonusId" to bonusId,
            "label" to t(
                "kingdom.bankedAid.spend",
                recordOf("value" to bonus.value.toString(), "source" to bonus.source),
            ),
        ),
    )
}
