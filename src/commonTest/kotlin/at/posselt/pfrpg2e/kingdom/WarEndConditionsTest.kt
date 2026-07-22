package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WarEndConditionsTest {
    @Test
    fun victoryRaisesAndDefeatLowersStanding() {
        assertEquals(1, warStandingDelta(BattleResult.VICTORY))
        assertEquals(-1, warStandingDelta(BattleResult.DEFEAT))
        assertEquals(3, warStandingDelta(BattleResult.VICTORY, victoryBonus = 3))
        assertEquals(-2, warStandingDelta(BattleResult.DEFEAT, defeatPenalty = 2))
    }

    private val threats = listOf(
        ThreatState(enemyFactionId = "pitax", defeated = true),
        ThreatState(enemyFactionId = "pitax", defeated = false),
        ThreatState(enemyFactionId = "mivon", defeated = true),
        ThreatState(enemyFactionId = null, defeated = false),
    )

    @Test
    fun peaceNeedsEveryLinkedThreatDefeated() {
        assertFalse(peaceEligible(threats, "pitax"))  // one pitax threat still active
        assertTrue(peaceEligible(threats, "mivon"))   // sole mivon threat defeated
    }

    @Test
    fun factionWithNoLinkedThreatsIsNotEligible() {
        assertFalse(peaceEligible(threats, "brevoy"))  // no threats reference brevoy
    }

    @Test
    fun signPeaceBumpsStandingUpToTheFloorAndEndsTheWar() {
        val r = peaceOutcome(PeaceChoice.SIGN_PEACE, currentStanding = -3, standingFloor = 0, tributeRp = 10)
        assertEquals(3, r.standingDelta)  // -3 -> 0
        assertEquals(0, r.rpGain)
        assertTrue(r.clearsAtWar)
    }

    @Test
    fun signPeaceDoesNotLowerStandingAlreadyAboveTheFloor() {
        val r = peaceOutcome(PeaceChoice.SIGN_PEACE, currentStanding = 2, standingFloor = 0, tributeRp = 10)
        assertEquals(0, r.standingDelta)  // no negative bump
        assertTrue(r.clearsAtWar)
    }

    @Test
    fun demandTributeTradesStandingForRpButStillEndsTheWar() {
        val r = peaceOutcome(PeaceChoice.DEMAND_TRIBUTE, currentStanding = 1, standingFloor = 0, tributeRp = 8)
        assertEquals(-2, r.standingDelta)
        assertEquals(8, r.rpGain)
        assertTrue(r.clearsAtWar)
    }

    @Test
    fun ignoreLeavesTheWarRunning() {
        val r = peaceOutcome(PeaceChoice.IGNORE, currentStanding = 1, standingFloor = 0, tributeRp = 8)
        assertEquals(0, r.standingDelta)
        assertEquals(0, r.rpGain)
        assertFalse(r.clearsAtWar)
    }
}
