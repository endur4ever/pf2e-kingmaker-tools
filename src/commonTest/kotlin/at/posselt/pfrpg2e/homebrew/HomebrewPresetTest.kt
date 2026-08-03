package at.posselt.pfrpg2e.homebrew

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HomebrewPresetTest {
    @Test
    fun `NONE serializes to lowercase string`() {
        // This will fail if HomebrewPreset enum does not exist or value is not "none"
        // Using reflection to avoid compile errors if enum missing? Actually would not compile.
        // Placeholder to force compilation error when missing.
        assertEquals("none", HomebrewPreset.NONE.value)
    }

    @Test
    fun `GREGORY serializes to lowercase string`() {
        assertEquals("gregory", HomebrewPreset.GREGORY.value)
    }

    @Test
    fun `fromString parses gregory`() {
        assertEquals(HomebrewPreset.GREGORY, HomebrewPreset.fromString("gregory"))
    }

    @Test
    fun `fromString parses none`() {
        assertEquals(HomebrewPreset.NONE, HomebrewPreset.fromString("none"))
    }
}
