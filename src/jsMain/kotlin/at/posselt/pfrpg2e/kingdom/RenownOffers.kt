package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.EpithetAward
import at.posselt.pfrpg2e.data.kingdom.RenownPerk
import at.posselt.pfrpg2e.data.kingdom.SpotlightKind
import at.posselt.pfrpg2e.data.kingdom.SpotlightPick
import at.posselt.pfrpg2e.data.kingdom.epithetContextFor
import at.posselt.pfrpg2e.data.kingdom.evaluateEpithets
import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import at.posselt.pfrpg2e.kingdom.data.RawPcRenown
import at.posselt.pfrpg2e.kingdom.data.toModel
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import js.objects.recordOf

/** One PC's newly-earned epithet, ready to be offered. */
data class PendingEpithetOffer(
    val actorUuid: String,
    val actorName: String?,
    val award: EpithetAward,
)

/**
 * Which epithets a PC has newly earned this turn.
 *
 * Guarded twice over, because an epithet offer that re-fires is worse than one that never does:
 * an epithet already held is never re-offered, and a PC already offered on [turn] is skipped
 * entirely. The caller stamps `lastOfferedTurn` when it posts.
 */
fun pendingEpithetOffers(
    renownRows: Array<RawPcRenown>?,
    rulerUuid: String?,
    turn: Int,
): List<PendingEpithetOffer> =
    (renownRows ?: emptyArray()).flatMap { row ->
        val uuid = row.actorUuid?.takeIf { it.isNotBlank() } ?: return@flatMap emptyList()
        if (row.lastOfferedTurn == turn) return@flatMap emptyList()
        val model = row.toModel() ?: return@flatMap emptyList()
        val context = epithetContextFor(model, holdsRulerRole = rulerUuid != null && rulerUuid == uuid)
        // EVERY newly-earned epithet, not just the first: a PC who crosses two thresholds in one
        // busy turn has earned both, and offering one per turn would ration honours the catalog
        // already decided they deserve
        evaluateEpithets(model, context)
            .filter { award -> award.epithetId !in model.epithets }
            .map { PendingEpithetOffer(actorUuid = uuid, actorName = row.actorName, award = it) }
    }

/**
 * Posts this turn's epithet offers, GM-whispered, one card per newly-earned epithet.
 *
 * Skipped entirely when no GM user exists: an empty whisper array posts PUBLICLY, and these cards
 * carry GM-only grant buttons. Stamps `lastOfferedTurn` on every PC offered so the same epithet
 * cannot be re-offered next turn while the GM decides.
 */
suspend fun postEpithetOffers(
    game: Game,
    actorUuid: String,
    kingdom: KingdomData,
    turn: Int,
    offers: List<PendingEpithetOffer>,
) {
    if (offers.isEmpty()) return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return

    for (offer in offers) {
        val ctx = js("{}")
        ctx.actorUuid = actorUuid
        ctx.pcUuid = offer.actorUuid
        ctx.epithetId = offer.award.epithetId
        ctx.title = t("kingdom.renown.epithetOffer.title")
        ctx.line = t(
            "kingdom.renown.epithetOffer.line",
            recordOf(
                "name" to (offer.actorName ?: t("kingdom.renown.unknownPc")),
                "epithet" to t("kingdom.renown.epithet.${offer.award.epithetId}"),
            ),
        )
        ctx.grantLabel = t("kingdom.renown.epithetOffer.grant")
        ctx.dismissLabel = t("kingdom.renown.epithetOffer.dismiss")
        // the perk rides the SAME card as a second button rather than a second whisper: one
        // decision for the GM, and a perk cannot be granted for an epithet they declined
        when (val perk = offer.award.perk) {
            is RenownPerk.PurchaseAccess -> {
                ctx.perkKind = "access"
                ctx.perkTier = perk.levels
                ctx.perkLabel = t("kingdom.renown.perkOffer.access", recordOf("levels" to perk.levels.toString()))
            }
            is RenownPerk.Invitation -> {
                ctx.perkKind = "invitation"
                ctx.perkFaction = perk.factionName
                ctx.perkLabel = t("kingdom.renown.perkOffer.invitation")
            }
            null -> ctx.perkKind = null
        }
        postChatTemplate(
            templatePath = "chatmessages/renown-epithet-offer.hbs",
            templateContext = ctx,
            whisper = gmUserIds,
        )
    }

    val offeredUuids = offers.map { it.actorUuid }.toSet()
    kingdom.renown = (kingdom.renown ?: emptyArray()).map { row ->
        if (row.actorUuid in offeredUuids) RawPcRenown.copy(row, lastOfferedTurn = turn) else row
    }.toTypedArray()
}

/** The Spotlight line, localized. Literal keys per kind so the i18n guard can see every one. */
fun localizeSpotlight(pick: SpotlightPick): String {
    val data = recordOf(
        "name" to (pick.actorName ?: t("kingdom.renown.unknownPc")),
        "count" to pick.count.toString(),
    )
    return when (pick.kind) {
        SpotlightKind.BUSIEST -> t("kingdom.renown.spotlight.busiest", data)
        SpotlightKind.CRIT_STAR -> t("kingdom.renown.spotlight.critStar", data)
        SpotlightKind.BLUNDERER -> t("kingdom.renown.spotlight.blunderer", data)
        SpotlightKind.DIPLOMAT -> t("kingdom.renown.spotlight.diplomat", data)
        SpotlightKind.EVENT_HERO -> t("kingdom.renown.spotlight.eventHero", data)
    }
}

/** The PC currently holding the Ruler role, if any -- the one epithet condition that needs it. */
suspend fun KingdomData.rulerActorUuid(): String? =
    runCatching { parseLeaderActors().resolve(Leader.RULER)?.uuid }.getOrNull()
