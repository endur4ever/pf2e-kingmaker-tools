package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.migrations.migrations.Migration52
import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Migration52 links existing war threats to factions by matching their free-text enemy name against
 * the kingdom's group names, so a campaign that has been naming its enemies consistently gets
 * war-end conditions without the GM re-entering anything.
 */
class Migration52Test {
    private val game = unsafeJso<Game>()

    private fun kingdomWith(vararg enemyFaction: String?): dynamic = unsafeJso<dynamic> {
        groups = arrayOf(
            unsafeJso<dynamic> { name = "Pitax" },
            unsafeJso<dynamic> { name = "Mivon" },
        )
        warThreats = enemyFaction.map { faction ->
            unsafeJso<dynamic> {
                id = "t"
                this.enemyFaction = faction
            }
        }.toTypedArray()
    }

    @Test
    fun linksThreatsWhoseFreeTextAlreadyNamesAGroup() = runTest {
        val kingdom = kingdomWith("Pitax", "Mivon")

        Migration52().migrateKingdom(game, kingdom)

        assertEquals("Pitax", kingdom.warThreats[0].enemyFactionName)
        assertEquals("Mivon", kingdom.warThreats[1].enemyFactionName)
    }

    @Test
    fun leavesUnmatchedAndMissingEnemiesUnlinked() = runTest {
        // A wandering menace, a faction that was never added as a group, and a blank.
        val kingdom = kingdomWith("Goblin Horde", null, "")

        Migration52().migrateKingdom(game, kingdom)

        assertNull(kingdom.warThreats[0].enemyFactionName)
        assertNull(kingdom.warThreats[1].enemyFactionName)
        assertNull(kingdom.warThreats[2].enemyFactionName)
    }

    @Test
    fun matchesAfterTrimmingButNeverFuzzily() = runTest {
        // " Pitax " is the same faction; "pitax" is not -- a fuzzy match would silently bind a war
        // to the wrong faction, and relinking is one click in the dialog.
        val kingdom = kingdomWith("  Pitax  ", "pitax")

        Migration52().migrateKingdom(game, kingdom)

        assertEquals("Pitax", kingdom.warThreats[0].enemyFactionName)
        assertNull(kingdom.warThreats[1].enemyFactionName)
    }

    @Test
    fun neverOverwritesAnExistingLink() = runTest {
        val kingdom = kingdomWith("Pitax")
        kingdom.warThreats[0].enemyFactionName = "Mivon"

        Migration52().migrateKingdom(game, kingdom)

        assertEquals("Mivon", kingdom.warThreats[0].enemyFactionName)
    }

    @Test
    fun survivesAKingdomWithNoThreatsOrNoGroups() = runTest {
        val noThreats = unsafeJso<dynamic> { groups = arrayOf<dynamic>() }
        Migration52().migrateKingdom(game, noThreats)

        val noGroups = unsafeJso<dynamic> {
            warThreats = arrayOf(unsafeJso<dynamic> { enemyFaction = "Pitax" })
        }
        Migration52().migrateKingdom(game, noGroups)
        assertNull(noGroups.warThreats[0].enemyFactionName)
    }
}
