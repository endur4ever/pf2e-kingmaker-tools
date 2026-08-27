package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.councilVoteMutex
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import at.posselt.pfrpg2e.kingdom.dialogs.performEndTurn
import com.foundryvtt.core.Game
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

/**
 * Ending a kingdom turn is GM-only, enforced inside [performEndTurn] itself.
 *
 * The guard used to live only on the sheet's "end-turn" click handler, which left the Turn
 * Wizard's Commit Turn button — ungated in both its template and its listener — as a full
 * bypass: any player could open the wizard and advance the kingdom. Guarding the chokepoint
 * covers every caller, including ones added later.
 *
 * A non-GM call returns before touching the actor or the kingdom, so these tests need no
 * Foundry document fixtures: reaching any real work would throw on the empty mocks, which is
 * itself part of the assertion.
 */
class EndTurnGatingTest {
    private fun runTest(block: suspend () -> Unit): dynamic =
        @Suppress("DELICATE_API_TRANSITIONAL_MINI_MARKER") GlobalScope.promise { block() }

    private fun installUiMock(): dynamic =
        js(
            """(function () {
                var warnings = [];
                globalThis.ui = { notifications: { warn: function (m) { warnings.push(m); } } };
                return warnings;
            })()"""
        )

    @Test
    fun playerCannotEndTurn() = runTest {
        val warnings = installUiMock()
        val game: Game = js("""{ user: { isGM: false } }""")
        val actor: KingdomActor = js("""{}""")
        // the kingdom now lives behind the actor: performEndTurn reads it itself, inside the
        // council mutex, so a stale caller copy can no longer be handed in
        val kingdom: KingdomData = js("""{ currentTurn: 4 }""")
        actor.asDynamic().getFlag = { _: String, _: String -> kingdom }

        val result = performEndTurn(game, actor)

        assertNull(result, "a non-GM call must not tick the turn")
        assertEquals(1, warnings.length as Int, "the player must be told why nothing happened")
        // The kingdom object must be untouched — no snapshot, no turn increment.
        assertEquals(4, kingdom.currentTurn, "a refused end-turn must not advance the turn")
    }

    @Test
    fun playerCannotEndTurnEvenWithAnOwnedActor() = runTest {
        // Players are OWNERs of the party actor, which is exactly why ownership is not the
        // permission being checked here.
        val warnings = installUiMock()
        val game: Game = js("""{ user: { isGM: false } }""")
        val actor: KingdomActor = js("""{ isOwner: true }""")
        val kingdom: KingdomData = js("""{ currentTurn: 9 }""")

        assertNull(performEndTurn(game, actor))
        assertEquals(1, warnings.length as Int)
        assertEquals(9, kingdom.currentTurn)
    }

    @Test
    fun theTickQueuesBehindTheCouncilMutexRatherThanReadingStaleState() = runTest {
        // The transaction-boundary contract: performEndTurn's kingdom READ happens under
        // councilVoteMutex. If the lock is held -- a ballot mid-write -- the tick must not have
        // touched the actor at all yet, or it would tick a snapshot that predates the ballot.
        installUiMock()
        val game: Game = js("""{ user: { isGM: true } }""")
        var reads = 0
        val actor: KingdomActor = js("""{}""")
        actor.asDynamic().getFlag = { _: String, _: String -> reads++; null }

        councilVoteMutex.lock()
        try {
            val job = GlobalScope.launch { performEndTurn(game, actor) }
            repeat(5) { yield() }
            assertEquals(0, reads, "the tick must not read the kingdom while a council write holds the lock")
            assertFalse(job.isCompleted, "it queues; it does not skip")
            job.cancelAndJoin()
        } finally {
            councilVoteMutex.unlock()
        }
        // and once the lock is free, the read happens (null kingdom -> clean no-op return)
        assertNull(performEndTurn(game, actor))
        assertTrue(reads > 0, "the read runs inside the lock once it is acquired")
    }
}
