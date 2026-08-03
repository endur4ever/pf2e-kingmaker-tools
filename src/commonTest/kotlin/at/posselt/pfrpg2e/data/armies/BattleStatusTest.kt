package at.posselt.pfrpg2e.data.armies

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BattleStatusTest {
    @Test
    fun fromStringResolvesEachValue() {
        BattleStatus.entries.forEach { s ->
            assertEquals(s, BattleStatus.fromString(s.value))
        }
    }

    @Test
    fun fromStringReturnsNullForUnknown() {
        assertNull(BattleStatus.fromString("not-a-status"))
    }

    @Test
    fun valueIsCamelCase() {
        assertEquals("active", BattleStatus.ACTIVE.value)
        assertEquals("victory", BattleStatus.VICTORY.value)
        assertEquals("defeat", BattleStatus.DEFEAT.value)
        assertEquals("retreat", BattleStatus.RETREAT.value)
    }

    @Test
    fun i18nKeyUsesBattleStatusPrefix() {
        assertEquals("battleStatus.active", BattleStatus.ACTIVE.i18nKey)
        assertEquals("battleStatus.victory", BattleStatus.VICTORY.i18nKey)
        assertEquals("battleStatus.retreat", BattleStatus.RETREAT.i18nKey)
    }

    @Test
    fun thereAreFourStatuses() {
        assertEquals(4, BattleStatus.entries.size)
    }
}
