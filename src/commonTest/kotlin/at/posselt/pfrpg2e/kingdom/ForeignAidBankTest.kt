package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForeignAidBankTest {
    // dieValue = 10 keeps determineDegreeOfSuccess free of nat-1/nat-20 adjustments, isolating the bonus.
    private val neutralDie = 10

    @Test
    fun spendingAidLiftsAFailureToSuccess() {
        // total 14 vs DC 15 = failure; +2 aid -> 16 = success.
        assertEquals(DegreeOfSuccess.FAILURE, degreeAfterSpendingAid(dc = 15, total = 14, dieValue = neutralDie, bonus = 0))
        assertEquals(DegreeOfSuccess.SUCCESS, degreeAfterSpendingAid(dc = 15, total = 14, dieValue = neutralDie, bonus = 2))
    }

    @Test
    fun aidWouldImproveOnlyWhenItChangesTheDegree() {
        assertTrue(aidWouldImprove(dc = 15, total = 14, dieValue = neutralDie, bonus = 2))   // fail -> success
        assertFalse(aidWouldImprove(dc = 15, total = 14, dieValue = neutralDie, bonus = 0))  // no bonus
        assertFalse(aidWouldImprove(dc = 15, total = 20, dieValue = neutralDie, bonus = 2))  // already success, stays success
    }

    @Test
    fun bigAidCanReachCriticalSuccess() {
        // total 14 vs DC 5 (>= dc+10 = 15 after +1? here total already 14, +11 -> 25 >= 15) -> crit success.
        assertEquals(DegreeOfSuccess.CRITICAL_SUCCESS, degreeAfterSpendingAid(dc = 5, total = 14, dieValue = neutralDie, bonus = 11))
    }

    @Test
    fun expiryHonorsTheTurnWindow() {
        val bonus = BankedBonus(value = 2, source = "Mendev", gainedTurn = 3, expiresTurn = 5)
        assertFalse(bonus.isExpired(currentTurn = 5))  // still valid on the expiry turn
        assertTrue(bonus.isExpired(currentTurn = 6))   // lapsed after
        assertFalse(BankedBonus(2, "x", gainedTurn = 1).isExpired(currentTurn = 999))  // no expiry
    }

    @Test
    fun spendableFiltersOutExpiredBonuses() {
        val bonuses = listOf(
            BankedBonus(2, "a", gainedTurn = 1, expiresTurn = 4),
            BankedBonus(3, "b", gainedTurn = 2, expiresTurn = 10),
            BankedBonus(1, "c", gainedTurn = 3),  // never expires
        )
        assertEquals(listOf("b", "c"), bonuses.spendable(currentTurn = 5).map { it.source })
    }
}
