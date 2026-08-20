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
    fun dcIsTheCounteractDcForTheItemLevel() {
        // PF2e's counteract DC against a level-N item is that level's level-based DC. Deliberately
        // NOT +10 on top: the kingdom Control DC shares the same 14 + level + level/3 base, so this
        // makes cleansing an item of the kingdom's own level exactly as hard as a routine kingdom
        // check. A flat +10 made it the hardest action in the game (DC 50 at level 20 vs a Control
        // DC of 40), which is why the original reading was dropped.
        assertEquals(15, cleanseItemPlan(itemLevel = 1, kingdomLevel = 1).dc)
        assertEquals(20, cleanseItemPlan(itemLevel = 5, kingdomLevel = 1).dc)
        assertEquals(27, cleanseItemPlan(itemLevel = 10, kingdomLevel = 1).dc)
        assertEquals(40, cleanseItemPlan(itemLevel = 20, kingdomLevel = 1).dc)
    }

    @Test
    fun theDcTracksTheItemNotTheKingdom() {
        // A powerful kingdom does not get a discount on a dangerous item, and a weak one is not
        // punished for a trinket -- only the counteract LEVEL scales with the kingdom.
        assertEquals(
            cleanseItemPlan(itemLevel = 12, kingdomLevel = 1).dc,
            cleanseItemPlan(itemLevel = 12, kingdomLevel = 20).dc,
        )
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
