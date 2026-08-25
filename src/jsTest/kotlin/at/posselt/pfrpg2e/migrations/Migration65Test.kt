package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.migrations.migrations.Migration65
import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals

/** Migration65 seeds the treasure ledger; hex manifests stay absent (null = no treasure). */
class Migration65Test {
    private val game = unsafeJso<Game>()

    @Test
    fun seedsAnAbsentLedgerToEmpty() = runTest {
        val kingdom = unsafeJso<dynamic> {}
        Migration65().migrateKingdom(game, kingdom.unsafeCast<KingdomData>())
        assertEquals(0, kingdom.treasureLedger.length as Int)
    }

    @Test
    fun aSecondRunNeverErasesAwardedHistory() = runTest {
        val kingdom = unsafeJso<dynamic> {
            treasureLedger = arrayOf(unsafeJso<dynamic> { id = "loot-1815-1" })
        }
        Migration65().migrateKingdom(game, kingdom.unsafeCast<KingdomData>())
        assertEquals(1, kingdom.treasureLedger.length as Int)
        assertEquals("loot-1815-1", kingdom.treasureLedger[0].id as String)
    }
}
