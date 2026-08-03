package at.posselt.pfrpg2e.kingdom.map

import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import at.posselt.pfrpg2e.data.hex.HexContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HexDiscoveryTest {

    // ── nextVisibility ──

    @Test
    fun `hidden to discovered on discover`() {
        assertEquals(
            HexContentVisibility.DISCOVERED,
            nextVisibility(HexContentVisibility.HIDDEN, DiscoveryEvent.DISCOVER)
        )
    }

    @Test
    fun `discovered stays discovered on discover (idempotent)`() {
        assertEquals(
            HexContentVisibility.DISCOVERED,
            nextVisibility(HexContentVisibility.DISCOVERED, DiscoveryEvent.DISCOVER)
        )
    }

    @Test
    fun `cleared stays cleared on discover (idempotent)`() {
        assertEquals(
            HexContentVisibility.CLEARED,
            nextVisibility(HexContentVisibility.CLEARED, DiscoveryEvent.DISCOVER)
        )
    }

    @Test
    fun `hidden to cleared on clear`() {
        assertEquals(
            HexContentVisibility.CLEARED,
            nextVisibility(HexContentVisibility.HIDDEN, DiscoveryEvent.CLEAR)
        )
    }

    @Test
    fun `discovered to cleared on clear`() {
        assertEquals(
            HexContentVisibility.CLEARED,
            nextVisibility(HexContentVisibility.DISCOVERED, DiscoveryEvent.CLEAR)
        )
    }

    @Test
    fun `cleared stays cleared on clear (idempotent)`() {
        assertEquals(
            HexContentVisibility.CLEARED,
            nextVisibility(HexContentVisibility.CLEARED, DiscoveryEvent.CLEAR)
        )
    }

    @Test
    fun `reset goes back to hidden`() {
        assertEquals(
            HexContentVisibility.HIDDEN,
            nextVisibility(HexContentVisibility.DISCOVERED, DiscoveryEvent.RESET)
        )
        assertEquals(
            HexContentVisibility.HIDDEN,
            nextVisibility(HexContentVisibility.CLEARED, DiscoveryEvent.RESET)
        )
    }

    @Test
    fun `never regresses without reset`() {
        val vis = HexContentVisibility.HIDDEN
        val afterDiscover = nextVisibility(vis, DiscoveryEvent.DISCOVER)
        val afterClear = nextVisibility(afterDiscover, DiscoveryEvent.CLEAR)
        assertEquals(HexContentVisibility.DISCOVERED, afterDiscover)
        assertEquals(HexContentVisibility.CLEARED, afterClear)
    }

    // ── suppressesRandomEncounter ──

    @Test
    fun `claimed hex suppresses encounters`() {
        assertTrue(suppressesRandomEncounter(claimed = true, cleared = false, content = null))
    }

    @Test
    fun `cleared hex suppresses encounters`() {
        assertTrue(suppressesRandomEncounter(claimed = false, cleared = true, content = null))
    }

    @Test
    fun `unclaimed uncleared hex does not suppress`() {
        assertFalse(suppressesRandomEncounter(claimed = false, cleared = false, content = null))
    }

    @Test
    fun `content override false prevents suppression on claimed hex`() {
        val content = HexContent(
            id = "c1", hexKey = "0,0", type = HexContentType.LANDMARK,
            name = "Test", suppressesEncounters = false
        )
        assertFalse(suppressesRandomEncounter(claimed = true, cleared = false, content = content))
    }

    @Test
    fun `content override true enables suppression on unclaimed hex`() {
        val content = HexContent(
            id = "c1", hexKey = "0,0", type = HexContentType.LANDMARK,
            name = "Test", suppressesEncounters = true
        )
        assertTrue(suppressesRandomEncounter(claimed = false, cleared = false, content = content))
    }

    @Test
    fun `no content uses claimed cleared logic`() {
        assertFalse(suppressesRandomEncounter(claimed = false, cleared = false, content = null))
        assertTrue(suppressesRandomEncounter(claimed = true, cleared = false, content = null))
        assertTrue(suppressesRandomEncounter(claimed = false, cleared = true, content = null))
    }

    // ── aggregateTravelModifiers ──

    @Test
    fun `empty features and contents returns 0`() {
        assertEquals(0, aggregateTravelModifiers(emptyList(), emptyList()))
    }

    @Test
    fun `road feature reduces travel cost by 1`() {
        assertEquals(-1, aggregateTravelModifiers(listOf("road"), emptyList()))
    }

    @Test
    fun `content travel modifier adds to total`() {
        val contents = listOf(
            HexContent(id = "c1", hexKey = "0,0", type = HexContentType.LANDMARK, name = "Bridge", travelModifier = -2)
        )
        assertEquals(-2, aggregateTravelModifiers(emptyList(), contents))
    }

    @Test
    fun `road and content modifiers stack`() {
        val contents = listOf(
            HexContent(id = "c1", hexKey = "0,0", type = HexContentType.LANDMARK, name = "Bridge", travelModifier = -2)
        )
        assertEquals(-3, aggregateTravelModifiers(listOf("road"), contents))
    }

    @Test
    fun `multiple content modifiers sum`() {
        val contents = listOf(
            HexContent(id = "c1", hexKey = "0,0", type = HexContentType.LANDMARK, name = "A", travelModifier = -2),
            HexContent(id = "c2", hexKey = "0,0", type = HexContentType.RUIN, name = "B", travelModifier = 1),
        )
        assertEquals(-1, aggregateTravelModifiers(emptyList(), contents))
    }

    @Test
    fun `null travel modifier is ignored`() {
        val contents = listOf(
            HexContent(id = "c1", hexKey = "0,0", type = HexContentType.LANDMARK, name = "A", travelModifier = null)
        )
        assertEquals(0, aggregateTravelModifiers(emptyList(), contents))
    }

    // ── contentMarkerFor ──

    @Test
    fun `returns null for empty contents`() {
        assertNull(contentMarkerFor(claimed = false, cleared = false, contents = emptyList()))
    }

    @Test
    fun `returns null when all contents are hidden and filter excludes hidden`() {
        val contents = listOf(
            HexContent(id = "c1", hexKey = "0,0", type = HexContentType.LANDMARK, name = "A", visibility = HexContentVisibility.HIDDEN)
        )
        assertNull(
            contentMarkerFor(claimed = false, cleared = false, contents = contents) {
                it.visibility != HexContentVisibility.HIDDEN
            }
        )
    }

    @Test
    fun `picks highest priority content type`() {
        val contents = listOf(
            HexContent(id = "c1", hexKey = "0,0", type = HexContentType.LANDMARK, name = "Shrine"),
            HexContent(id = "c2", hexKey = "0,0", type = HexContentType.ENEMY_ARMY, name = "Goblin Camp"),
        )
        val marker = contentMarkerFor(claimed = false, cleared = false, contents = contents)
        assertNotNull(marker)
        assertEquals("Goblin Camp", marker.label)
        assertEquals("fa-solid fa-skull-crossbones", marker.icon)
        assertEquals("#cc0000", marker.tint)
    }

    @Test
    fun `single content returns its marker`() {
        val contents = listOf(
            HexContent(id = "c1", hexKey = "0,0", type = HexContentType.MERCHANT, name = "Trading Post"),
        )
        val marker = contentMarkerFor(claimed = false, cleared = false, contents = contents)
        assertNotNull(marker)
        assertEquals("Trading Post", marker.label)
        assertEquals("fa-solid fa-store", marker.icon)
        assertEquals("#ffd700", marker.tint)
    }

    @Test
    fun `custom type uses custom icon when provided`() {
        val contents = listOf(
            HexContent(id = "c1", hexKey = "0,0", type = HexContentType.CUSTOM, name = "My Thing", icon = "fa-solid fa-star"),
        )
        val marker = contentMarkerFor(claimed = false, cleared = false, contents = contents)
        assertNotNull(marker)
        assertEquals("fa-solid fa-star", marker.icon)
    }

    @Test
    fun `custom type uses default icon when no custom icon`() {
        val contents = listOf(
            HexContent(id = "c1", hexKey = "0,0", type = HexContentType.CUSTOM, name = "My Thing"),
        )
        val marker = contentMarkerFor(claimed = false, cleared = false, contents = contents)
        assertNotNull(marker)
        assertEquals("fa-solid fa-circle-question", marker.icon)
    }
}
