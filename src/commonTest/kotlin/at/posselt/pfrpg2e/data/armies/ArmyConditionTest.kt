package at.posselt.pfrpg2e.data.armies

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArmyConditionTest {
    @Test
    fun fromStringResolvesEachValue() {
        ArmyCondition.entries.forEach { c ->
            assertEquals(c, ArmyCondition.fromString(c.value))
        }
    }

    @Test
    fun fromStringReturnsNullForUnknown() {
        assertNull(ArmyCondition.fromString("not-a-condition"))
    }

    @Test
    fun valueIsCamelCase() {
        assertEquals("mired", ArmyCondition.MIRED.value)
        assertEquals("pinned", ArmyCondition.PINNED.value)
        assertEquals("weary", ArmyCondition.WEARY.value)
        assertEquals("damaged", ArmyCondition.DAMAGED.value)
        assertEquals("routed", ArmyCondition.ROUTED.value)
        assertEquals("destroyed", ArmyCondition.DESTROYED.value)
    }

    @Test
    fun i18nKeyUsesArmyConditionPrefix() {
        assertEquals("armyCondition.mired", ArmyCondition.MIRED.i18nKey)
        assertEquals("armyCondition.pinned", ArmyCondition.PINNED.i18nKey)
        assertEquals("armyCondition.destroyed", ArmyCondition.DESTROYED.i18nKey)
    }

    @Test
    fun thereAreSixConditions() {
        assertEquals(6, ArmyCondition.entries.size)
    }
}
