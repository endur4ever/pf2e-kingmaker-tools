package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.kingdom.data.RawHexContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class Migration27Test {

    @Test
    fun `RawHexContent has all expected fields`() {
        // Verify the RawHexContent data class shape by constructing one
        val content = RawHexContent(
            id = "test-1",
            hexKey = "0,0",
            type = "landmark",
            name = "Old Ruins",
            visibility = "hidden",
            gmNotes = "GM only notes",
            playerText = "Discovered old ruins",
            suppressesEncounters = false,
            travelModifier = -1,
            linkedQuestId = "quest-1",
            linkedUuid = "uuid-123",
            icon = "fa-solid fa-monument",
        )
        assertEquals("test-1", content.id)
        assertEquals("0,0", content.hexKey)
        assertEquals("landmark", content.type)
        assertEquals("Old Ruins", content.name)
        assertEquals("hidden", content.visibility)
        assertEquals("GM only notes", content.gmNotes)
        assertEquals("Discovered old ruins", content.playerText)
        assertEquals(false, content.suppressesEncounters)
        assertEquals(-1, content.travelModifier)
        assertEquals("quest-1", content.linkedQuestId)
        assertEquals("uuid-123", content.linkedUuid)
        assertEquals("fa-solid fa-monument", content.icon)
    }

    @Test
    fun `RawHexContent with minimal fields`() {
        val content = RawHexContent(
            id = "test-2",
            hexKey = "1,2",
            type = "custom",
            name = "My Content",
            visibility = "discovered",
            gmNotes = "",
            playerText = "",
            suppressesEncounters = null,
            travelModifier = null,
            linkedQuestId = null,
            linkedUuid = null,
            icon = null,
        )
        assertEquals("test-2", content.id)
        assertEquals(null, content.suppressesEncounters)
        assertEquals(null, content.travelModifier)
        assertEquals(null, content.linkedQuestId)
        assertEquals(null, content.linkedUuid)
        assertEquals(null, content.icon)
    }
}
