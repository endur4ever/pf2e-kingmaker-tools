package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.checks.getLevelBasedDC
import kotlin.test.Test
import kotlin.test.assertEquals

class CleanseItemTest {
    @Test
    fun counteractLevelIsKingdomLevelHalvedRoundedUp() {
        assertEquals(1, cleanseItemPlan(itemLevel = 1, kingdomLevel = 1).counteractLevel)
        assertEquals(1, cleanseItemPlan(itemLevel = 1, kingdomLevel = 2).counteractLevel)
        assertEquals(2, cleanseItemPlan(itemLevel = 1, kingdomLevel = 3).counteractLevel)
        assertEquals(3, cleanseItemPlan(itemLevel = 1, kingdomLevel = 5).counteractLevel)
        assertEquals(10, cleanseItemPlan(itemLevel = 1, kingdomLevel = 20).counteractLevel)
    }

    @Test
    fun dcIsIncrediblyHardForTheItemLevel() {
        // Incredibly hard = the item-level DC plus 10.
        assertEquals(getLevelBasedDC(3) + 10, cleanseItemPlan(itemLevel = 3, kingdomLevel = 10).dc)
        assertEquals(getLevelBasedDC(17) + 10, cleanseItemPlan(itemLevel = 17, kingdomLevel = 10).dc)
        assertEquals(getLevelBasedDC(0) + 10, cleanseItemPlan(itemLevel = 0, kingdomLevel = 10).dc)
    }

    @Test
    fun luxuryCostAndStructureMatchTheHouseRulesTable() {
        // 1-5: 1 Luxury, Shrine
        for (lvl in 1..5) {
            val p = cleanseItemPlan(itemLevel = lvl, kingdomLevel = 8)
            assertEquals(1, p.luxuryCost)
            assertEquals(CleanseItemStructure.SHRINE, p.requiredStructure)
        }
        // 6-10: 2 Luxuries, Temple
        for (lvl in 6..10) {
            val p = cleanseItemPlan(itemLevel = lvl, kingdomLevel = 8)
            assertEquals(2, p.luxuryCost)
            assertEquals(CleanseItemStructure.TEMPLE, p.requiredStructure)
        }
        // 11-15: 4 Luxuries, Temple
        for (lvl in 11..15) {
            val p = cleanseItemPlan(itemLevel = lvl, kingdomLevel = 8)
            assertEquals(4, p.luxuryCost)
            assertEquals(CleanseItemStructure.TEMPLE, p.requiredStructure)
        }
        // 16-20: 8 Luxuries, Cathedral
        for (lvl in 16..20) {
            val p = cleanseItemPlan(itemLevel = lvl, kingdomLevel = 8)
            assertEquals(8, p.luxuryCost)
            assertEquals(CleanseItemStructure.CATHEDRAL, p.requiredStructure)
        }
    }

    @Test
    fun boundaryLevelsPickTheCorrectBand() {
        assertEquals(CleanseItemStructure.SHRINE, cleanseItemPlan(5, 8).requiredStructure)
        assertEquals(CleanseItemStructure.TEMPLE, cleanseItemPlan(6, 8).requiredStructure)
        assertEquals(CleanseItemStructure.TEMPLE, cleanseItemPlan(15, 8).requiredStructure)
        assertEquals(CleanseItemStructure.CATHEDRAL, cleanseItemPlan(16, 8).requiredStructure)
        // Above 20 stays in the top band rather than falling through.
        assertEquals(8, cleanseItemPlan(25, 8).luxuryCost)
    }
}
