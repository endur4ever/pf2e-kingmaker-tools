package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawSubsystemThreshold
import at.posselt.pfrpg2e.kingdom.dialogs.parseSubsystemImport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

private fun th(points: Int, consumed: Boolean = false) =
    RawSubsystemThreshold(points = points, effect = "effect $points", offerConsumed = consumed, revealedToPlayers = false)

class SubsystemOffersTest {
    @Test
    fun offersFireOnlyForNewlyCrossedUnconsumedThresholds() {
        val rows = arrayOf(th(4), th(6, consumed = true), th(8))
        assertEquals(listOf(4), thresholdsToOffer(3, 5, rows).map { it.row.points })
        // 6 was crossed too, but its offer is consumed and stays quiet forever
        assertEquals(listOf(4), thresholdsToOffer(3, 7, rows).map { it.row.points })
        assertEquals(listOf(4, 8), thresholdsToOffer(0, 10, rows).map { it.row.points })
        // already AT the threshold, moving past it: no boundary re-fire
        assertEquals(0, thresholdsToOffer(4, 5, rows).size)
        // losses never offer
        assertEquals(0, thresholdsToOffer(5, 3, rows).size)
        assertEquals(0, thresholdsToOffer(3, 5, null).size)
    }

    @Test
    fun duplicatePointRowsEachKeepTheirOwnOffer() {
        // identity is the ROW INDEX: the surviving offer names exactly the unanswered row
        val consumedSecond = thresholdsToOffer(3, 4, arrayOf(th(4), th(4, consumed = true)))
        assertEquals(listOf(0), consumedSecond.map { it.index })
        val consumedFirst = thresholdsToOffer(3, 4, arrayOf(th(4, consumed = true), th(4)))
        assertEquals(listOf(1), consumedFirst.map { it.index })
        val both = thresholdsToOffer(3, 4, arrayOf(th(4), th(4)))
        assertEquals(listOf(0, 1), both.map { it.index })
    }

    @Test
    fun importParsesTheDocumentedShapeAndAssignsIdentity() {
        var n = 0
        val store = parseSubsystemImport(
            """
            {
              "influenceEncounters": [
                {
                  "name": "Example Banquet",
                  "npcName": "Example Court NPC",
                  "level": 4,
                  "discoveries": [ { "skill": "society", "dc": 18 } ],
                  "influenceSkills": [ { "skill": "diplomacy", "dc": 22 } ],
                  "thresholds": [ { "points": 4, "effect": "Shares a rumor." } ],
                  "resistances": [ { "label": "flattery", "delta": -1 } ],
                  "weaknesses": []
                }
              ],
              "researchProjects": [
                {
                  "name": "Example Library",
                  "maxResearchPoints": 15,
                  "checks": [ { "skill": "arcana", "dc": 21 } ],
                  "thresholds": [ { "points": 5, "effect": "First clue." } ]
                }
              ]
            }
            """.trimIndent(),
            now = 123.0,
        ) { "id-${n++}" }
        assertNotNull(store)
        val enc = store.influenceEncounters!!.single()
        assertEquals("id-0", enc.id)
        assertEquals("Example Banquet", enc.name)
        assertEquals(0, enc.influencePoints)
        assertEquals(false, enc.visibleToPlayers)
        assertEquals(false, enc.discoveries!!.single().revealed)
        assertEquals(-1, enc.resistances!!.single().delta)
        assertEquals(123.0, enc.createdAt)
        val proj = store.researchProjects!!.single()
        assertEquals("id-1", proj.id)
        assertEquals(15, proj.maxResearchPoints)
        assertEquals(false, proj.thresholds!!.single().revealedToPlayers)
    }

    @Test
    fun importIsAllOrNothing() {
        // missing dc on one check blocks the WHOLE paste
        assertNull(parseSubsystemImport(
            """{"influenceEncounters":[{"name":"A","influenceSkills":[{"skill":"diplomacy"}]}]}""",
            0.0) { "x" })
        assertNull(parseSubsystemImport("""{"researchProjects":[{"name":""}]}""", 0.0) { "x" })
        assertNull(parseSubsystemImport("not json", 0.0) { "x" })
        assertNull(parseSubsystemImport("{}", 0.0) { "x" })
        // a nonsense max is dropped, not fatal: the project still imports uncapped
        val store = parseSubsystemImport(
            """{"researchProjects":[{"name":"B","maxResearchPoints":-5}]}""", 0.0) { "x" }
        assertNotNull(store)
        assertNull(store.researchProjects!!.single().maxResearchPoints)
    }
}
