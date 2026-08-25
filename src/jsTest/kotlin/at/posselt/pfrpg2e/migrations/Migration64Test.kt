package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.migrations.migrations.Migration64
import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Migration64 backfills map dynamism non-destructively: threats stay static and start "at
 * target", the re-wild side-table starts empty, and the dials default with migration OFF.
 */
class Migration64Test {
    private val game = unsafeJso<Game>()

    private fun kingdom(block: dynamic.() -> Unit = {}): dynamic {
        val k = unsafeJso<dynamic> { settings = unsafeJso<dynamic> {} }
        k.block()
        return k
    }

    @Test
    fun backfillsThreatsToStaticAtTheirTarget() = runTest {
        val k = kingdom {
            warThreats = arrayOf(unsafeJso<dynamic> { id = "t1"; targetHexLocation = "1815" })
        }
        Migration64().migrateKingdom(game, k.unsafeCast<KingdomData>())
        assertEquals(false, k.warThreats[0].wanders as Boolean)
        assertEquals("1815", k.warThreats[0].currentHexLocation as String)
        assertNull(k.warThreats[0].migrationConsumedTurn)
    }

    @Test
    fun aSecondRunPreservesAWanderingThreatsPosition() = runTest {
        val k = kingdom {
            warThreats = arrayOf(unsafeJso<dynamic> {
                id = "t1"; targetHexLocation = "1815"
                wanders = true; currentHexLocation = "1612"; migrationConsumedTurn = 9
            })
        }
        Migration64().migrateKingdom(game, k.unsafeCast<KingdomData>())
        assertEquals(true, k.warThreats[0].wanders as Boolean)
        assertEquals("1612", k.warThreats[0].currentHexLocation as String, "an advanced position survives re-migration")
        assertEquals(9, k.warThreats[0].migrationConsumedTurn as Int)
    }

    @Test
    fun seedsTrackersAndDialsWithoutClobberingExistingValues() = runTest {
        val fresh = kingdom()
        Migration64().migrateKingdom(game, fresh.unsafeCast<KingdomData>())
        assertEquals(0, fresh.rewildTrackers.length as Int)
        assertEquals(false, fresh.settings.threatMigrationEnabled as Boolean)
        assertEquals(1, fresh.settings.threatMigrationSpeed as Int)
        assertEquals(6, fresh.settings.rewildDelayTurns as Int)

        val tuned = kingdom {
            rewildTrackers = arrayOf(unsafeJso<dynamic> { hexKey = "1815"; clearedSinceTurn = 3 })
            settings = unsafeJso<dynamic> {
                threatMigrationEnabled = true; threatMigrationSpeed = 2; rewildDelayTurns = 0
            }
        }
        Migration64().migrateKingdom(game, tuned.unsafeCast<KingdomData>())
        assertEquals(1, tuned.rewildTrackers.length as Int)
        assertEquals(true, tuned.settings.threatMigrationEnabled as Boolean)
        assertEquals(2, tuned.settings.threatMigrationSpeed as Int)
        assertEquals(0, tuned.settings.rewildDelayTurns as Int, "0 = never is a GM choice, not a gap to refill")
    }
}
