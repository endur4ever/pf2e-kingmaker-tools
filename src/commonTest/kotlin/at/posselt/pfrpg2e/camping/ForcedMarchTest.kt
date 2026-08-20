package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForcedMarchTest {
    private val day = FORCED_MARCH_DAY_SECONDS

    @Test
    fun daysAreWholeMarchingDays() {
        assertEquals(0, forcedMarchDays(0))
        assertEquals(0, forcedMarchDays(day - 1))
        assertEquals(1, forcedMarchDays(day))
        assertEquals(3, forcedMarchDays(day * 3 + 5))
    }

    @Test
    fun aNegativeAccumulationIsTreatedAsNoneRatherThanWrapping() {
        assertEquals(0, forcedMarchDays(-day))
    }

    @Test
    fun thePartyMarchesAtThePaceOfWhoeverTiresFirst() {
        assertEquals(2, forcedMarchMaxDays(listOf(5, 2, 4)))
    }

    @Test
    fun aFeebleCharacterStillGetsOneDay() {
        // A negative Constitution modifier must not make the limit 0 or negative, which would put
        // the party permanently over the limit the instant they set out.
        assertEquals(1, forcedMarchMaxDays(listOf(-2)))
        assertEquals(1, forcedMarchMaxDays(listOf(0, 3)))
    }

    @Test
    fun anEmptyPartyHasNoLimitToExceed() {
        assertEquals(0, forcedMarchMaxDays(emptyList()))
        // ...and with no limit, nothing is ever over it -- otherwise an empty camp would nag.
        assertFalse(forcedMarchOverLimit(days = 5, maxDays = 0))
    }

    @Test
    fun reachingTheLimitIsNotYetExceedingIt() {
        // The limit is how many days CAN be marched, so day 2 of 2 is still fine.
        assertFalse(forcedMarchOverLimit(days = 2, maxDays = 2))
        assertTrue(forcedMarchOverLimit(days = 3, maxDays = 2))
    }

    @Test
    fun theOfferFiresOnTheCrossingOnly() {
        // Edge-triggered: this is what makes the GM offer idempotent.
        assertTrue(crossedForcedMarchLimit(previousDays = 2, newDays = 3, maxDays = 2))
    }

    @Test
    fun marchingOnWhileAlreadyExhaustedDoesNotReOffer() {
        // Without this, every further night of marching would whisper the same fatigue offer again.
        assertFalse(crossedForcedMarchLimit(previousDays = 3, newDays = 4, maxDays = 2))
        assertFalse(crossedForcedMarchLimit(previousDays = 9, newDays = 10, maxDays = 2))
    }

    @Test
    fun aRestThatClearsTheCounterDoesNotCountAsCrossing() {
        assertFalse(crossedForcedMarchLimit(previousDays = 5, newDays = 0, maxDays = 2))
    }

    @Test
    fun stayingWithinTheLimitNeverOffers() {
        assertFalse(crossedForcedMarchLimit(previousDays = 0, newDays = 1, maxDays = 3))
        assertFalse(crossedForcedMarchLimit(previousDays = 1, newDays = 2, maxDays = 3))
    }
}
