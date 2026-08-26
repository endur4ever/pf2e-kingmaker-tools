package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.data.kingdom.RivalGrowthProfile
import at.posselt.pfrpg2e.data.kingdom.rivalPowerScore
import at.posselt.pfrpg2e.kingdom.data.RawGroup
import at.posselt.pfrpg2e.kingdom.data.RawRivalRealm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RivalRealmsContextTest {
    private fun realm(
        id: String,
        factionRef: String? = "Pitax",
        size: Int = 10,
        fame: Int = 0,
        army: Int = 0,
        profile: String? = null,
        sizeOverride: Double? = null,
    ): RawRivalRealm {
        val obj = js("{}").unsafeCast<RawRivalRealm>()
        obj.id = id
        obj.factionRef = factionRef
        obj.size = size
        obj.fame = fame
        obj.armyCount = army
        obj.growthProfile = profile
        obj.sizeGrowthPerTurn = sizeOverride
        return obj
    }

    private fun group(name: String): RawGroup {
        val obj = js("{}").unsafeCast<RawGroup>()
        obj.name = name
        obj.negotiationDC = 0
        obj.atWar = false
        obj.relations = "none"
        obj.preventPledgeOfFealty = false
        return obj
    }

    private val profiles = mapOf(
        "pitax-wartime" to RivalGrowthProfile(sizePerTurn = 0.33, famePerTurn = 0.5, armyPerTurn = 0.5),
    )

    private fun build(
        rivals: Array<RawRivalRealm>?,
        groups: Array<RawGroup> = arrayOf(group("Pitax")),
        isGM: Boolean = true,
    ) = buildRivalRealmsContext(
        rivals = rivals,
        groups = groups,
        profiles = profiles,
        playerLabel = "Vaskonia",
        playerSize = 12,
        playerFame = 3,
        isGM = isGM,
    )

    @Test
    fun ranksThePlayerAmongTheRivalsOnScoreWithoutAnArmyTerm() {
        val ctx = build(arrayOf(
            realm("big", size = 20),   // outranks the player
            realm("small", size = 2),  // does not
        ))
        assertEquals(listOf("big", "Vaskonia", "small"), ctx.rows.map { r -> if (r.isPlayer) "Vaskonia" else r.id })
        assertEquals(listOf(1, 2, 3), ctx.rows.map { it.rank })
        val player = ctx.rows.first { it.isPlayer }
        assertEquals(rivalPowerScore(12, 3, 0), player.score, "the player scores on size + fame only")
        assertEquals(0, player.armyCount)
        assertNull(player.growthSummary, "the player row has no dials even for the GM")
        assertNull(player.id)
    }

    @Test
    fun identicalTwinRivalsKeepTheirOwnIds() {
        // two rivals with the same faction and the same stats: a label- or stats-keyed pairing
        // would hand both rows the same id and the delete button would hit the wrong realm
        val ctx = build(arrayOf(realm("first"), realm("second")))
        val rivalIds = ctx.rows.filter { !it.isPlayer }.map { it.id }
        assertEquals(setOf("first", "second"), rivalIds.toSet())
        assertEquals(2, rivalIds.distinct().size)
    }

    @Test
    fun growthSummaryIsGmOnlyAndShowsWhatWillActuallyHappen() {
        val gm = build(arrayOf(realm("r", profile = "pitax-wartime", sizeOverride = 1.0)))
        val summary = gm.rows.first { !it.isPlayer }.growthSummary!!
        assertTrue("Size +1/t" in summary, "the explicit override wins over the preset in the summary: $summary")
        assertTrue("Fame +0.5/t" in summary, "unoverridden stats show the preset rate: $summary")
        assertTrue("(pitax-wartime)" in summary)

        val player = build(arrayOf(realm("r", profile = "pitax-wartime")), isGM = false)
        assertNull(player.rows.first { !it.isPlayer }.growthSummary, "players never see the dials")
        assertFalse(player.isGM)
    }

    @Test
    fun linkedUsesTheSameSoftMatchAsTheEngine() {
        val ctx = build(
            arrayOf(realm("a", factionRef = "  pitax "), realm("b", factionRef = "Mivon"), realm("c", factionRef = null)),
            groups = arrayOf(group("Pitax")),
        )
        val byId = ctx.rows.filter { !it.isPlayer }.associateBy { it.id }
        assertTrue(byId.getValue("a").linked, "case + whitespace differences still link, matching the war split")
        assertFalse(byId.getValue("b").linked, "a renamed-away group shows the broken-link icon")
        assertFalse(byId.getValue("c").linked)
        assertTrue(ctx.rows.first { it.isPlayer }.linked, "the player row never shows a broken link")
    }

    @Test
    fun noRivalsMeansEmptyStateNotASoloScoreboard() {
        // the player row alone would make rows.length >= 1 forever, hiding the empty-state; the
        // template gates on hasRivals instead
        assertFalse(build(null).hasRivals)
        assertFalse(build(emptyArray()).hasRivals)
        assertTrue(build(arrayOf(realm("r"))).hasRivals)
        assertEquals(1, build(null).rows.count { it.isPlayer })
    }
}
