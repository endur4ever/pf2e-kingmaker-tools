package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.DamageSeverity
import at.posselt.pfrpg2e.data.kingdom.HoldingCondition
import at.posselt.pfrpg2e.data.kingdom.HoldingIncome
import at.posselt.pfrpg2e.data.kingdom.HoldingIncomeLine
import at.posselt.pfrpg2e.data.kingdom.HoldingTier
import at.posselt.pfrpg2e.data.kingdom.conditionAdjustedIncome
import at.posselt.pfrpg2e.data.kingdom.nextConditionAfterDamage
import at.posselt.pfrpg2e.kingdom.data.RawPersonalHolding

/**
 * Boundary reads. The `?:` fallback is the deliberate choice written HERE at the one boundary,
 * not buried in the enum: a discriminator this build does not recognise degrades to the most
 * conservative live value rather than throwing mid-tick.
 */
fun RawPersonalHolding.tierEnum(): HoldingTier =
    HoldingTier.fromValue(incomeTier) ?: HoldingTier.MODEST

fun RawPersonalHolding.conditionEnum(): HoldingCondition =
    HoldingCondition.fromValue(condition) ?: HoldingCondition.SOUND

/** Copied Raw with the condition advanced and the card hint set; the input is never mutated. */
fun applyHoldingDamage(holding: RawPersonalHolding, severity: DamageSeverity, label: String): RawPersonalHolding {
    val next = nextConditionAfterDamage(holding.conditionEnum(), severity)
    return RawPersonalHolding.copy(
        holding,
        condition = next.value,
        lastEventLabel = label,
    )
}

/** One holding's income this turn; ZERO when [RawPersonalHolding.lastIncomeTurn] already stamped it. */
fun accrueIncome(holding: RawPersonalHolding, ownerLevel: Int, currentTurn: Int): HoldingIncome {
    if (holding.lastIncomeTurn == currentTurn) return HoldingIncome.ZERO
    return conditionAdjustedIncome(holding.tierEnum(), ownerLevel, holding.conditionEnum())
}

/** The tick's whole holdings pass: stamped rows out, offer lines out. Pure over its inputs. */
data class HoldingAccrualResult(
    val holdings: Array<RawPersonalHolding>,
    val offers: List<HoldingIncomeLine>,
)

/**
 * Accrues every holding's income for [currentTurn].
 *
 * Advances ONLY lastIncomeTurn -- the idempotency stamp, safe to move whether or not the GM ever
 * clicks Award. lifetimeIncomeGold records gold ACTUALLY handed over and is bumped only by the
 * award handler; advancing it here would count gold nobody received. A holding whose owner is not
 * in [ownerLevels] falls back to [kingdomLevel] (plan open question 3), so preview and commit
 * agree even when an actor fails to resolve.
 */
fun accrueHoldingIncome(
    holdings: Array<RawPersonalHolding>,
    ownerLevels: Map<String, Int>,
    kingdomLevel: Int,
    currentTurn: Int,
): HoldingAccrualResult {
    if (holdings.isEmpty()) return HoldingAccrualResult(holdings, emptyList())
    val offers = mutableListOf<HoldingIncomeLine>()
    val stamped = holdings.map { holding ->
        val level = holding.actorUuid?.let { ownerLevels[it] } ?: kingdomLevel
        val income = accrueIncome(holding, level, currentTurn)
        if (income.isEmpty) {
            holding
        } else {
            offers.add(
                HoldingIncomeLine(
                    ownerUserId = holding.ownerUserId,
                    ownerLabel = holding.ownerLabel ?: holding.name,
                    holdingId = holding.id,
                    holdingName = holding.name,
                    gold = income.gold,
                    luxuries = income.luxuries,
                    favors = income.favors,
                )
            )
            RawPersonalHolding.copy(holding, lastIncomeTurn = currentTurn)
        }
    }.toTypedArray()
    return HoldingAccrualResult(holdings = stamped, offers = offers)
}
