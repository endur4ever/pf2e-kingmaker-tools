package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.migrations.migrations.Migration69
import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Migration69 seeds the three renown arrays; legacy turn records keep a null `contributions`.
 *
 * The second-run test is the one that matters: the seam appends deeds to these very arrays the
 * moment a PC rolls, so a migration that assigned instead of guarding would erase a turn's worth
 * of credited deeds and every epithet threshold they had crossed.
 */
class Migration69Test {
    private val game = unsafeJso<Game>()

    @Test
    fun seedsTheAbsentRenownArraysToEmpty() = runTest {
        val kingdom = unsafeJso<dynamic> {}
        Migration69().migrateKingdom(game, kingdom.unsafeCast<KingdomData>())
        assertEquals(0, kingdom.renown.length as Int)
        assertEquals(0, kingdom.currentTurnContributions.length as Int)
        assertEquals(0, kingdom.currentTurnDeeds.length as Int)
    }

    @Test
    fun aSecondRunNeverErasesRecordedRenown() = runTest {
        val kingdom = unsafeJso<dynamic> {
            renown = arrayOf(
                unsafeJso<dynamic> {
                    actorUuid = "Actor.pc1"
                    populace = 17
                    epithets = arrayOf("theUntiring")
                },
            )
            currentTurnContributions = arrayOf(
                unsafeJso<dynamic> {
                    actorUuid = "Actor.pc1"
                    crits = 2
                },
            )
            currentTurnDeeds = arrayOf(
                unsafeJso<dynamic> {
                    deedId = "deed-1"
                    actorUuid = "Actor.pc1"
                    populaceApplied = 3
                },
            )
        }

        Migration69().migrateKingdom(game, kingdom.unsafeCast<KingdomData>())
        Migration69().migrateKingdom(game, kingdom.unsafeCast<KingdomData>())

        assertEquals(1, kingdom.renown.length as Int)
        assertEquals("Actor.pc1", kingdom.renown[0].actorUuid as String)
        assertEquals(17, kingdom.renown[0].populace as Int)
        assertEquals("theUntiring", kingdom.renown[0].epithets[0] as String)
        assertEquals(1, kingdom.currentTurnContributions.length as Int)
        assertEquals(2, kingdom.currentTurnContributions[0].crits as Int)
        assertEquals(1, kingdom.currentTurnDeeds.length as Int)
        assertEquals("deed-1", kingdom.currentTurnDeeds[0].deedId as String)
        assertEquals(3, kingdom.currentTurnDeeds[0].populaceApplied as Int)
    }

    @Test
    fun leavesLegacyTurnRecordsWithoutContributions() = runTest {
        val kingdom = unsafeJso<dynamic> {
            turnHistory = arrayOf(unsafeJso<dynamic> { turn = 3 })
        }
        Migration69().migrateKingdom(game, kingdom.unsafeCast<KingdomData>())
        assertEquals(1, kingdom.turnHistory.length as Int)
        assertTrue(
            kingdom.turnHistory[0].contributions == null,
            "legacy turn records must keep contributions absent so the Spotlight shows nothing",
        )
    }
}
