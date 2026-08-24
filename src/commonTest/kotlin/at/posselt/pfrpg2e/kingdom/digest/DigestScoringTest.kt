package at.posselt.pfrpg2e.kingdom.digest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Covers the §7.1 scoring and selection cases of the meanwhile-digest plan. */
class DigestScoringTest {
    private fun event(
        id: String = "e1",
        kind: String = "caravan",
        sourceName: String = "Olegs Caravan",
        magnitudeNorm: Double = 0.5,
        relevance: Double = 0.5,
    ) = DigestEvent(
        id = id, kind = kind, sourceName = sourceName, magnitudeNorm = magnitudeNorm,
        relevance = relevance, labelKey = "kingdom.meanwhile.beat",
    )

    @Test
    fun theDefaultWeightsScoreMagnitudePlusOneAndAHalfTimesRelevance() {
        assertEquals(1.1, interestScore(event(magnitudeNorm = 0.5, relevance = 0.4)), 1e-9)
        assertEquals(0.0, interestScore(event(magnitudeNorm = 0.0, relevance = 0.0)), 1e-9)
        assertEquals(2.5, interestScore(event(magnitudeNorm = 1.0, relevance = 1.0)), 1e-9)
    }

    @Test
    fun customWeightsRebalanceTheScore() {
        val magnitudeHeavy = DigestWeights(magnitude = 2.0, relevance = 0.0)
        assertEquals(1.0, interestScore(event(magnitudeNorm = 0.5, relevance = 0.9), magnitudeHeavy), 1e-9)
    }

    @Test
    fun magnitudeOutsideTheUnitRangeIsClampedFromBothSides() {
        // A bad adapter must not let one runaway event drown the digest: 7.0 scores as 1.0.
        assertEquals(1.0, interestScore(event(magnitudeNorm = 7.0, relevance = 0.0)), 1e-9)
        assertEquals(0.0, interestScore(event(magnitudeNorm = -3.0, relevance = 0.0)), 1e-9)
        // The in-range edges pass through untouched.
        assertEquals(1.0, interestScore(event(magnitudeNorm = 1.0, relevance = 0.0)), 1e-9)
        assertEquals(0.0, interestScore(event(magnitudeNorm = 0.0, relevance = 0.0)), 1e-9)
    }

    @Test
    fun relevanceOutsideTheUnitRangeIsClampedFromBothSides() {
        assertEquals(1.5, interestScore(event(magnitudeNorm = 0.0, relevance = 9.0)), 1e-9)
        assertEquals(0.0, interestScore(event(magnitudeNorm = 0.0, relevance = -1.0)), 1e-9)
        assertEquals(1.5, interestScore(event(magnitudeNorm = 0.0, relevance = 1.0)), 1e-9)
        assertEquals(0.0, interestScore(event(magnitudeNorm = 0.0, relevance = 0.0)), 1e-9)
    }

    @Test
    fun previousBeatIdsExcludesExactlyThoseIds() {
        // The last-turn recap already told "b"; the two chat beats must never repeat each other.
        val a = event(id = "a", kind = "caravan", sourceName = "A")
        val b = event(id = "b", kind = "clock", sourceName = "B")
        val c = event(id = "c", kind = "weather", sourceName = "C")
        val out = selectDigestBeats(listOf(a, b, c), previousBeatIds = setOf("b"))
        assertEquals(listOf("a", "c"), out.map { it.id })
    }

    @Test
    fun sameKindAndSourceDedupesToTheHigherScoringEvent() {
        // One caravan cannot produce two beats, whichever order its events arrive in.
        val weak = event(id = "weak", magnitudeNorm = 0.2, relevance = 0.2)
        val strong = event(id = "strong", magnitudeNorm = 0.9, relevance = 0.9)
        assertEquals(listOf("strong"), selectDigestBeats(listOf(weak, strong)).map { it.id })
        assertEquals(listOf("strong"), selectDigestBeats(listOf(strong, weak)).map { it.id })
    }

    @Test
    fun anExactScoreTieDedupesToTheEarlierEvent() {
        // Input order is chronological, so on equal interest the first-reported event wins.
        val first = event(id = "first")
        val second = event(id = "second")
        assertEquals(listOf("first"), selectDigestBeats(listOf(first, second)).map { it.id })
    }

    @Test
    fun theSameSourceInDifferentKindsKeepsBothEvents() {
        // Pitax drifting hostile AND raiding a caravan are two beats, not one.
        val drift = event(id = "drift", kind = "factionDrift", sourceName = "Pitax")
        val raid = event(id = "raid", kind = "caravan", sourceName = "Pitax")
        assertEquals(2, selectDigestBeats(listOf(drift, raid)).size)
    }

    @Test
    fun theDefaultCapKeepsOnlyTheFourHighestScoringBeats() {
        val events = (1..6).map {
            event(id = "e" + it, kind = "clock", sourceName = "c" + it, magnitudeNorm = it / 10.0, relevance = 0.0)
        }
        assertEquals(listOf("e6", "e5", "e4", "e3"), selectDigestBeats(events).map { it.id })
    }

    @Test
    fun aCapOfZeroOrBelowReturnsEmptyAndACapOfOneKeepsOne() {
        val events = listOf(event(id = "a"))
        assertTrue(selectDigestBeats(events, cap = 0).isEmpty())
        assertTrue(selectDigestBeats(events, cap = -1).isEmpty())
        assertEquals(1, selectDigestBeats(events, cap = 1).size)
    }

    @Test
    fun aCapBeyondTheInputKeepsEverything() {
        val a = event(id = "a", kind = "caravan", sourceName = "A")
        val b = event(id = "b", kind = "weather", sourceName = "B")
        assertEquals(2, selectDigestBeats(listOf(a, b), cap = 10).size)
    }

    @Test
    fun orderingIsScoreDescendingWithInputOrderTieStability() {
        val a = event(id = "a", kind = "weather", sourceName = "A", magnitudeNorm = 0.2, relevance = 0.2)
        val b = event(id = "b", kind = "caravan", sourceName = "B", magnitudeNorm = 0.8, relevance = 0.8)
        val c = event(id = "c", kind = "clock", sourceName = "C", magnitudeNorm = 0.2, relevance = 0.2)
        val d = event(id = "d", kind = "warThreat", sourceName = "D", magnitudeNorm = 0.5, relevance = 0.6)
        // Scores: b 2.0, d 1.4, a 0.5, c 0.5 — a before c because a came first in the input.
        assertEquals(listOf(b, d, a, c), selectDigestBeats(listOf(a, b, c, d)))
    }

    @Test
    fun selectionHonoursThePassedWeights() {
        val big = event(id = "big", kind = "weather", sourceName = "Storm", magnitudeNorm = 1.0, relevance = 0.0)
        val near = event(id = "near", kind = "caravan", sourceName = "Olegs", magnitudeNorm = 0.0, relevance = 1.0)
        // Default weights favour relevance; magnitude-only weights flip the order.
        assertEquals(listOf("near", "big"), selectDigestBeats(listOf(big, near)).map { it.id })
        val magnitudeOnly = DigestWeights(magnitude = 1.0, relevance = 0.0)
        assertEquals(listOf("big", "near"), selectDigestBeats(listOf(big, near), weights = magnitudeOnly).map { it.id })
    }

    @Test
    fun aFullRaidLossScoresOne() {
        assertEquals(1.0, caravanMagnitude(cargoLost = 10, deliveredAmount = 0, cargoAmount = 10, raided = true), 1e-9)
    }

    @Test
    fun aPartialRaidLossIsProportionalToThePreTickCargo() {
        // The denominator is what the cargo WAS: losing 2 of 10 is not losing 2 of 2.
        assertEquals(0.2, caravanMagnitude(cargoLost = 2, deliveredAmount = 0, cargoAmount = 10, raided = true), 1e-9)
        assertEquals(1.0, caravanMagnitude(cargoLost = 2, deliveredAmount = 0, cargoAmount = 2, raided = true), 1e-9)
    }

    @Test
    fun aDeliveryScoresByDeliveredAmountNotCargoLost() {
        assertEquals(0.5, caravanMagnitude(cargoLost = 9, deliveredAmount = 5, cargoAmount = 10, raided = false), 1e-9)
    }

    @Test
    fun zeroCargoDoesNotThrowAndStaysInsideTheUnitRange() {
        // The max-with-1 guard: an empty caravan scores 0, not divide-by-zero.
        assertEquals(0.0, caravanMagnitude(cargoLost = 0, deliveredAmount = 0, cargoAmount = 0, raided = true), 1e-9)
        // Corrupt data claiming a loss from an empty caravan clamps at 1 rather than exceeding it.
        assertEquals(1.0, caravanMagnitude(cargoLost = 3, deliveredAmount = 0, cargoAmount = 0, raided = true), 1e-9)
    }

    @Test
    fun negativeInputsClampToZero() {
        assertEquals(0.0, caravanMagnitude(cargoLost = -5, deliveredAmount = 0, cargoAmount = 10, raided = true), 1e-9)
        assertEquals(0.0, caravanMagnitude(cargoLost = 0, deliveredAmount = -5, cargoAmount = 10, raided = false), 1e-9)
        assertEquals(0.0, caravanMagnitude(cargoLost = 0, deliveredAmount = 0, cargoAmount = -10, raided = false), 1e-9)
    }

    @Test
    fun thePipelineOrderIsFilterThenDedupThenCap() {
        // One fixture that fails if any two stages swap. The (kind, source) duplicate pair BOTH
        // score near the top: cap-before-dedup would spend a cap slot on the duplicate and emit
        // three beats instead of four; filter-after-dedup would let the previously-shown top scorer
        // win its dedup group and then vanish at the filter, dropping its whole source. Only
        // filter -> dedup -> cap yields the four expected beats. (An earlier fixture scored the
        // duplicate LOWEST, which the cap removes in either order -- it caught nothing.)
        val shown = event(id = "shown", kind = "caravan", sourceName = "Pitax", magnitudeNorm = 1.0, relevance = 1.0)
        val fresh = event(id = "fresh", kind = "caravan", sourceName = "Pitax", magnitudeNorm = 0.95, relevance = 1.0)
        val dupHigh = event(id = "dupHigh", kind = "caravan", sourceName = "Pitax", magnitudeNorm = 0.9, relevance = 1.0)
        val c1 = event(id = "c1", kind = "clock", sourceName = "StagLord", magnitudeNorm = 0.8, relevance = 1.0)
        val c2 = event(id = "c2", kind = "threat", sourceName = "Hargulka", magnitudeNorm = 0.7, relevance = 1.0)
        val c3 = event(id = "c3", kind = "faction", sourceName = "Mivon", magnitudeNorm = 0.6, relevance = 1.0)
        val out = selectDigestBeats(
            listOf(shown, fresh, dupHigh, c1, c2, c3),
            previousBeatIds = setOf("shown"),
            cap = 4,
        )
        assertEquals(listOf("fresh", "c1", "c2", "c3"), out.map { it.id })
        assertEquals(4, out.size, "a cap slot must never be spent on a row dedup would remove")
    }
}
