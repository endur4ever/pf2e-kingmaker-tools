package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawFactionAgenda
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

    @Test
    fun aStaleFormCannotResurrectADeletedFactionCarryingItsNeighboursHistory() {
        // Another client deleted Pitax between render and submit, so the form still posts three
        // rows against two stored ones. Matching by position here handed Pitax back Mivon's
        // standing and log, and wiped Brevoy's -- the exact loss this function exists to prevent.
        val submitted = arrayOf(group("Pitax"), group("Mivon"), group("Brevoy"))
        val existing = arrayOf(
            group("Mivon", standing = 30, log = arrayOf(entry(4))),
            group("Brevoy", standing = 10, log = arrayOf(entry(8))),
        )

        val merged = mergeSubmittedGroups(submitted, existing)

        assertNull(merged[0].standing)
        assertNull(merged[0].standingLog)
        assertEquals(30, merged[1].standing)
        assertEquals(10, merged[2].standing)
    }

    @Test
    fun renamingAFactionKeepsItsHistory() {
        // Same length, so position still means something -- and it is the only thing that carries
        // history through a rename, since the name lookup would find nothing.
        val submitted = arrayOf(group("Pitax the Greater"), group("Mivon"))
        val existing = arrayOf(group("Pitax", standing = -60), group("Mivon", standing = 30))

        val merged = mergeSubmittedGroups(submitted, existing)

        assertEquals(-60, merged[0].standing)
        assertEquals(30, merged[1].standing)
    }

    @Test
    fun mergeCarriesTheAgendaTheFormNeverRenders() {
        val agenda = RawFactionAgenda(
            goalId = "conquer-neighbor", goalTitle = "", progress = 3, segments = 6,
            archetype = "aggressive", moveCooldowns = js.objects.recordOf(),
            lastAdvancedTurn = 4, targetFaction = null,
        )
        val existing = arrayOf(group("Pitax").also { it.agenda = agenda })
        // the sheet DataModel strips agenda from submitted rows, exactly like standing
        val submitted = arrayOf(group("Pitax"))
        val merged = mergeSubmittedGroups(submitted, existing)
        assertEquals(3, merged[0].agenda?.progress)
        assertEquals(4, merged[0].agenda?.lastAdvancedTurn)
    }
}
