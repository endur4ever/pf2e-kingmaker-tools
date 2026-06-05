package at.posselt.pfrpg2e.data.hex

import kotlin.test.Test
import kotlin.test.assertEquals

class HexContentEnumsTest {
    @Test
    fun `HexContentType value round-trip`() {
        HexContentType.entries.forEach { type ->
            val parsed = HexContentType.fromString(type.value)
            assertEquals(type, parsed, "Round-trip failed for ${type.name}")
        }
    }

    @Test
    fun `HexContentType i18nKey format`() {
        assertEquals("hexContentType.landmark", HexContentType.LANDMARK.i18nKey)
        assertEquals("hexContentType.refuge", HexContentType.REFUGE.i18nKey)
        assertEquals("hexContentType.worksite", HexContentType.WORKSITE.i18nKey)
        assertEquals("hexContentType.resource", HexContentType.RESOURCE.i18nKey)
        assertEquals("hexContentType.ruin", HexContentType.RUIN.i18nKey)
        assertEquals("hexContentType.merchant", HexContentType.MERCHANT.i18nKey)
        assertEquals("hexContentType.trainer", HexContentType.TRAINER.i18nKey)
        assertEquals("hexContentType.enemyArmy", HexContentType.ENEMY_ARMY.i18nKey)
        assertEquals("hexContentType.custom", HexContentType.CUSTOM.i18nKey)
    }

    @Test
    fun `HexContentVisibility value round-trip`() {
        HexContentVisibility.entries.forEach { vis ->
            val parsed = HexContentVisibility.fromString(vis.value)
            assertEquals(vis, parsed, "Round-trip failed for ${vis.name}")
        }
    }

    @Test
    fun `HexContentVisibility i18nKey format`() {
        assertEquals("hexContentVisibility.hidden", HexContentVisibility.HIDDEN.i18nKey)
        assertEquals("hexContentVisibility.discovered", HexContentVisibility.DISCOVERED.i18nKey)
        assertEquals("hexContentVisibility.cleared", HexContentVisibility.CLEARED.i18nKey)
    }
}
