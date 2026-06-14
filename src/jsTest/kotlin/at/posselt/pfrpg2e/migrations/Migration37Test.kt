package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.migrations.migrations.Migration37
import com.foundryvtt.core.Game
import js.objects.recordOf
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

fun runTest(block: suspend () -> Unit): dynamic = GlobalScope.promise { block() }

class Migration37Test {

    @Test
    fun testMigration37RemovesEnhanceWeaponsFromGlobalAndAddsToAmiri() = runTest {
        val amiriActor = unsafeJso<dynamic> {
            name = "Amiri (Companion)"
            uuid = "Actor.amiri123"
        }
        val gameMock = unsafeJso<Game>()
        gameMock.asDynamic().actors = unsafeJso {
            contents = arrayOf(amiriActor)
        }

        val camping = unsafeJso<dynamic> {
            learnedCompanionActivities = arrayOf("enhance-weapons", "bolster-confidence")
            learnedCompanionActivitiesByActor = recordOf<String, Array<String>>()
        }

        val migration = Migration37()
        assertEquals(37, migration.version)

        migration.migrateCamping(gameMock, camping)

        val globalLearned = camping.learnedCompanionActivities.unsafeCast<Array<String>>()
        assertFalse("enhance-weapons" in globalLearned)
        assertTrue("bolster-confidence" in globalLearned)

        val byActor = camping.learnedCompanionActivitiesByActor
        val amiriKey = "Actor_amiri123"
        val amiriLearned = byActor[amiriKey].unsafeCast<Array<String>>()
        assertTrue("enhance-weapons" in amiriLearned)
    }
}
