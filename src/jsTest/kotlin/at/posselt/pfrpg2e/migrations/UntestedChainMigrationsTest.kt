package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.camping.CampingData
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.migrations.migrations.Migration17
import at.posselt.pfrpg2e.migrations.migrations.Migration21
import at.posselt.pfrpg2e.migrations.migrations.Migration22
import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Coverage for chain migrations that card t_8bf82be6 triaged as "(a) MUST" and left untested:
 * 17 (field adds + a cost String -> RawCost parse), 21 (settlement layout transform) and 22 (a new
 * nested object). These run on every kingdom and camping actor of anyone upgrading across versions,
 * so a silent mangle here is invisible until a GM notices their data is wrong.
 */
class UntestedChainMigrationsTest {
    private val game = unsafeJso<Game>()

    private fun camping(build: (dynamic) -> Unit = {}): CampingData {
        val c = unsafeJso<dynamic> {}
        build(c)
        return c.unsafeCast<CampingData>()
    }

    private fun kingdom(build: (dynamic) -> Unit = {}): KingdomData {
        val k = unsafeJso<dynamic> {}
        build(k)
        return k.unsafeCast<KingdomData>()
    }

    private fun meal(cost: String): dynamic = unsafeJso<dynamic> {
        this.name = "Stew"
        this.cost = cost
    }

    // ── Migration17: homebrew meal cost String -> RawCost ────────────────────────────────────────

    @Test
    fun migration17ParsesEachCurrency() = runTest {
        val c = camping {
            it.cooking = unsafeJso<dynamic> { homebrewMeals = arrayOf(meal("5 gp"), meal("12 sp"), meal("3 cp"), meal("2 pp")) }
        }
        Migration17().migrateCamping(game, c)
        val meals = c.cooking.homebrewMeals
        assertEquals(5, meals[0].cost.value)
        assertEquals("gp", meals[0].cost.currency)
        assertEquals(12, meals[1].cost.value)
        assertEquals("sp", meals[1].cost.currency)
        assertEquals(3, meals[2].cost.value)
        assertEquals("cp", meals[2].cost.currency)
        assertEquals(2, meals[3].cost.value)
        assertEquals("pp", meals[3].cost.currency)
    }

    @Test
    fun migration17TrimsSurroundingWhitespace() = runTest {
        val c = camping { it.cooking = unsafeJso<dynamic> { homebrewMeals = arrayOf(meal("  7 sp  ")) } }
        Migration17().migrateCamping(game, c)
        assertEquals(7, c.cooking.homebrewMeals[0].cost.value)
        assertEquals("sp", c.cooking.homebrewMeals[0].cost.currency)
    }

    @Test
    fun migration17SilentlyZeroesACostItCannotParse() = runTest {
        // Documents real, already-shipped behaviour rather than asserting what we might prefer:
        // the regex demands "<digits> <currency>", so a cost written without a space -- "5gp" --
        // does not match and the value is LOST, falling back to 0 gp. Anyone who typed a homebrew
        // cost that way had it silently zeroed on upgrade. This migration has long since run in the
        // wild, so it must not be changed now; the test exists so the behaviour is at least known.
        val c = camping { it.cooking = unsafeJso<dynamic> { homebrewMeals = arrayOf(meal("5gp"), meal("free"), meal("")) } }
        Migration17().migrateCamping(game, c)
        val meals = c.cooking.homebrewMeals
        assertEquals(0, meals[0].cost.value, "\"5gp\" without a space parses to 0 -- known data loss")
        assertEquals("gp", meals[0].cost.currency)
        assertEquals(0, meals[1].cost.value)
        assertEquals(0, meals[2].cost.value)
    }

    @Test
    fun migration17PreservesTheRestOfTheMeal() = runTest {
        val c = camping { it.cooking = unsafeJso<dynamic> { homebrewMeals = arrayOf(meal("4 gp")) } }
        Migration17().migrateCamping(game, c)
        assertEquals("Stew", c.cooking.homebrewMeals[0].name)
    }

    @Test
    fun migration17SeedsModifierAndGroupFlagsWithoutClobbering() = runTest {
        val k = kingdom {
            it.modifiers = arrayOf(unsafeJso<dynamic> { name = "m1"; value = 3 })
            it.groups = arrayOf(unsafeJso<dynamic> { name = "Pitax"; relations = "neutral" })
        }
        Migration17().migrateKingdom(game, k)
        assertEquals(false, k.modifiers[0].requiresTranslation)
        assertEquals("m1", k.modifiers[0].name, "existing modifier fields must survive the copy")
        assertEquals(3, k.modifiers[0].value)
        assertEquals(false, k.groups[0].preventPledgeOfFealty)
        assertEquals("Pitax", k.groups[0].name, "existing group fields must survive the copy")
        assertEquals("neutral", k.groups[0].relations)
    }

    // ── Migration21: every settlement forced to freeForm ─────────────────────────────────────────

    @Test
    fun migration21SetsLayoutOnEverySettlement() = runTest {
        val k = kingdom {
            it.settings = unsafeJso<dynamic> {}
            it.settlements = arrayOf(
                unsafeJso<dynamic> { sceneId = "a" },
                unsafeJso<dynamic> { sceneId = "b" },
            )
        }
        Migration21().migrateKingdom(game, k)
        assertEquals(false, k.settings.capitalCanGrowOneSizeLarger)
        assertEquals("freeForm", k.settlements[0].layoutType)
        assertEquals("freeForm", k.settlements[1].layoutType, "the loop must cover every settlement, not just the first")
    }

    @Test
    fun migration21IsUnconditionalWhichIsWhyItMustNotBeReRun() = runTest {
        // Pins the property that makes re-running the chain unsafe (see ImportPlan): a settlement
        // already laid out rigidly is overwritten, not preserved.
        val k = kingdom {
            it.settings = unsafeJso<dynamic> { capitalCanGrowOneSizeLarger = true }
            it.settlements = arrayOf(unsafeJso<dynamic> { sceneId = "a"; layoutType = "rigid" })
        }
        Migration21().migrateKingdom(game, k)
        assertEquals("freeForm", k.settlements[0].layoutType)
        assertEquals(false, k.settings.capitalCanGrowOneSizeLarger)
    }

    @Test
    fun migration21HandlesAKingdomWithNoSettlements() = runTest {
        val k = kingdom {
            it.settings = unsafeJso<dynamic> {}
            it.settlements = arrayOf<dynamic>()
        }
        Migration21().migrateKingdom(game, k)
        assertEquals(false, k.settings.capitalCanGrowOneSizeLarger)
    }

    // ── Migration22: council cooldowns nested object ─────────────────────────────────────────────

    @Test
    fun migration22SeedsEveryCooldownAtZero() = runTest {
        val k = kingdom { it.settings = unsafeJso<dynamic> {} }
        Migration22().migrateKingdom(game, k)
        assertEquals(false, k.settings.enableCouncilMissions)
        val cooldowns = k.councilCooldowns
        assertNotNull(cooldowns, "the migration must create the nested object, not leave it null")
        assertEquals(0, cooldowns.audit)
        assertEquals(0, cooldowns.scrying)
        assertEquals(0, cooldowns.lockdown)
        assertEquals(0, cooldowns.feast, "a missed field here would leave a cooldown undefined")
    }
}
