package at.posselt.pfrpg2e.kingdom

import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiquidateResourcesTest {
    private fun kingdom(penaltyPending: Boolean? = null): KingdomData =
        unsafeJso<dynamic> { liquidateResourcesPenaltyNextTurn = penaltyPending }
            .unsafeCast<KingdomData>()

    @Test
    fun thePendingPenaltyDoublesAsTheOncePerTurnMarker() {
        // Set on use, cleared at End Turn when the penalty is spent -- so within a turn its presence
        // means exactly "already liquidated", without a second persisted field for the same fact.
        assertFalse(kingdom().liquidateUsedThisTurn())
        assertFalse(kingdom(penaltyPending = false).liquidateUsedThisTurn())
        assertTrue(kingdom(penaltyPending = true).liquidateUsedThisTurn())
    }

    @Test
    fun theFeatLeavesExactlyOneResourcePoint() {
        // "you may instead reduce your RP to 1" -- not 0, and not the full expense refunded.
        assertEquals(1, liquidatedRp())
    }

    @Test
    fun thePenaltyIsFourResourceDice() {
        assertEquals(4, LIQUIDATE_RESOURCES_NEXT_TURN_RD_PENALTY)
    }

    @Test
    fun theGateOnlyOpensWhenTheExpenseActuallyZeroesTheTreasury() {
        // An expense you could afford is not an emergency, and a kingdom already at 0 was not
        // reduced to 0 BY this expense.
        assertTrue(canLiquidateResources(currentRp = 3, expense = 5, alreadyUsedThisTurn = false))
        assertTrue(canLiquidateResources(currentRp = 5, expense = 5, alreadyUsedThisTurn = false))
        assertFalse(canLiquidateResources(currentRp = 10, expense = 5, alreadyUsedThisTurn = false))
        assertFalse(canLiquidateResources(currentRp = 3, expense = 5, alreadyUsedThisTurn = true))
    }
}
