package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import js.objects.recordOf

/** Caravans still on the road to [partners]. */
fun KingdomData.caravansInTransitTo(partners: Set<String>) =
    (caravans ?: emptyArray()).filter { it.status == "inTransit" && it.partnerName in partners }

/**
 * Posts one GM-confirmed offer per shipment still travelling to a partner war was just declared on.
 *
 * The card's rule is explicit: no silent confiscation. Each shipment is either recalled (it turns
 * around and its cargo comes home) or pressed on, in which case the war's raid DC penalty applies
 * to it like any other shipment to a hostile partner.
 */
suspend fun postCaravanWarOffers(
    game: Game,
    actor: KingdomActor,
    kingdom: KingdomData,
    partners: Set<String>,
) {
    if (partners.isEmpty()) return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return
    kingdom.caravansInTransitTo(partners).forEach { caravan ->
        val cargo = caravan.cargoCommodity
            ?.let { t("kingdom.caravans.cargoCommodity", recordOf("amount" to caravan.cargoAmount, "commodity" to t("kingdom.$it"))) }
            ?: t("kingdom.caravans.cargoRp", recordOf("amount" to (caravan.cargoRp ?: 0)))
        postChatTemplate(
            templatePath = "chatmessages/caravan-war-offer.hbs",
            templateContext = recordOf(
                "actorUuid" to actor.uuid,
                "caravanId" to caravan.id,
                "partner" to (caravan.partnerName ?: ""),
                "summary" to t(
                    "kingdom.caravans.warOfferSummary",
                    recordOf(
                        "cargo" to cargo,
                        "dest" to caravan.destLabel,
                        "turns" to caravan.turnsRemaining,
                    ),
                ),
            ),
            whisper = gmUserIds,
        )
    }
}

/**
 * Turn a shipment around: mark it recalled and bring its cargo home.
 *
 * The tick already ignores anything that is not `inTransit`, so the status alone stops it; the
 * refund is what makes a recall a real choice rather than a write-off.
 */
fun KingdomData.recallCaravan(caravanId: String): Boolean {
    val caravan = (caravans ?: emptyArray()).find { it.id == caravanId && it.status == "inTransit" } ?: return false
    caravan.status = "recalled"
    // Refund what was actually spent at dispatch, and only that. A buyFromPartner caravan records
    // BOTH the RP it paid and the commodities it expects to bring home, so refunding both handed
    // the kingdom goods it never owned on top of its money back.
    if (caravan.kind == "buyFromPartner") {
        caravan.cargoRp?.takeIf { it > 0 }?.let { resourcePoints.now += it }
    } else {
        val commodity = caravan.cargoCommodity
        if (commodity != null && caravan.cargoAmount > 0) {
            val now = commodities.now
            when (commodity) {
                "food" -> now.food += caravan.cargoAmount
                "lumber" -> now.lumber += caravan.cargoAmount
                "stone" -> now.stone += caravan.cargoAmount
                "ore" -> now.ore += caravan.cargoAmount
                "luxuries" -> now.luxuries += caravan.cargoAmount
            }
        }
    }
    return true
}
