package at.posselt.pfrpg2e.kingdom

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
        val kingdom: KingdomData = js("""{ currentTurn: 4 }""")

        val result = performEndTurn(game, actor, kingdom)

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

        assertNull(performEndTurn(game, actor, kingdom))
        assertEquals(1, warnings.length as Int)
        assertEquals(9, kingdom.currentTurn)
    }
}
