package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.armies.BattleStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WarEndConditionsTest {
    @Test
    fun victoryRaisesAndDefeatLowersStanding() {
        assertEquals(4, warStandingDelta(BattleStatus.VICTORY))
        assertEquals(-4, warStandingDelta(BattleStatus.DEFEAT))
        assertEquals(3, warStandingDelta(BattleStatus.VICTORY, victoryBonus = 3))
        assertEquals(-2, warStandingDelta(BattleStatus.DEFEAT, defeatPenalty = 2))
    }

    @Test
    fun unfinishedAndWithdrawnBattlesMoveNothing() {
        // A retreat is a survivable outcome in the army lifecycle, not a loss; an ACTIVE battle has
        // no result to price yet. Either mapped onto DEFEAT would penalise standing wrongly.
        assertEquals(0, warStandingDelta(BattleStatus.RETREAT))
        assertEquals(0, warStandingDelta(BattleStatus.ACTIVE))
    }

    private fun threat(faction: String?, outcome: ThreatOutcome) = ThreatState(faction, outcome)

    @Test
    fun peaceIsOfferedWhenTheLastOfSeveralThreatsFalls() {
        // The card's actual rule is about the LAST of several threats. A suite that only ever tests
        // single-threat wars passes against an implementation that only works for single-threat wars.
        val war = listOf(
            threat("pitax", ThreatOutcome.DEFEATED),
            threat("pitax", ThreatOutcome.DEFEATED),
        )
        assertTrue(peaceEligible(war, "pitax"))
    }

    @Test
    fun peaceWaitsWhileAnyLinkedThreatIsStillActive() {
        val war = listOf(
            threat("pitax", ThreatOutcome.DEFEATED),
            threat("pitax", ThreatOutcome.ACTIVE),
        )
        assertFalse(peaceEligible(war, "pitax"))
    }

    @Test
    fun anEvadedThreatDoesNotWedgeTheWarOpenForever() {
        // Nothing ever transitions EVADED/EXPIRED to DEFEATED, so requiring *every* linked threat to
        // be defeated would leave this faction at war permanently with no peace card, ever.
        val war = listOf(
            threat("pitax", ThreatOutcome.ENDED_OTHERWISE),
            threat("pitax", ThreatOutcome.DEFEATED),
        )
        assertTrue(peaceEligible(war, "pitax"))
    }

    @Test
    fun aWarThatOnlyExpiredIsNotAVictoryToDictateTermsFrom() {
        // Escalation maxed out and the invasion landed. The board is clear, but the kingdom did not
        // win, so it does not get a "sign peace, bump standing to the floor" card.
        val war = listOf(threat("pitax", ThreatOutcome.ENDED_OTHERWISE))
        assertFalse(peaceEligible(war, "pitax"))
    }

    @Test
    fun factionWithNoLinkedThreatsIsNotEligible() {
        val war = listOf(threat("mivon", ThreatOutcome.DEFEATED))
        assertFalse(peaceEligible(war, "brevoy"))
    }

    @Test
    fun unlinkedThreatsDoNotFuseIntoAPseudoFaction() {
        val war = listOf(
            threat(null, ThreatOutcome.DEFEATED),
            threat("", ThreatOutcome.DEFEATED),
        )
        assertFalse(peaceEligible(war, ""))
        assertFalse(peaceEligible(war, "   "))
    }

    @Test
    fun signPeaceRaisesStandingToTheFloorAndEndsTheWar() {
        val r = peaceOutcome(PeaceChoice.SIGN_PEACE, currentStanding = -3, standingFloor = 0, tributeRp = 10)
        assertEquals(3, r.standingDelta)
        assertEquals(0, r.rpGain)
        assertTrue(r.clearsFactionAtWar)
    }

    @Test
    fun signPeaceDoesNotLowerStandingAlreadyAboveTheFloor() {
        val r = peaceOutcome(PeaceChoice.SIGN_PEACE, currentStanding = 20, standingFloor = 0, tributeRp = 10)
        assertEquals(0, r.standingDelta)
        assertTrue(r.clearsFactionAtWar)
    }

    @Test
    fun signPeaceClampsAnOutOfRangeFloorSoTheLogStaysReplayable() {
        // Standing is clamped to [-100, 100] on write. Without clamping the floor here, standingLog
        // would record a delta far larger than the change actually stored.
        val r = peaceOutcome(PeaceChoice.SIGN_PEACE, currentStanding = 0, standingFloor = 1000, tributeRp = 0)
        assertEquals(100, r.standingDelta)
    }

    @Test
    fun demandTributeTradesStandingForRpAndEndsTheWar() {
        val r = peaceOutcome(PeaceChoice.DEMAND_TRIBUTE, currentStanding = 1, standingFloor = 0, tributeRp = 8)
        assertEquals(-8, r.standingDelta)
        assertEquals(8, r.rpGain)
        assertTrue(r.clearsFactionAtWar)
    }

    @Test
    fun demandTributeHonoursACustomPenalty() {
        val r = peaceOutcome(
            PeaceChoice.DEMAND_TRIBUTE,
            currentStanding = 1,
            standingFloor = 0,
            tributeRp = 8,
            tributePenalty = 3,
        )
        assertEquals(-3, r.standingDelta)
    }

    @Test
    fun tributeNeverPaysNegativeResourcePoints() {
        val r = peaceOutcome(PeaceChoice.DEMAND_TRIBUTE, currentStanding = 1, standingFloor = 0, tributeRp = -5)
        assertEquals(0, r.rpGain)
    }

    @Test
    fun ignoreLeavesTheWarRunning() {
        val r = peaceOutcome(PeaceChoice.IGNORE, currentStanding = 1, standingFloor = 0, tributeRp = 8)
        assertEquals(0, r.standingDelta)
        assertEquals(0, r.rpGain)
        assertFalse(r.clearsFactionAtWar)
    }

    @Test
    fun kingdomStaysAtWarWhileAnyOtherFactionStillIs() {
        // Signing peace with one of two enemies must not stop the kingdom-wide +1 unrest per turn.
        assertTrue(kingdomRemainsAtWar(listOf(false, true)))
        assertFalse(kingdomRemainsAtWar(listOf(false, false)))
        assertFalse(kingdomRemainsAtWar(emptyList()))
    }
}
