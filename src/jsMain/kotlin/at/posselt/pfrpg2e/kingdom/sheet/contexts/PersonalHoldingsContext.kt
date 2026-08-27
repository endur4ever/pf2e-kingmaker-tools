package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.data.kingdom.HoldingCondition
import at.posselt.pfrpg2e.kingdom.conditionEnum
import at.posselt.pfrpg2e.kingdom.data.RawPersonalHolding
import at.posselt.pfrpg2e.kingdom.tierEnum
import at.posselt.pfrpg2e.data.kingdom.conditionAdjustedIncome
import at.posselt.pfrpg2e.utils.t
import js.objects.recordOf
import kotlinx.js.JsPlainObject

@Suppress("unused")
@JsPlainObject
external interface HoldingCardContext {
    val id: String
    val title: String?
    val name: String
    val kindLabel: String
    val ownerLabel: String
    val locationLabel: String?
    val conditionValue: String
    val conditionLabel: String
    val incomeLabel: String
    val lastEventLabel: String?
    val isOwner: Boolean
    val isGM: Boolean
}

@Suppress("unused")
@JsPlainObject
external interface PersonalHoldingsSectionContext {
    val holdings: Array<HoldingCardContext>
    val hasAny: Boolean
    val isGM: Boolean
    val canGrant: Boolean
}

/** Literal keys in whens -- composed keys are invisible to the i18n guard. */
private fun kindLabel(kind: String): String = when (kind) {
    "manor" -> t("kingdom.holdings.kind.manor")
    "lodge" -> t("kingdom.holdings.kind.lodge")
    "tavern-stake" -> t("kingdom.holdings.kind.tavernStake")
    "farmstead" -> t("kingdom.holdings.kind.farmstead")
    "workshop" -> t("kingdom.holdings.kind.workshop")
    else -> t("kingdom.holdings.kind.other")
}

private fun conditionLabel(condition: HoldingCondition): String = when (condition) {
    HoldingCondition.SOUND -> t("kingdom.holdings.condition.sound")
    HoldingCondition.DAMAGED -> t("kingdom.holdings.condition.damaged")
    HoldingCondition.DESTROYED -> t("kingdom.holdings.condition.destroyed")
}

/**
 * Builds the holdings cards. A PLAYER sees only holdings they own ([ownedActorUuids] resolves the
 * viewer's owned actors); the GM sees all. The projected income shown uses the level the NEXT tick
 * will use ([ownerLevels], kingdom fallback), so the card and the eventual digest agree.
 */
fun buildPersonalHoldingsContext(
    holdings: Array<RawPersonalHolding>?,
    isGM: Boolean,
    ownedActorUuids: Set<String>,
    ownerLevels: Map<String, Int>,
    kingdomLevel: Int,
): PersonalHoldingsSectionContext {
    val cards = (holdings ?: emptyArray()).mapNotNull { holding ->
        val isOwner = holding.actorUuid?.let { it in ownedActorUuids } == true
        val visible = isGM || (isOwner && holding.visibleToPlayers != false)
        if (!visible) return@mapNotNull null
        val condition = holding.conditionEnum()
        val level = holding.actorUuid?.let { ownerLevels[it] } ?: kingdomLevel
        val income = conditionAdjustedIncome(holding.tierEnum(), level, condition)
        HoldingCardContext(
            id = holding.id,
            title = holding.title?.takeIf { it.isNotBlank() },
            name = holding.name,
            kindLabel = kindLabel(holding.kind),
            ownerLabel = holding.ownerLabel ?: "?",
            locationLabel = holding.boundHexKey ?: holding.structureRef,
            conditionValue = condition.value,
            conditionLabel = conditionLabel(condition),
            incomeLabel = t(
                "kingdom.holdings.incomePerTurn",
                recordOf(
                    "gold" to income.gold.toString(),
                    "luxuries" to income.luxuries.toString(),
                    "favors" to income.favors.toString(),
                ),
            ),
            lastEventLabel = holding.lastEventLabel?.takeIf { it.isNotBlank() },
            isOwner = isOwner,
            isGM = isGM,
        )
    }
    return PersonalHoldingsSectionContext(
        holdings = cards.toTypedArray(),
        hasAny = cards.isNotEmpty(),
        isGM = isGM,
        canGrant = isGM,
    )
}
