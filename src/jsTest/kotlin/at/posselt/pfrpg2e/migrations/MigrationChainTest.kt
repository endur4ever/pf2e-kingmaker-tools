package at.posselt.pfrpg2e.migrations

import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The single most valuable migration test (card step 3): a minimal early-version kingdom fixture
 * pushed through the FULL sequential migration chain must end up with every additive field defined,
 * proving a GM upgrading across many versions at once (e.g. 6.0 -> current) doesn't end with
 * undefined-required fields that crash the sheet.
 *
 * Also guards the migration REGISTRATION list itself: migrations 41-48 shipped as classes but were
 * never registered in [migrations], so they never ran. The contiguity assertion below is what makes
 * that class of bug impossible to reintroduce.
 */
class MigrationChainTest {
    private val game = unsafeJso<Game>()

    @Test
    fun registeredVersionsAreContiguous17To53() {
        assertEquals((17..53).toList(), migrations.map { it.version })
    }

    @Test
    fun everyRegisteredVersionIsUnique() {
        val versions = migrations.map { it.version }
        assertEquals(versions.size, versions.toSet().size)
    }

    @Test
    fun fullKingdomChainBackfillsEveryAdditiveField() = runTest {
        // Minimal pre-migration kingdom: only the fields the earliest typed migrations (17-22) read.
        val kingdom = unsafeJso<dynamic> {
            modifiers = arrayOf<dynamic>()
            groups = arrayOf<dynamic>()
            settings = unsafeJso<dynamic> {}
            settlements = arrayOf<dynamic>()
        }

        // Run the whole chain exactly as the runner does, in registration order.
        migrations.forEach { it.migrateKingdom(game, kingdom) }

        // Fields added across the chain must all be defined (== null is true for undefined in JS).
        fun assertDefined(name: String, value: dynamic) =
            assertTrue(value != null, "field '$name' should be defined after the full chain")

        assertDefined("quests", kingdom.quests)                                 // M23
        assertDefined("companions", kingdom.companions)                         // M24
        assertDefined("hexContents", kingdom.hexContents)                       // M27
        assertDefined("campaignClocks", kingdom.campaignClocks)                 // M29
        assertDefined("questTemplates", kingdom.questTemplates)                 // M30/M32
        assertDefined("companionPersonalQuests", kingdom.companionPersonalQuests) // M33
        assertDefined("structureBlacklist", kingdom.structureBlacklist)        // M34
        assertDefined("partyInfluence", kingdom.partyInfluence)                // M35
        assertDefined("shipments", kingdom.shipments)                          // M38
        assertDefined("accessGrants", kingdom.accessGrants)                    // M42 (was unregistered)
        assertDefined("bankedBonuses", kingdom.bankedBonuses)                  // M43 (was unregistered)

        // Scalar defaults from the late (formerly-unregistered) feat-tracking migration.
        assertEquals(11, kingdom.pullTogetherCurrentDC.unsafeCast<Int>())      // M45
        assertEquals(false, kingdom.envyOfTheWorldFirstIgnoreUsed.unsafeCast<Boolean>())
    }

    @Test
    fun fullKingdomChainIsIdempotentOnReRun() = runTest {
        val kingdom = unsafeJso<dynamic> {
            modifiers = arrayOf<dynamic>()
            groups = arrayOf<dynamic>()
            settings = unsafeJso<dynamic> {}
            settlements = arrayOf<dynamic>()
        }
        migrations.forEach { it.migrateKingdom(game, kingdom) }
        // Re-running the whole chain must not clobber the now-populated data.
        migrations.forEach { it.migrateKingdom(game, kingdom) }
        assertEquals(11, kingdom.pullTogetherCurrentDC.unsafeCast<Int>())
        assertTrue(kingdom.accessGrants != null)
    }
}
