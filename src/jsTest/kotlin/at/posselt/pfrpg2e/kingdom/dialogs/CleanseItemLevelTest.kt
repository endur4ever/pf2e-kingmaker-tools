package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.kingdom.CleanseItemStructure
import at.posselt.pfrpg2e.kingdom.cleanseItemPlan
import com.foundryvtt.pf2e.item.PF2EItem
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Cleanse Item DC and luxury cost come from the item's level, so misreading it misprices the
 * whole ritual. Level is not on the typed facade, so this reaches into PF2e system data and must
 * survive items that carry none.
 */
class CleanseItemLevelTest {
    private fun itemWithLevel(level: Int): PF2EItem {
        val item = unsafeJso<dynamic>()
        item.system = unsafeJso<dynamic>()
        item.system.level = unsafeJso<dynamic>()
        item.system.level.value = level
        return item.unsafeCast<PF2EItem>()
    }

    @Test
    fun aLevelledItemReportsItsLevel() {
        assertEquals(12, cleanseItemLevelOf(itemWithLevel(12)))
    }

    @Test
    fun aLevelZeroItemIsZeroNotMissing() {
        assertEquals(0, cleanseItemLevelOf(itemWithLevel(0)))
    }

    @Test
    fun anItemWithNoLevelBlockIsZeroRatherThanThrowing() {
        // A GM can drop anything droppable onto the dialog, including types with no level.
        val bare = unsafeJso<dynamic>()
        bare.system = unsafeJso<dynamic>()
        assertEquals(0, cleanseItemLevelOf(bare.unsafeCast<PF2EItem>()))
    }

    @Test
    fun anItemWithNoSystemDataAtAllIsZero() {
        assertEquals(0, cleanseItemLevelOf(unsafeJso<dynamic>().unsafeCast<PF2EItem>()))
    }

    @Test
    fun theLevelDrivesTheRitualPrice() {
        // Ties this reader to what it exists for: a misread level misprices the ritual.
        val plan = cleanseItemPlan(itemLevel = cleanseItemLevelOf(itemWithLevel(16)), kingdomLevel = 12)
        assertEquals(8, plan.luxuryCost)
        assertEquals(CleanseItemStructure.CATHEDRAL, plan.requiredStructure)
    }
}
