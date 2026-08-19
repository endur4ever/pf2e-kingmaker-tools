package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals

class HexSceneSourceTest {
    @Test
    fun `the configured hexploration scene wins over the active scene`() {
        // The whole point: at camp time the active scene is usually a campsite or battle map, and
        // the configured hex map is the only thing that still knows where the party actually is.
        assertEquals(HexSceneSource.CONFIGURED, hexSceneSource(true, false))
        assertEquals(HexSceneSource.CONFIGURED, hexSceneSource(true, null))
        assertEquals(HexSceneSource.CONFIGURED, hexSceneSource(true, true))
    }

    @Test
    fun `falls back to the active scene when no hexploration scene is configured`() {
        // Preserves the behaviour of every world that never set the pointer.
        assertEquals(HexSceneSource.ACTIVE, hexSceneSource(null, true))
    }

    @Test
    fun `a configured scene that is not a hex grid falls through rather than giving up`() {
        // A stale or mis-picked pointer is a misconfiguration. Treating it as authoritative would
        // disable hex rules on a world that is currently sitting on a perfectly good hex map.
        assertEquals(HexSceneSource.ACTIVE, hexSceneSource(false, true))
    }

    @Test
    fun `no hex map anywhere means no derivable hex position`() {
        assertEquals(HexSceneSource.NONE, hexSceneSource(null, null))
        assertEquals(HexSceneSource.NONE, hexSceneSource(null, false))
        assertEquals(HexSceneSource.NONE, hexSceneSource(false, false))
        assertEquals(HexSceneSource.NONE, hexSceneSource(false, null))
    }
}
