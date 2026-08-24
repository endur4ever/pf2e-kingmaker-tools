package at.posselt.pfrpg2e.kingdom.mapdynamism

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Covers the §7.1 pure-logic cases of the map-dynamism plan. */
class MapDynamismTest {
    private fun graph(vararg edges: Pair<String, String>): (String) -> Set<String> {
        val adjacency = mutableMapOf<String, MutableSet<String>>()
        for ((a, b) in edges) {
            adjacency.getOrPut(a) { mutableSetOf() }.add(b)
            adjacency.getOrPut(b) { mutableSetOf() }.add(a)
        }
        return { adjacency[it] ?: emptySet() }
    }

    // A - B - C - D: the smallest fixture where the first step differs from the target.
    private val line = graph("A" to "B", "B" to "C", "C" to "D")

    @Test
    fun aKeptTrackerPreservesItsOriginalClearedSinceTurn() {
        // The reconcile runs every turn; a timer that restarted on each pass would never elapse.
        val existing = listOf(RewildTracker("5.10", clearedSinceTurn = 3))
        assertEquals(existing, updateRewildTrackers(existing, setOf("5.10"), currentTurn = 9))
    }

    @Test
    fun aTrackerWhoseHexLeftTheSetIsDropped() {
        // Claimed, re-wilded, or un-cleared -- in every case the timer resets by disappearing.
        val existing = listOf(RewildTracker("5.10", clearedSinceTurn = 3))
        assertTrue(updateRewildTrackers(existing, emptySet(), currentTurn = 9).isEmpty())
    }

    @Test
    fun newHexesStartTrackersAtTheCurrentTurnAfterSurvivorsInSortedOrder() {
        // Survivors keep their input order, new hexes append sorted: the persisted flag must be
        // byte-identical between the preview tick and the commit tick.
        val existing = listOf(
            RewildTracker("9.9", clearedSinceTurn = 4),
            RewildTracker("0.5", clearedSinceTurn = 2),
        )
        val out = updateRewildTrackers(existing, setOf("9.9", "0.5", "7.7", "1.1"), currentTurn = 6)
        assertEquals(
            listOf(
                RewildTracker("9.9", clearedSinceTurn = 4),
                RewildTracker("0.5", clearedSinceTurn = 2),
                RewildTracker("1.1", clearedSinceTurn = 6),
                RewildTracker("7.7", clearedSinceTurn = 6),
            ),
            out,
        )
    }

    @Test
    fun aTrackerBecomesACandidateAtExactlyTheDelayAndNotOneTurnShort() {
        val tracker = RewildTracker("5.10", clearedSinceTurn = 3)
        assertEquals(listOf("5.10"), rewildCandidates(listOf(tracker), currentTurn = 6, delayTurns = 3))
        assertTrue(rewildCandidates(listOf(tracker), currentTurn = 5, delayTurns = 3).isEmpty())
    }

    @Test
    fun aDelayOfZeroOrBelowDisablesRewildingEvenForAncientTrackers() {
        // The settings dial reads "0 = never" -- zero must not decay into "instantly".
        val ancient = RewildTracker("5.10", clearedSinceTurn = -100)
        assertTrue(rewildCandidates(listOf(ancient), currentTurn = 100, delayTurns = 0).isEmpty())
        assertTrue(rewildCandidates(listOf(ancient), currentTurn = 100, delayTurns = -1).isEmpty())
    }

    @Test
    fun theFirstStepOnALineGraphIsTheAdjacentHexTowardTheTarget() {
        // The step is one hex toward the target, never the target itself from far away.
        assertEquals("B", nextThreatHex("A", "D", line))
    }

    @Test
    fun equalLengthPathsAlwaysPickTheSortedFirstBranch() {
        // A reaches D through B or C in two steps either way. The edge insertion order tempts an
        // insertion-order traversal into answering "C"; sorted expansion must answer "B" -- the
        // preview and commit ticks have to propose the identical move.
        val branch = graph("A" to "C", "C" to "D", "A" to "B", "B" to "D")
        assertEquals("B", nextThreatHex("A", "D", branch))
    }

    @Test
    fun anUnreachableTargetReturnsNull() {
        val split = graph("A" to "B", "C" to "D")
        assertNull(nextThreatHex("A", "D", split))
    }

    @Test
    fun aSearchAlreadyAtTheTargetReturnsNull() {
        assertNull(nextThreatHex("D", "D", line))
    }

    @Test
    fun theDepthCapSucceedsAtTheExactDistanceAndFailsOnePastIt() {
        // D is three BFS levels from A: a cap of three still finds it, a cap of two gives up.
        assertEquals("B", nextThreatHex("A", "D", line, maxSearchDepth = 3))
        assertNull(nextThreatHex("A", "D", line, maxSearchDepth = 2))
    }

    @Test
    fun aThreatConsumedThisTurnIsSkippedWhileOneConsumedLastTurnProposes() {
        // The idempotency guard is per-turn: a hold suppresses THIS turn's offer only, so the
        // held threat re-proposes next turn and momentum resumes.
        val resolvedNow = WanderingThreat("resolved", "A", "D", migrationConsumedTurn = 5)
        val heldLastTurn = WanderingThreat("held", "A", "D", migrationConsumedTurn = 4)
        val out = proposeThreatSteps(listOf(resolvedNow, heldLastTurn), currentTurn = 5, neighbors = line)
        assertEquals(listOf(ThreatMigrationProposal("held", fromHex = "A", toHex = "B")), out)
    }

    @Test
    fun anArrivedThreatProposesNothingWhileItsNeighbourStillSteps() {
        // Arrival is the war-threat escalation flow's hand-off, not a migration.
        val arrived = WanderingThreat("arrived", "D", "D")
        val incoming = WanderingThreat("incoming", "C", "D")
        val out = proposeThreatSteps(listOf(arrived, incoming), currentTurn = 1, neighbors = line)
        assertEquals(listOf(ThreatMigrationProposal("incoming", fromHex = "C", toHex = "D")), out)
    }
}
