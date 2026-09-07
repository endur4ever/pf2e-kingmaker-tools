package at.posselt.pfrpg2e.kingdom.map

import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
