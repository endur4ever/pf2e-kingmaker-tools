package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.HoldingIncomeLine
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import js.objects.recordOf

/**
 * The End Turn income digest: ONE whispered card per turn listing every holding's accrued income,
 * with an Award button per line (plan sections 5.1-5.3). Posted AFTER the turn's persist, like
 * every other offer card -- the buttons write the kingdom flag from whichever GM client clicks.
 *
 * Skipped when no GM user exists: an empty whisper array posts publicly, and the card exists to
 * let the GM decide what actually changes hands.
 */
suspend fun postHoldingIncomeOffer(
    game: Game,
    actorUuid: String,
    currentTurn: Int,
    lines: List<HoldingIncomeLine>,
) {
    if (lines.isEmpty()) return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return
    val ctx = js("{}")
    ctx.actorUuid = actorUuid
    ctx.turn = currentTurn
    ctx.title = t("kingdom.holdings.incomeOffer.title")
    ctx.awardLabel = t("kingdom.holdings.incomeOffer.award")
    ctx.rows = lines.map { line ->
        val r = js("{}")
        r.holdingId = line.holdingId
        r.gold = line.gold
        r.line = t(
            "kingdom.holdings.incomeOffer.line",
            recordOf(
                "owner" to line.ownerLabel,
                "holding" to line.holdingName,
                "gold" to line.gold.toString(),
                "luxuries" to line.luxuries.toString(),
                "favors" to line.favors.toString(),
            ),
        )
        r
    }.toTypedArray()
    postChatTemplate(
        templatePath = "chatmessages/holding-income-offer.hbs",
        templateContext = ctx,
        whisper = gmUserIds,
    )
}
