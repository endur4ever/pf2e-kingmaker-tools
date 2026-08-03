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
