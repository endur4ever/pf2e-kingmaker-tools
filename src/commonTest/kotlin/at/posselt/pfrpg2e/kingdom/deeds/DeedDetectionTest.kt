package at.posselt.pfrpg2e.kingdom.deeds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Covers the phase-1 detection core the shipped MilestoneOffers pair is retrofitted onto. */
class DeedDetectionTest {
    private val roadEntry = DeedCatalogEntry("connect-settlement-to-capital-via-roads", "road-to-capital")
    private val regionEntry = DeedCatalogEntry("claim-all-hexes-in-a-region", "region-fully-claimed")

    @Test
    fun theRoadDetectorFiresAtOneConnectionAndNotAtZero() {
        val d = deedDetectors.getValue("road-to-capital")
        assertTrue(d.firesFor(DeedInputs(settlementsRoadedToCapital = 1)))
        assertFalse(d.firesFor(DeedInputs(settlementsRoadedToCapital = 0)))
    }

    @Test
    fun theRegionDetectorFiresAtOneRegionAndNotAtZero() {
        val d = deedDetectors.getValue("region-fully-claimed")
        assertTrue(d.firesFor(DeedInputs(regionsFullyClaimed = 1)))
        assertFalse(d.firesFor(DeedInputs(regionsFullyClaimed = 0)))
    }

    @Test
    fun anAnsweredDeedIsSuppressedWhileItsSiblingStillFires() {
        // Answered means awarded OR dismissed: a GM who declined must not see the card again.
        val fired = undetectedDeeds(
            listOf(roadEntry, regionEntry),
            answeredIds = setOf(roadEntry.id),
            DeedInputs(settlementsRoadedToCapital = 1, regionsFullyClaimed = 1),
        )
        assertEquals(listOf(regionEntry.id), fired)
    }

    @Test
    fun aDetectorThatDoesNotFireProducesNothing() {
        val fired = undetectedDeeds(
            listOf(roadEntry, regionEntry),
            emptySet(),
            DeedInputs(settlementsRoadedToCapital = 1, regionsFullyClaimed = 0),
        )
        assertEquals(listOf(roadEntry.id), fired)
    }

    @Test
    fun anUnknownDetectionIdIsSkippedAndTheRestStillFire() {
        // A catalog newer than the build must not take down the whole End Turn evaluation.
        val future = DeedCatalogEntry("some-future-deed", "teleportation-network")
        val fired = undetectedDeeds(
            listOf(future, roadEntry),
            emptySet(),
            DeedInputs(settlementsRoadedToCapital = 1),
        )
        assertEquals(listOf(roadEntry.id), fired)
    }

    @Test
    fun aNullDetectionIdNeverFires() {
        // A manually-ticked milestone has no detector and must never auto-offer.
        val manual = DeedCatalogEntry("defeat-the-stag-lord", detectionId = null)
        assertTrue(undetectedDeeds(listOf(manual), emptySet(), DeedInputs(settlementsRoadedToCapital = 9)).isEmpty())
    }

    @Test
    fun catalogOrderIsPreserved() {
        val fired = undetectedDeeds(
            listOf(regionEntry, roadEntry),
            emptySet(),
            DeedInputs(settlementsRoadedToCapital = 1, regionsFullyClaimed = 1),
        )
        assertEquals(listOf(regionEntry.id, roadEntry.id), fired)
    }

    @Test
    fun bothAnsweredYieldsNothingHoweverLoudTheInputs() {
        val fired = undetectedDeeds(
            listOf(roadEntry, regionEntry),
            setOf(roadEntry.id, regionEntry.id),
            DeedInputs(settlementsRoadedToCapital = 5, regionsFullyClaimed = 5),
        )
        assertTrue(fired.isEmpty())
    }
}
