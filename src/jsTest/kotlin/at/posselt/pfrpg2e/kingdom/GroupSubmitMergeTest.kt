package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawFactionStandingEntry
import at.posselt.pfrpg2e.kingdom.data.RawGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupSubmitMergeTest {
    private fun group(
        name: String,
        standing: Int? = null,
        allianceLevel: String? = null,
        log: Array<RawFactionStandingEntry>? = null,
    ): RawGroup = RawGroup(
        name = name,
        negotiationDC = 15,
        atWar = false,
        preventPledgeOfFealty = false,
        relations = "none",
        standing = standing,
        standingLog = log,
        allianceLevel = allianceLevel,
        hexKey = null,
    )

    private fun entry(delta: Int) = RawFactionStandingEntry(
        turn = 3,
        delta = delta,
        reason = "kingdom.factionStanding.warVictory",
    )

    @Test
    fun carriesStandingHistoryAcrossASubmitThatDoesNotIncludeIt() {
        // What the form posts back: only the six rendered fields, so these three are absent.
        val submitted = arrayOf(group("Pitax"), group("Mivon"))
        val existing = arrayOf(
            group("Pitax", standing = -30, allianceLevel = "tribute", log = arrayOf(entry(-4))),
            group("Mivon", standing = 12, log = arrayOf(entry(4), entry(8))),
        )

        val merged = mergeSubmittedGroups(submitted, existing)

        assertEquals(-30, merged[0].standing)
        assertEquals("tribute", merged[0].allianceLevel)
        assertEquals(1, merged[0].standingLog?.size)
        assertEquals(12, merged[1].standing)
        assertEquals(2, merged[1].standingLog?.size)
    }

    @Test
    fun keepsTheEditableFieldsFromTheSubmitNotTheOldCopy() {
        val submitted = arrayOf(
            RawGroup.copy(group("Pitax"), atWar = true, negotiationDC = 22, relations = "trade-agreement"),
        )
        val existing = arrayOf(group("Pitax", standing = -30))

        val merged = mergeSubmittedGroups(submitted, existing)

        assertEquals(true, merged[0].atWar)
        assertEquals(22, merged[0].negotiationDC)
        assertEquals("trade-agreement", merged[0].relations)
        assertEquals(-30, merged[0].standing)
    }

    @Test
    fun aNewlyAddedGroupWithNoCounterpartGetsNullHistory() {
        val submitted = arrayOf(group("Pitax"), group("Brevoy"))
        val existing = arrayOf(group("Pitax", standing = -30))

        val merged = mergeSubmittedGroups(submitted, existing)

        assertEquals(-30, merged[0].standing)
        assertNull(merged[1].standing)
        assertNull(merged[1].standingLog)
        assertEquals(2, merged.size)
    }
}
