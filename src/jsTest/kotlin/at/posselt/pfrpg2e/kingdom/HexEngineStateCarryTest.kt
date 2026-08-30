package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.camping.RawEncounterCreature
import at.posselt.pfrpg2e.camping.RawEncounterManifest
import at.posselt.pfrpg2e.kingdom.data.RawHexContent
import at.posselt.pfrpg2e.kingdom.data.carryHexEngineState
import at.posselt.pfrpg2e.kingdom.data.encounterManifestOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HexEngineStateCarryTest {
    private fun hex(id: String = "h1") = RawHexContent(
        id = id, hexKey = "5.5", type = "landmark", name = "Old Bridge",
        visibility = "hidden", gmNotes = "", playerText = "",
    )

    @Test
    fun rebuildKeepsEveryFieldTheFormNeverRenders() {
        val previous = hex().also {
            it.pendingEncounter = true
            it.encounterManifest = RawEncounterManifest(
                creatures = arrayOf(RawEncounterCreature(uuid = "Actor.a", count = 2)),
            )
            it.manifestAwarded = true
            it.manifestAwardedTurn = 9
        }
        // the manager rebuilds from form data, so the fresh row starts blank
        val rebuilt = carryHexEngineState(hex(), previous)
        assertEquals(true, rebuilt.pendingEncounter)
        assertEquals(2, rebuilt.encounterManifest?.creatures?.single()?.count)
        assertEquals(true, rebuilt.manifestAwarded)
        assertEquals(9, rebuilt.manifestAwardedTurn)
    }

    @Test
    fun blankPredecessorCarriesBlanks() {
        val rebuilt = carryHexEngineState(hex(), hex())
        assertNull(rebuilt.pendingEncounter)
        assertNull(rebuilt.encounterManifest)
    }

    @Test
    fun guardedReadTreatsAnEmptyManifestAsNone() {
        val empty = hex().also { it.encounterManifest = RawEncounterManifest(creatures = emptyArray()) }
        assertNull(empty.encounterManifestOrNull())
        assertNull(hex().encounterManifestOrNull())
        val curated = hex().also {
            it.encounterManifest = RawEncounterManifest(
                creatures = arrayOf(RawEncounterCreature(uuid = "Actor.a", count = 1)),
            )
        }
        assertTrue(curated.encounterManifestOrNull() != null)
    }
}
