package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.migrations.migrations.Migration68
import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Migration68 only seeds the rival charter party array; bands themselves are opt-in, so there is
 * nothing to backfill and nothing it may overwrite.
 */
class Migration68Test {
    private val game = unsafeJso<Game>()

    @Test
    fun seedsAnAbsentBandArrayToEmpty() = runTest {
        val kingdom = unsafeJso<dynamic> {}
        Migration68().migrateKingdom(game, kingdom.unsafeCast<KingdomData>())
        assertEquals(0, kingdom.rivalCharterParties.length as Int)
    }

    @Test
    fun aSecondRunNeverErasesExistingBands() = runTest {
        val kingdom = unsafeJso<dynamic> {
            rivalCharterParties = arrayOf(
                unsafeJso<dynamic> {
                    id = "band-pitax"
                    name = "The Pitax Chartists"
                    arrivals = 3
                    currentHexKey = "12034"
                }
            )
        }
        Migration68().migrateKingdom(game, kingdom.unsafeCast<KingdomData>())
        Migration68().migrateKingdom(game, kingdom.unsafeCast<KingdomData>())
        assertEquals(1, kingdom.rivalCharterParties.length as Int)
        assertEquals("band-pitax", kingdom.rivalCharterParties[0].id as String)
        assertEquals(3, kingdom.rivalCharterParties[0].arrivals as Int)
        assertEquals("12034", kingdom.rivalCharterParties[0].currentHexKey as String)
    }
}
