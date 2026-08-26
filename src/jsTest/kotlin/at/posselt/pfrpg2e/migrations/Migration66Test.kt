package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.migrations.migrations.Migration66
import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals

/** Migration66 seeds the council-vote ledger and must never clobber ballots already cast. */
class Migration66Test {
    private val game = unsafeJso<Game>()

    @Test
    fun seedsAnAbsentLedgerToEmpty() = runTest {
        val kingdom = unsafeJso<dynamic> {}
        Migration66().migrateKingdom(game, kingdom.unsafeCast<KingdomData>())
        assertEquals(0, kingdom.councilVotes.length as Int)
    }

    @Test
    fun aSecondRunNeverErasesRecordedBallots() = runTest {
        val ballots = unsafeJso<dynamic> {}
        ballots["player-2"] = 1
        val kingdom = unsafeJso<dynamic> {
            councilVotes = arrayOf(
                unsafeJso<dynamic> {
                    id = "vote-1815-1"
                    question = "Appease or fight the druids?"
                    options = arrayOf("Appease", "Fight")
                    votes = ballots
                    openedTurn = 12
                }
            )
        }

        Migration66().migrateKingdom(game, kingdom.unsafeCast<KingdomData>())
        Migration66().migrateKingdom(game, kingdom.unsafeCast<KingdomData>())

        assertEquals(1, kingdom.councilVotes.length as Int)
        assertEquals("vote-1815-1", kingdom.councilVotes[0].id as String)
        assertEquals(1, kingdom.councilVotes[0].votes["player-2"] as Int)
    }
}
