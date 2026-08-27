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

/**
 * One whispered damage offer per struck holding. The FROM condition is pinned on the card, which
 * is the whole idempotency story: Apply advances only when the holding still holds the pinned
 * condition, so a double-click or a stale card is a visible no-op rather than a second blow.
 */
suspend fun postHoldingDamageOffer(
    game: Game,
    actorUuid: String,
    holding: at.posselt.pfrpg2e.kingdom.data.RawPersonalHolding,
    severity: at.posselt.pfrpg2e.data.kingdom.DamageSeverity,
    cause: String,
) {
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return
    val ctx = js("{}")
    ctx.actorUuid = actorUuid
    ctx.holdingId = holding.id
    ctx.fromCondition = holding.condition
    ctx.severity = severity.name
    ctx.cause = cause
    ctx.title = t("kingdom.holdings.damageOffer.title")
    ctx.line = t(
        "kingdom.holdings.damageOffer.line",
        recordOf("holding" to holding.name, "cause" to cause),
    )
    ctx.applyLabel = t("kingdom.holdings.damageOffer.apply")
    ctx.waiveLabel = t("kingdom.holdings.damageOffer.waive")
    postChatTemplate(
        templatePath = "chatmessages/holding-damage-offer.hbs",
        templateContext = ctx,
        whisper = gmUserIds,
    )
}

/** The repair offer for one damaged/destroyed holding; cost pinned at post time. */
suspend fun postHoldingRepairOffer(
    game: Game,
    actorUuid: String,
    holding: at.posselt.pfrpg2e.kingdom.data.RawPersonalHolding,
    cost: Int,
) {
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return
    val ctx = js("{}")
    ctx.actorUuid = actorUuid
    ctx.holdingId = holding.id
    ctx.fromCondition = holding.condition
    ctx.cost = cost
    ctx.title = t("kingdom.holdings.repairOffer.title")
    ctx.line = t(
        "kingdom.holdings.repairOffer.line",
        recordOf("holding" to holding.name, "cost" to cost.toString()),
    )
    ctx.repairLabel = t("kingdom.holdings.repairOffer.repair", recordOf("cost" to cost.toString()))
    postChatTemplate(
        templatePath = "chatmessages/holding-repair-offer.hbs",
        templateContext = ctx,
        whisper = gmUserIds,
    )
}
