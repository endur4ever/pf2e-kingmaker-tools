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

    // ── Phase 2: the advance engine ───────────────────────────────────────────────────────────

    private val catalog = mapOf(
        "expand" to AgendaMoveSpec("expand", cooldownTurns = 2, validTargets = "self", effect = "clock", effectMagnitude = 1, requires = null),
        "sabotage-rival" to AgendaMoveSpec("sabotage-rival", 3, "rival", "standing-delta", -10, "not-at-war"),
        "court-ally" to AgendaMoveSpec("court-ally", 2, "ally", "standing-delta", 8, null),
        "raise-army" to AgendaMoveSpec("raise-army", 4, "self", "war-threat", 0, "has-hex"),
        "court-pcs" to AgendaMoveSpec("court-pcs", 0, "pcs", "standing-delta", 5, null),
    )
    private val aggressive = AgendaArchetypeSpec(
        "aggressive",
        weights = mapOf("expand" to 30, "sabotage-rival" to 25, "raise-army" to 20, "court-ally" to 15, "court-pcs" to 10),
        goals = listOf("conquer-neighbor", "build-army", "raid-trade-routes"),
    )
    private val archetypes = mapOf("aggressive" to aggressive)

    private fun agenda(
        goalId: String = "conquer-neighbor",
        progress: Int = 0,
        cooldowns: Map<String, Int> = emptyMap(),
        lastAdvancedTurn: Int? = null,
    ) = FactionAgendaState(goalId, "", progress, 6, "aggressive", cooldowns, lastAdvancedTurn, null)

    private fun faction(
        name: String,
        standing: Int? = 0,
        atWar: Boolean = false,
        hasHex: Boolean = true,
        agenda: FactionAgendaState? = agenda(),
    ) = FactionSnapshot(name, standing, atWar, hasHex, agenda)

    @Test
    fun pickRespectsWeightsAcrossManyDraws() {
        val actor = faction("Pitax")
        val all = listOf(actor, faction("Mivon", standing = -30), faction("Restov", standing = 40))
        val rng = TurnRng(42)
        val counts = mutableMapOf<String, Int>()
        repeat(10000) {
            val picked = pickAgendaMove(actor, agenda(), all, catalog, aggressive, rng)!!
            counts[picked.id] = (counts[picked.id] ?: 0) + 1
        }
        // plan 9.1: distribution within +-2% of the weight ratios
        assertTrue(counts.getValue("expand") in 2800..3200, "expand=${counts["expand"]}")
        assertTrue(counts.getValue("sabotage-rival") in 2300..2700, "sabotage=${counts["sabotage-rival"]}")
        assertTrue(counts.getValue("court-pcs") in 800..1200, "courtPcs=${counts["court-pcs"]}")
    }

    @Test
    fun cooldownBlocksAndDecrements() {
        val actor = faction("Pitax", agenda = agenda(cooldowns = mapOf("expand" to 1)))
        val all = listOf(actor)
        // a solo faction has no rival/ally; with expand cooling down only raise-army/court-pcs remain
        val rng = TurnRng(1)
        repeat(50) {
            val picked = pickAgendaMove(actor, actor.agenda!!, all, catalog, aggressive, rng)!!
            assertTrue(picked.id != "expand")
        }
        // after one advance the cooldown map drops to zero entries and expand returns
        val result = advanceAllAgendas(all, currentTurn = 5, catalog, archetypes, TurnRng(1))
        val after = result.factions.single().agenda!!
        assertTrue("expand" !in after.moveCooldowns || after.moveCooldowns["expand"] == null)
    }

    @Test
    fun rivalIsLowestStandingExcludingAtWar() {
        val actor = faction("Pitax")
        val all = listOf(
            actor,
            faction("Mivon", standing = -40, atWar = true),
            faction("Restov", standing = -20),
            faction("Varnhold", standing = 30),
        )
        assertEquals("Restov", pickRival(actor, all)?.name)
        assertEquals("Varnhold", pickAlly(actor, all)?.name)
    }

    @Test
    fun advanceIsIdempotentPerTurn() {
        val all = listOf(faction("Pitax", agenda = agenda(lastAdvancedTurn = 7)))
        val result = advanceAllAgendas(all, currentTurn = 7, catalog, archetypes, TurnRng(9))
        assertTrue(result.moves.isEmpty())
        assertEquals(7, result.factions.single().agenda!!.lastAdvancedTurn)
    }

    @Test
    fun clockCompletionDrawsNewGoalFromPoolAndResets() {
        // only expand eligible: solo faction (no rival/ally), no hex (no army), zero court-pcs weight
        val solo = AgendaArchetypeSpec("aggressive", mapOf("expand" to 1), aggressive.goals)
        val actor = faction("Pitax", hasHex = false, agenda = agenda(progress = 5))
        val result = advanceAllAgendas(listOf(actor), 3, catalog, mapOf("aggressive" to solo), TurnRng(11))
        val move = result.moves.single()
        assertTrue(move.goalCompleted)
        val after = result.factions.single().agenda!!
        assertEquals(0, after.progress)
        assertTrue(after.goalId in aggressive.goals)
        assertTrue(after.goalId != "conquer-neighbor", "completed goal must not be redrawn")
    }

    @Test
    fun warThreatFlagOnlyOnCrossingAndGuarded() {
        val sabotageOnly = AgendaArchetypeSpec("aggressive", mapOf("sabotage-rival" to 1), aggressive.goals)
        val arch = mapOf("aggressive" to sabotageOnly)
        // -45 -> -55 crosses into HOSTILE (attitudeFor: <= -50); Mivon idles so only Pitax moves
        val crossing = listOf(faction("Pitax"), faction("Mivon", standing = -45, agenda = null))
        val m1 = advanceAllAgendas(crossing, 1, catalog, arch, TurnRng(5)).moves.single()
        val e1 = m1.effect as AgendaMoveEffect.StandingDelta
        assertEquals("Mivon", e1.targetFaction)
        assertTrue(e1.offerWarThreat)
        // already hostile: a re-touch never re-offers
        val hostile = listOf(faction("Pitax"), faction("Mivon", standing = -60, agenda = null))
        val e2 = advanceAllAgendas(hostile, 1, catalog, arch, TurnRng(5)).moves.single().effect as AgendaMoveEffect.StandingDelta
        assertTrue(!e2.offerWarThreat)
        // pending war threat suppresses the flag even on a genuine crossing
        val e3 = advanceAllAgendas(crossing, 1, catalog, arch, TurnRng(5), pendingWarThreatFactions = setOf("Mivon"))
            .moves.single().effect as AgendaMoveEffect.StandingDelta
        assertTrue(!e3.offerWarThreat)
    }

    @Test
    fun courtPcsEmitsCourtIntentWithCrossing()  {
        val courtOnly = AgendaArchetypeSpec("aggressive", mapOf("court-pcs" to 1), aggressive.goals)
        val arch = mapOf("aggressive" to courtOnly)
        // 12 -> 17 crosses into FRIENDLY (attitudeFor: >= 15)
        val actor = faction("Pitax", standing = 12)
        val effect = advanceAllAgendas(listOf(actor), 1, catalog, arch, TurnRng(2)).moves.single().effect
        val court = effect as AgendaMoveEffect.CourtPcs
        assertEquals(5, court.delta)
        assertTrue(court.offerDiplomacyQuest)
        // FRIENDLY -> further up but not a new crossing into FRIENDLY: helper decides; pin the re-touch
        val warm = faction("Pitax", standing = 30)
        val again = advanceAllAgendas(listOf(warm), 1, catalog, arch, TurnRng(2)).moves.single().effect as AgendaMoveEffect.CourtPcs
        assertTrue(!again.offerDiplomacyQuest)
    }

    @Test
    fun requiresGatesSabotageAndArmy() {
        val actorAtWar = faction("Pitax", atWar = true, hasHex = false)
        val all = listOf(actorAtWar, faction("Mivon", standing = -30))
        val rng = TurnRng(3)
        repeat(50) {
            val picked = pickAgendaMove(actorAtWar, actorAtWar.agenda!!, all, catalog, aggressive, rng)!!
            assertTrue(picked.id != "sabotage-rival", "not-at-war must gate sabotage")
            assertTrue(picked.id != "raise-army", "has-hex must gate raise-army")
        }
    }

    @Test
    fun sameSeedSameSequenceDifferentSeedDiverges() {
        val all = listOf(
            faction("Pitax"), faction("Mivon", standing = -30),
            faction("Restov", standing = 40, agenda = agenda(goalId = "build-army")),
        )
        val a = advanceAllAgendas(all, 4, catalog, archetypes, TurnRng(factionAgendaTurnSeed("Elk", 4)))
        val b = advanceAllAgendas(all, 4, catalog, archetypes, TurnRng(factionAgendaTurnSeed("Elk", 4)))
        assertEquals(a, b)
        val c = advanceAllAgendas(all, 5, catalog, archetypes, TurnRng(factionAgendaTurnSeed("Elk", 5)))
        assertTrue(a.moves.map { it.moveId } != c.moves.map { it.moveId } || a != c)
    }

    @Test
    fun factionsReturnInOriginalOrderAndIdleFactionsUntouched() {
        val idle = faction("Varnhold", agenda = null)
        val all = listOf(faction("Pitax"), idle, faction("Mivon", standing = -10))
        val result = advanceAllAgendas(all, 2, catalog, archetypes, TurnRng(8))
        assertEquals(listOf("Pitax", "Varnhold", "Mivon"), result.factions.map { it.name })
        assertNull(result.factions[1].agenda)
        assertTrue(result.moves.none { it.factionName == "Varnhold" })
    }
}
