package at.posselt.pfrpg2e.data.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FactionRelationsTest {

    // --- attitudeFor band mapping ---

    @Test
    fun nullStandingIsIndifferent() {
        assertEquals(FactionAttitude.INDIFFERENT, attitudeFor(null))
    }

    @Test
    fun attitudeBandBoundaries() {
        assertEquals(FactionAttitude.HOSTILE, attitudeFor(-100))
        assertEquals(FactionAttitude.HOSTILE, attitudeFor(-50))
        assertEquals(FactionAttitude.UNFRIENDLY, attitudeFor(-49))
        assertEquals(FactionAttitude.UNFRIENDLY, attitudeFor(-15))
        assertEquals(FactionAttitude.INDIFFERENT, attitudeFor(-14))
        assertEquals(FactionAttitude.INDIFFERENT, attitudeFor(0))
        assertEquals(FactionAttitude.INDIFFERENT, attitudeFor(14))
        assertEquals(FactionAttitude.FRIENDLY, attitudeFor(15))
        assertEquals(FactionAttitude.FRIENDLY, attitudeFor(49))
        assertEquals(FactionAttitude.HELPFUL, attitudeFor(50))
        assertEquals(FactionAttitude.HELPFUL, attitudeFor(100))
    }

    // --- applyStandingDelta ---

    @Test
    fun deltaClampsAtUpperBound() {
        assertEquals(MAX_FACTION_STANDING, applyStandingDelta(95, 20))
    }

    @Test
    fun deltaClampsAtLowerBound() {
        assertEquals(MIN_FACTION_STANDING, applyStandingDelta(-95, -20))
    }

    @Test
    fun deltaFromNullUsesDefault() {
        assertEquals(10, applyStandingDelta(null, 10))
        assertEquals(-10, applyStandingDelta(null, -10))
    }

    @Test
    fun sequentialDeltasAccumulate() {
        val afterFirst = applyStandingDelta(0, 20)
        val afterSecond = applyStandingDelta(afterFirst, -5)
        assertEquals(15, afterSecond)
    }

    // --- shouldOfferWarThreat: only on the crossing into Hostile ---

    @Test
    fun warThreatOffersOnCrossingIntoHostile() {
        assertTrue(shouldOfferWarThreat(-40, applyStandingDelta(-40, -15))) // -40 -> -55: Unfriendly -> Hostile
    }

    @Test
    fun warThreatDoesNotRepeatWhenAlreadyHostile() {
        assertFalse(shouldOfferWarThreat(-60, applyStandingDelta(-60, -5))) // already Hostile
    }

    @Test
    fun warThreatNotOfferedWhenStayingAboveHostile() {
        assertFalse(shouldOfferWarThreat(0, applyStandingDelta(0, -10))) // Indifferent -> Indifferent
    }

    // --- shouldOfferDiplomacyQuest: only on the crossing into Friendly+ ---

    @Test
    fun diplomacyQuestOffersOnCrossingIntoFriendly() {
        assertTrue(shouldOfferDiplomacyQuest(0, applyStandingDelta(0, 20))) // 0 -> 20: Indifferent -> Friendly
    }

    @Test
    fun diplomacyQuestDoesNotRepeatWhenAlreadyFriendly() {
        assertFalse(shouldOfferDiplomacyQuest(30, applyStandingDelta(30, 10))) // Friendly -> Friendly/Helpful
    }

    @Test
    fun diplomacyQuestNotOfferedWhenStayingBelowFriendly() {
        assertFalse(shouldOfferDiplomacyQuest(0, applyStandingDelta(0, 5))) // Indifferent -> Indifferent
    }
}
