package at.posselt.pfrpg2e.kingdom.deeds

import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementSizeType
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

    // ── Phase 2: the full catalog's detectors ─────────────────────────────────────────────────

    private fun fires(detectionId: String, inputs: DeedInputs): Boolean =
        deedDetectors.getValue(detectionId).firesFor(inputs)

    @Test
    fun scaleDetectorsFireOnTheThresholdNotBelowIt() {
        assertFalse(fires("size-25", DeedInputs(size = 24)))
        assertTrue(fires("size-25", DeedInputs(size = 25)))
        assertFalse(fires("size-50", DeedInputs(size = 49)))
        assertTrue(fires("size-100", DeedInputs(size = 100)))
        assertFalse(fires("level-10", DeedInputs(level = 9)))
        assertTrue(fires("level-20", DeedInputs(level = 20)))
        assertFalse(fires("survived-fifty-turns", DeedInputs(turn = 49)))
        assertTrue(fires("survived-fifty-turns", DeedInputs(turn = 50)))
    }

    @Test
    fun settlementDetectorsReadTheSizeDistribution() {
        val townOnly = DeedInputs(settlementSizes = listOf(SettlementSizeType.TOWN))
        assertTrue(fires("first-settlement", townOnly))
        assertTrue(fires("first-town", townOnly))
        assertFalse(fires("first-city", townOnly))
        val withCity = DeedInputs(settlementSizes = listOf(SettlementSizeType.VILLAGE, SettlementSizeType.CITY))
        assertTrue(fires("first-city", withCity))
        assertFalse(fires("first-metropolis", withCity))
        assertFalse(fires("first-settlement", DeedInputs()))
    }

    @Test
    fun allSettlementsRoadedNeedsThreeAndFullCoverage() {
        val three = List(3) { SettlementSizeType.VILLAGE }
        assertTrue(fires("all-settlements-roaded", DeedInputs(settlementSizes = three, settlementsRoadedToCapital = 3)))
        assertFalse(fires("all-settlements-roaded", DeedInputs(settlementSizes = three, settlementsRoadedToCapital = 2)))
        // two settlements both roaded is not the achievement, however complete
        assertFalse(fires("all-settlements-roaded", DeedInputs(
            settlementSizes = List(2) { SettlementSizeType.VILLAGE }, settlementsRoadedToCapital = 2)))
    }

    @Test
    fun fameMaxStaysQuietWhenTheCapIsUnknown() {
        assertFalse(fires("fame-max", DeedInputs(fame = 3, fameMax = 0)))
        assertFalse(fires("fame-max", DeedInputs(fame = 2, fameMax = 3)))
        assertTrue(fires("fame-max", DeedInputs(fame = 3, fameMax = 3)))
    }

    @Test
    fun recoveryDeedsRequireEvidenceOfTheCrisis() {
        // a kingdom that never had ruin cannot claim the deed for having none
        assertFalse(fires("ruin-free", DeedInputs(ruinTotal = 0, history = listOf(DeedHistoryPoint(1, 0, 0)))))
        assertTrue(fires("ruin-free", DeedInputs(ruinTotal = 0, history = listOf(DeedHistoryPoint(1, 0, 4)))))
        assertFalse(fires("ruin-free", DeedInputs(ruinTotal = 1, history = listOf(DeedHistoryPoint(1, 0, 4)))))
        assertFalse(fires("unrest-zero-after-crisis", DeedInputs(unrest = 0, history = listOf(DeedHistoryPoint(1, 9, 0)))))
        assertTrue(fires("unrest-zero-after-crisis", DeedInputs(unrest = 0, history = listOf(DeedHistoryPoint(1, 10, 0)))))
        assertFalse(fires("unrest-zero-after-crisis", DeedInputs(unrest = 1, history = listOf(DeedHistoryPoint(1, 10, 0)))))
    }

    @Test
    fun tenQuietTurnsNeedsTenEntriesAndNoRise() {
        fun calm(n: Int) = (1..n).map { DeedHistoryPoint(it, unrest = 5, ruinTotal = 0) }
        // a short history is FALSE, not vacuously true
        assertFalse(hasTenQuietTurns(emptyList()))
        assertFalse(hasTenQuietTurns(calm(9)))
        assertTrue(hasTenQuietTurns(calm(10)))
        // one rise inside the only window breaks it
        val withRise = calm(10).toMutableList().also { it[4] = DeedHistoryPoint(5, unrest = 9, ruinTotal = 0) }
        assertFalse(hasTenQuietTurns(withRise))
        // falling unrest is quiet; a later ten-window still qualifies
        val recovering = (1..12).map { DeedHistoryPoint(it, unrest = maxOf(0, 12 - it), ruinTotal = 0) }
        assertTrue(hasTenQuietTurns(recovering))
    }

    @Test
    fun commerceAndWarDetectors() {
        assertFalse(fires("safe-trade-route", DeedInputs(consecutiveSafeCaravanTurns = 4)))
        assertTrue(fires("safe-trade-route", DeedInputs(consecutiveSafeCaravanTurns = 5)))
        assertTrue(fires("first-battle-won", DeedInputs(armiesWon = 1)))
        assertFalse(fires("three-trade-agreements", DeedInputs(tradeAgreements = 2)))
        assertTrue(fires("three-trade-agreements", DeedInputs(tradeAgreements = 3)))
        assertTrue(fires("two-regions-claimed", DeedInputs(regionsFullyClaimed = 2)))
        assertFalse(fires("two-regions-claimed", DeedInputs(regionsFullyClaimed = 1)))
    }

    @Test
    fun everyStarterDetectionIdHasADetector() {
        // the plan's 21 rows; a catalog file naming an id with no detector ships a dead deed
        val starter = listOf(
            "first-settlement", "first-town", "first-city", "first-metropolis",
            "road-to-capital", "all-settlements-roaded", "region-fully-claimed", "two-regions-claimed",
            "size-25", "size-50", "size-100", "level-10", "level-20", "fame-max",
            "ruin-free", "unrest-zero-after-crisis", "ten-quiet-turns", "safe-trade-route",
            "first-battle-won", "three-trade-agreements", "survived-fifty-turns",
        )
        assertEquals(21, starter.size)
        starter.forEach { assertTrue(it in deedDetectors, "no detector for $it") }
        // and nothing extra: a detector with no catalog row is code nothing can reach
        assertEquals(starter.toSet(), deedDetectors.keys)
    }

    @Test
    fun everySettlementRoadedCountsTheCapitalItself() {
        // the capital is trivially connected to itself; a numerator that excludes it can never
        // equal a total that includes it, which made this deed unreachable
        val three = List(3) { SettlementSizeType.VILLAGE }
        assertTrue(fires("all-settlements-roaded", DeedInputs(
            settlementSizes = three,
            settlementsRoadedToCapital = 3,
        )))
        assertFalse(fires("all-settlements-roaded", DeedInputs(
            settlementSizes = three,
            settlementsRoadedToCapital = 2,
        )))
    }
}
