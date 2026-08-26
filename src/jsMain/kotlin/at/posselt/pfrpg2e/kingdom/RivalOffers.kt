package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import js.objects.recordOf

/**
 * Posts this turn's rival offer digests: at most ONE whisper per offer type per turn, each card
 * listing every rival that crossed its threshold as a button row (three massing rivals are one
 * card with three rows, not three cards -- plan section 5.3).
 *
 * GM-whispered and skipped entirely when no GM user exists: an empty whisper array posts
 * PUBLICLY, and both cards carry GM-only buttons plus dials (thresholds, growth bookkeeping)
 * players must never see. Identity is pinned in data attributes at post time -- the handlers
 * re-resolve everything through the actor uuid rather than trusting a live sheet.
 */
suspend fun postRivalOfferDigests(
    game: Game,
    actorUuid: String,
    currentTurn: Int,
    warRows: List<RivalWarOfferRow>,
    shiftRows: List<RivalShiftOfferRow>,
) {
    if (warRows.isEmpty() && shiftRows.isEmpty()) return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return

    if (warRows.isNotEmpty()) {
        val ctx = js("{}")
        ctx.actorUuid = actorUuid
        ctx.title = t("kingdom.rivalRealms.warOffer.digestTitle")
        ctx.raiseLabel = t("kingdom.rivalRealms.warOffer.raise")
        ctx.dismissLabel = t("kingdom.rivalRealms.warOffer.dismiss")
        ctx.rows = warRows.map { row ->
            val r = js("{}")
            r.realmId = row.realmId
            r.faction = row.factionRef
            r.armyCount = row.armyCount
            r.line = t("kingdom.rivalRealms.warOffer.title", recordOf("rival" to row.factionRef)) +
                    " — ${t("kingdom.rivalRealms.armies")}: ${row.armyCount}"
            r
        }.toTypedArray()
        postChatTemplate(
            templatePath = "chatmessages/rival-war-threat-offer.hbs",
            templateContext = ctx,
            whisper = gmUserIds,
        )
    }

    if (shiftRows.isNotEmpty()) {
        val ctx = js("{}")
        ctx.actorUuid = actorUuid
        ctx.title = t("kingdom.rivalRealms.standingOffer.digestTitle")
        ctx.applyLabel = t("kingdom.rivalRealms.standingOffer.apply")
        ctx.dismissLabel = t("kingdom.rivalRealms.standingOffer.dismiss")
        ctx.turn = currentTurn
        ctx.rows = shiftRows.map { row ->
            val r = js("{}")
            r.faction = row.factionRef
            // the reason is resolved ONCE, here, and pinned on the card: the apply handler uses
            // it both for the log entry and the dedup compare, so every clicking client --
            // whatever its locale -- works with the same bytes
            r.reason = t("kingdom.rivalRealms.standingReason", recordOf("rival" to row.factionRef))
            r.line = t("kingdom.rivalRealms.standingOffer.title", recordOf("rival" to row.factionRef)) +
                    " — ${t("kingdom.rivalRealms.size")} +${row.sizeDelta}"
            r
        }.toTypedArray()
        postChatTemplate(
            templatePath = "chatmessages/rival-standing-shift-offer.hbs",
            templateContext = ctx,
            whisper = gmUserIds,
        )
    }
}
