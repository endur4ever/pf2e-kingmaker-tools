package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.migrations.migrations.Migration40
import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals

// Reuses the package-level `runTest` helper defined in Migration37Test.kt.
class Migration40Test {

    @Test
    fun testMigration40SeedsDefaultsOnExistingCompanions() = runTest {
        val gameMock = unsafeJso<Game>()
        val bareCompanion = unsafeJso<dynamic> { name = "Amiri" }
        val kingdom = unsafeJso<dynamic> { companions = arrayOf(bareCompanion) }

        val migration = Migration40()
        assertEquals(40, migration.version)

        migration.migrateKingdom(gameMock, kingdom)

        // companionExpeditions seeded to an empty array.
        assertEquals(0, kingdom.companionExpeditions.unsafeCast<Array<dynamic>>().size)
        // per-companion defaults backfilled.
        val c = kingdom.companions[0]
        assertEquals(1, c.level.unsafeCast<Int>())
        assertEquals(0, c.xp.unsafeCast<Int>())
        assertEquals("available", c.expeditionStatus.unsafeCast<String>())
    }

    @Test
    fun testMigration40PreservesExistingCompanionValues() = runTest {
        // Mirror of "Migration24 preserves existing companions": already-populated
        // fields must NOT be clobbered (the backfill is null-guarded / idempotent).
        val gameMock = unsafeJso<Game>()
        val leveled = unsafeJso<dynamic> {
            name = "Ekundayo"
            level = 5
            xp = 400
            expeditionStatus = "onExpedition"
            injuryDaysRemaining = 3
        }
        val kingdom = unsafeJso<dynamic> {
            companions = arrayOf(leveled)
            companionExpeditions = arrayOf(unsafeJso<dynamic> { id = "exp-1" })
        }

        Migration40().migrateKingdom(gameMock, kingdom)

        // existing companionExpeditions left intact (only seeded when null).
        assertEquals(1, kingdom.companionExpeditions.unsafeCast<Array<dynamic>>().size)
        val c = kingdom.companions[0]
        assertEquals(5, c.level.unsafeCast<Int>())
        assertEquals(400, c.xp.unsafeCast<Int>())
        assertEquals("onExpedition", c.expeditionStatus.unsafeCast<String>())
        assertEquals(3, c.injuryDaysRemaining.unsafeCast<Int>())
    }

    @Test
    fun testMigration40HandlesKingdomWithoutCompanions() = runTest {
        val gameMock = unsafeJso<Game>()
        val kingdom = unsafeJso<dynamic> { }

        // Must not throw when the companions array is absent.
        Migration40().migrateKingdom(gameMock, kingdom)

        assertEquals(0, kingdom.companionExpeditions.unsafeCast<Array<dynamic>>().size)
    }
}
