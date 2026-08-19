package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import at.posselt.pfrpg2e.kingdom.data.RawExpeditionChronicleEntry
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExpeditionChronicleContextTest {
    private fun entry(
        turn: Int,
        title: String,
        degree: String = "success",
        rp: Int = 0,
        standing: Int = 0,
        faction: String? = null,
    ) = unsafeJso<dynamic> {
        this.title = title
        this.companionNames = "Ekundayo"
        this.activityId = "scout"
        this.outcomeDegree = degree
        this.lootRp = rp
        this.factionStandingDelta = standing
        this.targetFactionName = faction
        this.turn = turn
        this.appliedAt = "2026-01-0${turn}T00:00:00Z"
    }.unsafeCast<RawExpeditionChronicleEntry>()

    private fun context(vararg chronicle: RawExpeditionChronicleEntry, isGM: Boolean = true) =
        emptyArray<RawCompanionExpedition>().toExpeditionsContext(
            isGM = isGM,
            companions = emptyArray(),
            chronicle = arrayOf(*chronicle),
        )

    @Test
    fun anEmptyChronicleRendersNoTimeline() {
        val c = context()
        assertFalse(c.hasChronicle)
        assertEquals(0, c.chronicleTurns.size)
    }

    @Test
    fun entriesReadNewestTurnFirst() {
        val c = context(entry(1, "Old scouting"), entry(7, "Recent scouting"), entry(3, "Middling"))
        assertEquals(listOf(7, 3, 1), c.chronicleTurns.map { it.turn })
        assertTrue(c.hasChronicle)
    }

    @Test
    fun entriesFromOneTurnStayTogetherInOrder() {
        val c = context(entry(4, "First"), entry(4, "Second"))
        assertEquals(1, c.chronicleTurns.size)
        assertEquals(listOf("First", "Second"), c.chronicleTurns[0].entries.map { it.title })
    }

    @Test
    fun aRunThatYieldedNothingShowsNoRewardsChip() {
        val c = context(entry(2, "Fruitless patrol"))
        val row = c.chronicleTurns[0].entries[0]
        assertFalse(row.hasRewards)
        assertEquals("", row.rewardsSummary)
    }

    @Test
    fun rewardsSummariseLootAndFactionStanding() {
        val c = context(entry(2, "Envoy", rp = 3, standing = 2, faction = "Pitax"))
        val row = c.chronicleTurns[0].entries[0]
        assertTrue(row.hasRewards)
        assertTrue(row.rewardsSummary.contains("3"), row.rewardsSummary)
        assertTrue(row.rewardsSummary.contains("Pitax +2"), row.rewardsSummary)
    }

    @Test
    fun aNegativeStandingKeepsItsSign() {
        val c = context(entry(2, "Botched envoy", degree = "criticalFailure", standing = -2, faction = "Mivon"))
        assertTrue(c.chronicleTurns[0].entries[0].rewardsSummary.contains("Mivon -2"))
    }

    @Test
    fun playersSeeTheChronicleToo() {
        // It is the party's own history of completed missions. Chronicle entries carry no
        // expedition id and no visibility flag, so there is nothing to join back to
        // visibleToPlayers -- that flag guards the in-progress board, not the record.
        val c = context(entry(2, "Scouting"), isGM = false)
        assertTrue(c.hasChronicle)
        assertEquals(1, c.chronicleTurns[0].entries.size)
    }
}
