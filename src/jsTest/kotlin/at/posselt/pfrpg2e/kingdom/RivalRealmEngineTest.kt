package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.RivalGrowthProfile
import at.posselt.pfrpg2e.data.kingdom.RivalStat
import at.posselt.pfrpg2e.kingdom.data.RawGroup
import at.posselt.pfrpg2e.kingdom.data.RawRivalRealm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RivalRealmEngineTest {
    private fun realm(
        id: String = "r1",
        factionRef: String? = "Pitax",
        size: Int = 10,
        fame: Int = 0,
        army: Int = 0,
        profile: String? = "pitax-wartime",
        mode: String? = null,
        paused: Boolean? = null,
        sizeAccrual: Double? = null,
    ): RawRivalRealm {
        val obj = js("{}").unsafeCast<RawRivalRealm>()
        obj.id = id
        obj.factionRef = factionRef
        obj.size = size
        obj.fame = fame
        obj.armyCount = army
        obj.growthProfile = profile
        obj.growthMode = mode
        obj.pauseGrowth = paused
        obj.sizeAccrual = sizeAccrual
        return obj
    }

    private fun group(name: String, atWar: Boolean): RawGroup {
        val obj = js("{}").unsafeCast<RawGroup>()
        obj.name = name
        obj.atWar = atWar
        obj.negotiationDC = 0
        obj.preventPledgeOfFealty = false
        obj.relations = "none"
        return obj
    }

    private val profiles = mapOf(
        "pitax-wartime" to RivalGrowthProfile(sizePerTurn = 0.33, famePerTurn = 0.5, armyPerTurn = 0.5),
        "dormant" to RivalGrowthProfile(sizePerTurn = 0.0, famePerTurn = 0.0, armyPerTurn = 0.0),
    )

    @Test
    fun growthAccruesFractionallyAndOnlyHeadlinesOnAWholePoint() {
        // 0.33/turn accrues 0.33 / 0.66 / 0.99 / 1.32 -- the hex lands on the FOURTH turn, not the
        // third. 0.99 is deliberately not rounded up: ACCRUAL_EPSILON exists to absorb binary
        // drift (ten ticks of 0.3 landing on 0.9999999999999998), not to round a real 0.99.
        // isolate SIZE: pitax-wartime also grows army at 0.5, which would land its own headline on
        // turn 2 and mask the schedule under test
        val sizeOnly = mapOf("s" to RivalGrowthProfile(sizePerTurn = 0.33, famePerTurn = 0.0, armyPerTurn = 0.0))
        var r = realm(profile = "s", sizeAccrual = 0.0)
        val seen = mutableListOf<RivalStat?>()
        repeat(4) { turn ->
            val out = growRivalRealm(r, sizeOnly, turn = turn, atWar = false)
            r = out.realm
            seen.add(out.move?.stat)
        }
        assertEquals(listOf(null, null, null, RivalStat.SIZE), seen.toList(), "three quiet turns, then the hex")
        assertEquals(11, r.size)
        // and the remainder carries rather than being discarded
        assertTrue((r.sizeAccrual ?: 0.0) > 0.3, "0.32 carries into the next turn")
    }

    @Test
    fun atMostOneHeadlinePerRealmPerTurnAndSizeOutranksTheRest() {
        // a profile that grows everything at once still yields ONE beat, and it is the size one
        val fat = mapOf("all" to RivalGrowthProfile(sizePerTurn = 1.0, famePerTurn = 1.0, armyPerTurn = 1.0))
        val out = growRivalRealm(realm(profile = "all"), fat, turn = 1, atWar = false)
        assertEquals(RivalStat.SIZE, out.move?.stat)
        assertEquals(11, out.realm.size)
        assertEquals(1, out.realm.fame, "the other stats still grow, they just do not headline")
        assertEquals(1, out.realm.armyCount)
    }

    @Test
    fun warSwapsTheArmyHeadlinePool() {
        val armyOnly = mapOf("a" to RivalGrowthProfile(sizePerTurn = 0.0, famePerTurn = 0.0, armyPerTurn = 1.0))
        val peace = growRivalRealm(realm(profile = "a"), armyOnly, turn = 1, atWar = false).move!!
        val war = growRivalRealm(realm(profile = "a"), armyOnly, turn = 1, atWar = true).move!!
        assertTrue(peace.pool != war.pool, "wartime musters read differently from peacetime ones")
    }

    @Test
    fun pausedAndAgendaDrivenRealmsDoNotAccrue() {
        // Use a rate that would increment IMMEDIATELY, so suppression is visible after one turn --
        // a fractional rate would leave size unchanged either way and prove nothing. Assert the
        // ACCRUAL too: suppression must not even bank progress toward a future point.
        val fast = mapOf("f" to RivalGrowthProfile(sizePerTurn = 1.0, famePerTurn = 1.0, armyPerTurn = 1.0))

        val paused = growRivalRealm(realm(profile = "f", paused = true), fast, turn = 1, atWar = false)
        assertEquals(10, paused.realm.size)
        assertEquals(0.0, paused.realm.sizeAccrual, "a paused realm banks nothing")
        assertNull(paused.move)

        val agenda = growRivalRealm(realm(profile = "f", mode = "agenda-driven"), fast, turn = 1, atWar = false)
        assertEquals(10, agenda.realm.size, "agenda-driven stats come from the parent engine, not flat accrual")
        assertEquals(0.0, agenda.realm.sizeAccrual, "and it banks nothing either, or it would double-grow later")
        assertNull(agenda.move)

        // control: the same profile on an ordinary realm DOES grow, so the assertions above are
        // testing suppression rather than an inert fixture
        val normal = growRivalRealm(realm(profile = "f"), fast, turn = 1, atWar = false)
        assertEquals(11, normal.realm.size)
    }

    @Test
    fun anUnknownProfileIdGrowsNothingRatherThanThrowing() {
        val out = growRivalRealm(realm(profile = "no-such-preset"), profiles, turn = 1, atWar = false)
        assertEquals(10, out.realm.size)
        assertNull(out.move)
    }

    @Test
    fun anExplicitPerRealmRateOverridesThePreset() {
        val r = realm(profile = "dormant")
        r.sizeGrowthPerTurn = 1.0
        val out = growRivalRealm(r, profiles, turn = 1, atWar = false)
        assertEquals(11, out.realm.size, "the explicit rate wins over the preset's zero")
    }

    @Test
    fun aRealmWithNoFactionRefGrowsButNeverHeadlines() {
        val fat = mapOf("all" to RivalGrowthProfile(sizePerTurn = 1.0, famePerTurn = 0.0, armyPerTurn = 0.0))
        val out = growRivalRealm(realm(factionRef = null, profile = "all"), fat, turn = 1, atWar = false)
        assertEquals(11, out.realm.size)
        assertNull(out.move, "there is no name to put in the prose")
    }

    @Test
    fun warStateComesFromTheLinkedGroupByNameCaseInsensitively() {
        val armyOnly = mapOf("a" to RivalGrowthProfile(sizePerTurn = 0.0, famePerTurn = 0.0, armyPerTurn = 1.0))
        val (_, moves) = advanceAllRivals(
            realms = arrayOf(realm(factionRef = "  pitax ", profile = "a")),
            groups = arrayOf(group("Pitax", atWar = true)),
            profiles = armyOnly,
            turn = 1,
        )
        assertEquals(1, moves.size)
        assertTrue(moves[0].pool.value.contains("War"), "the at-war group drove the wartime pool")
    }

    @Test
    fun aRenamedGroupReadsAsNotAtWarRatherThanBreakingTheTick() {
        val armyOnly = mapOf("a" to RivalGrowthProfile(sizePerTurn = 0.0, famePerTurn = 0.0, armyPerTurn = 1.0))
        val (rows, moves) = advanceAllRivals(
            realms = arrayOf(realm(factionRef = "Pitax", profile = "a")),
            groups = arrayOf(group("Pitax of the River Kings", atWar = true)),
            profiles = armyOnly,
            turn = 1,
        )
        assertEquals(1, rows.size)
        assertEquals(1, moves.size)
        assertTrue(!moves[0].pool.value.contains("War"), "a soft-FK miss degrades to peacetime, it does not throw")
    }

    @Test
    fun advancingIsDeterministicSoPreviewAndCommitAgree() {
        // the parity contract: running the same turn twice from the same input must be identical
        val input = { arrayOf(realm(id = "a", sizeAccrual = 0.9), realm(id = "b", factionRef = "Mivon", fame = 3)) }
        val (rowsA, movesA) = advanceAllRivals(input(), emptyArray(), profiles, turn = 7)
        val (rowsB, movesB) = advanceAllRivals(input(), emptyArray(), profiles, turn = 7)
        assertEquals(rowsA.map { it.size }, rowsB.map { it.size })
        assertEquals(rowsA.map { it.sizeAccrual }, rowsB.map { it.sizeAccrual })
        assertEquals(movesA.map { it.templateIndex }, movesB.map { it.templateIndex })
        assertEquals(movesA.map { it.stat }, movesB.map { it.stat })
    }

    @Test
    fun rowOrderIsPreservedSoThePersistedArrayDoesNotChurn() {
        val (rows, _) = advanceAllRivals(
            realms = arrayOf(realm(id = "z"), realm(id = "a"), realm(id = "m")),
            groups = emptyArray(),
            profiles = profiles,
            turn = 1,
        )
        assertEquals(listOf("z", "a", "m"), rows.map { it.id })
    }

    @Test
    fun anEmptyOrAbsentLedgerIsANoOp() {
        assertEquals(0, advanceAllRivals(null, emptyArray(), profiles, 1).first.size)
        assertEquals(0, advanceAllRivals(emptyArray(), emptyArray(), profiles, 1).second.size)
    }

    @Test
    fun everyShippedPresetResolvesAndOnlyDormantIsInert() {
        val shipped = rivalGrowthProfilesById()
        assertEquals(6, shipped.size)
        assertTrue("pitax-wartime" in shipped && "dormant" in shipped)
        val dormant = shipped.getValue("dormant")
        assertEquals(0.0, dormant.sizePerTurn + dormant.famePerTurn + dormant.armyPerTurn)
        assertTrue(shipped.getValue("expansionist-endgame").sizePerTurn > 0.0)
    }

}
