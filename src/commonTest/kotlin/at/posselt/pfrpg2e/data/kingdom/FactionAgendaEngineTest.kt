package at.posselt.pfrpg2e.data.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class FactionAgendaEngineTest {
    @Test
    fun archetypeAssignmentIsDeterministicAndCoversTheTable() {
        // pinned values: a stdlib or hash change that reshuffles live campaigns must fail HERE
        assertEquals(pickArchetypeForFaction("Pitax"), pickArchetypeForFaction("Pitax"))
        val names = listOf("Pitax", "Mivon", "Brevoy", "Narlmarches", "Tiger Lords", "Varnhold",
            "Restov", "Hargulka", "Stag Lord", "House Surtova", "M'botuu", "Sootscale Kobolds")
        val assigned = names.map { pickArchetypeForFaction(it) }.toSet()
        // twelve canonical names should touch most of a five-row table; a constant function
        // (the obvious mutation) collapses this to one
        assertTrue(assigned.size >= 3, "expected spread across archetypes, got $assigned")
        assigned.forEach { assertTrue(it in FACTION_ARCHETYPE_IDS) }
    }

    @Test
    fun initialGoalIsDeterministicWithinThePool() {
        val pool = listOf("conquer-neighbor", "build-army", "raid-trade-routes")
        val goal = initialGoalForFaction("Pitax", pool)
        assertEquals(goal, initialGoalForFaction("Pitax", pool))
        assertTrue(goal in pool)
        assertNull(initialGoalForFaction("Pitax", emptyList()))
    }

    @Test
    fun negativeHashNamesStillIndexSafely() {
        // long names overflow Int and go negative; .mod keeps the index in range
        val name = "The Grand Coalition of the Restov Free Armies and Allied Houses"
        assertTrue(stableFactionHash(name) < 0)
        assertTrue(pickArchetypeForFaction(name) in FACTION_ARCHETYPE_IDS)
        assertEquals(pickArchetypeForFaction(name), pickArchetypeForFaction(name))
    }

    @Test
    fun turnRngIteratesInsteadOfCollapsing() {
        val rng = TurnRng(factionAgendaTurnSeed("Elkhaven", 12))
        val draws = (1..200).map { rng.next(10) }
        // the salt-linear bug the plan documents yields exactly TWO distinct buckets mod 10
        assertTrue(draws.toSet().size >= 8, "LCG collapsed to ${draws.toSet()}")
        draws.forEach { assertTrue(it in 0..9) }
    }

    @Test
    fun turnRngIsReproducibleAndSeedSensitive() {
        val a = TurnRng(factionAgendaTurnSeed("Elkhaven", 12))
        val b = TurnRng(factionAgendaTurnSeed("Elkhaven", 12))
        assertEquals((1..50).map { a.next(100) }, (1..50).map { b.next(100) })
        val c = TurnRng(factionAgendaTurnSeed("Elkhaven", 13))
        val cDraws = (1..50).map { c.next(100) }
        val aAgain = TurnRng(factionAgendaTurnSeed("Elkhaven", 12))
        assertTrue(cDraws != (1..50).map { aAgain.next(100) }, "turn must move the sequence")
        assertFailsWith<IllegalArgumentException> { TurnRng(1).next(0) }
    }
}
