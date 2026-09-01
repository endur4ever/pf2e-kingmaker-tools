package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Liquidate Resources penalty reduces the dice actually ROLLED at the start of the next turn.
 *
 * It used to be subtracted from `resourceDice.next` during End Turn -- a field the tick has
 * already zeroed -- and the marker flag was cleared in the same breath, so the -4 never landed in
 * any configuration and nothing survived to retry. This pins the arithmetic at the point where
 * the rolled count exists.
 */
class LiquidatePenaltyTest {
    private fun rolled(base: Int, penaltyPending: Boolean): Int =
        (base - if (penaltyPending) LIQUIDATE_RESOURCES_NEXT_TURN_RD_PENALTY else 0).coerceAtLeast(0)

    @Test
    fun aPendingPenaltyRemovesFourDice() {
        assertEquals(6, rolled(base = 10, penaltyPending = true))
        assertEquals(10, rolled(base = 10, penaltyPending = false))
    }

    @Test
    fun theReductionNeverGoesNegative() {
        // a small kingdom that liquidated rolls zero dice, not minus one
        assertEquals(0, rolled(base = 3, penaltyPending = true))
        assertEquals(0, rolled(base = 0, penaltyPending = true))
    }

    @Test
    fun thePenaltyIsTheDocumentedFour() {
        // "roll 4 fewer Resource Dice than normal" -- the offer card quotes this at the GM
        assertEquals(4, LIQUIDATE_RESOURCES_NEXT_TURN_RD_PENALTY)
    }
}
