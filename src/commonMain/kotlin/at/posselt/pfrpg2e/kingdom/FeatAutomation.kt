package at.posselt.pfrpg2e.kingdom

/**
 * Pure mechanics for the previously-"Not automated" kingdom feats. The impure surfaces (offer chat
 * cards, the Liquidate dialog, wiring into the check pipeline) consume these; the numbers live here
 * so they stay pinned to the feat text and unit-tested. State is tracked on RawKingdomData by
 * Migration45 (pullTogetherCurrentDC / pullTogetherUsedThisTurn / pullTogetherTurnsSinceLastUsed /
 * liquidateResourcesPenaltyNextTurn).
 */

/** Pull Together's flat-check base DC (also its floor when it decays). */
const val PULL_TOGETHER_BASE_DC = 11

/** The DC rises by 5 each time Pull Together is used. */
fun pullTogetherDcAfterUse(currentDc: Int): Int = currentDc + 5

/**
 * At the end of a Kingdom turn: the flat-check DC decreases by 1 (floored at [PULL_TOGETHER_BASE_DC])
 * for each turn that passes without using Pull Together; a turn in which it was used holds the DC.
 */
fun pullTogetherDcAfterTurn(currentDc: Int, usedThisTurn: Boolean): Int =
    if (usedThisTurn) currentDc else maxOf(PULL_TOGETHER_BASE_DC, currentDc - 1)

/** Pull Together may only be attempted on a critical failure, once per Kingdom turn. */
fun canUsePullTogether(isCriticalFailure: Boolean, alreadyUsedThisTurn: Boolean): Boolean =
    isCriticalFailure && !alreadyUsedThisTurn

/** Resource Dice reduction applied on the turn after Liquidate Resources is used. */
const val LIQUIDATE_RESOURCES_NEXT_TURN_RD_PENALTY = 4

/**
 * Liquidate Resources is available the first time in a turn a forced RP expense (from a failed check
 * or dangerous event) would reduce the kingdom to 0 RP or below. Using it treats the expense as paid
 * and leaves the kingdom at [liquidatedRp] RP, at the cost of [LIQUIDATE_RESOURCES_NEXT_TURN_RD_PENALTY]
 * fewer Resource Dice next turn.
 */
fun canLiquidateResources(currentRp: Int, expense: Int, alreadyUsedThisTurn: Boolean): Boolean =
    !alreadyUsedThisTurn && expense > 0 && currentRp - expense <= 0

/** RP left after liquidating — reduced to 1 (never fully to 0). */
fun liquidatedRp(): Int = 1

/** Feat id granting the once-per-turn luxury bonus. */
const val QUALITY_OF_LIFE_FEAT = "quality-of-life"

/**
 * The extra Luxuries a gain of [gained] receives from Quality of Life.
 *
 * The feat reads: "The first time you gain Luxury Commodities in a Kingdom turn, increase the total
 * gained by 1." So it applies to the first ACTUAL gain of the turn from any source — not merely the
 * Upkeep collection, and not to a gain of zero, which is not a gain at all.
 */
fun qualityOfLifeLuxuryBonus(gained: Int, bonusPerTurn: Int, alreadyUsedThisTurn: Boolean): Int =
    if (gained > 0 && !alreadyUsedThisTurn) bonusPerTurn else 0

/** The level-20 kingdom FEATURE (not a feat) granting the Unrest/Ruin ignore. */
const val ENVY_OF_THE_WORLD_FEATURE = "envy-of-the-world"

/**
 * Whether Envy of the World's FREE ignore applies to an increase of [gained].
 *
 * "The first time in a Kingdom turn when your kingdom would gain Unrest or Ruin, ignore that
 * increase." First — not every time, which is what a level check alone amounts to — and an increase,
 * so a zero or negative delta is not one and must not burn the turn's free ignore.
 *
 * Later increases in the same turn can still be ignored, but only by spending a Fame or Infamy
 * point, which is a choice and therefore an offer rather than an automatic suppression.
 */
fun envyIgnoresIncrease(gained: Int, alreadyUsedThisTurn: Boolean): Boolean =
    gained > 0 && !alreadyUsedThisTurn
