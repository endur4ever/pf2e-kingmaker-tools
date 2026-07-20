package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestAccessGrantsTest {
    @Test
    fun kingdomWideGrantAppliesEverywhere() {
        val grant = AccessGrant(benefitType = "trainer", value = "wizard", sourceQuestId = "q1", settlementId = null)
        assertTrue(grant.appliesTo("settlement-a"))
        assertTrue(grant.appliesTo("settlement-b"))
    }

    @Test
    fun scopedGrantAppliesOnlyToItsSettlement() {
        val grant = AccessGrant(benefitType = "trainer", value = "wizard", sourceQuestId = "q1", settlementId = "settlement-a")
        assertTrue(grant.appliesTo("settlement-a"))
        assertFalse(grant.appliesTo("settlement-b"))
    }

    @Test
    fun trainerAndCraftingGrantsUnionWithoutDuplicates() {
        val grants = listOf(
            AccessGrant("trainer", value = "wizard", sourceQuestId = "q1"),
            AccessGrant("trainer", value = "cleric", sourceQuestId = "q2", settlementId = "s1"),
            AccessGrant("crafting", value = "runes", sourceQuestId = "q3"),
        )
        val access = unionSettlementAccess(
            settlementId = "s1",
            baseTrainers = listOf("cleric", "fighter"),  // cleric already from a structure
            baseCrafting = listOf("metallic"),
            baseItemLevel = 3,
            grants = grants,
        )
        assertEquals(listOf("cleric", "fighter", "wizard"), access.trainers)  // cleric not duplicated
        assertEquals(listOf("metallic", "runes"), access.craftingAccess)
        assertEquals(3, access.itemLevel)  // no itemLevel grant -> structure level
    }

    @Test
    fun itemLevelGrantTakesTheMaxAgainstStructureLevel() {
        val grants = listOf(
            AccessGrant("itemLevel", amount = 12, sourceQuestId = "q1", settlementId = "s1"),
            AccessGrant("itemLevel", amount = 6, sourceQuestId = "q2"),  // kingdom-wide, lower
        )
        val access = unionSettlementAccess("s1", emptyList(), emptyList(), baseItemLevel = 9, grants = grants)
        assertEquals(12, access.itemLevel)  // max(9, 12, 6)
    }

    @Test
    fun grantsForOtherSettlementsDoNotLeakIn() {
        val grants = listOf(AccessGrant("trainer", value = "wizard", sourceQuestId = "q1", settlementId = "other"))
        val access = unionSettlementAccess("s1", baseTrainers = listOf("fighter"), emptyList(), 3, grants)
        assertEquals(listOf("fighter"), access.trainers)  // "wizard" scoped elsewhere
    }
}
