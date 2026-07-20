package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaravanRulesTest {
    @Test
    fun bonusRdCapScalesWithKingdomLevelMinimumOne() {
        assertEquals(1, caravanBonusRdCap(kingdomLevel = 1))
        assertEquals(8, caravanBonusRdCap(kingdomLevel = 8))
        assertEquals(20, caravanBonusRdCap(kingdomLevel = 20))
        assertEquals(1, caravanBonusRdCap(kingdomLevel = 0))  // floor
    }

    @Test
    fun capBindsOnlyWhenExceeded() {
        assertEquals(5, capCaravanBonusRd(bonusRd = 5, cap = 8))   // under cap, unchanged
        assertEquals(8, capCaravanBonusRd(bonusRd = 12, cap = 8))  // over cap, clamped
        assertEquals(8, capCaravanBonusRd(bonusRd = 8, cap = 8))   // exactly at cap
        assertEquals(0, capCaravanBonusRd(bonusRd = -3, cap = 8))  // never negative
    }

    @Test
    fun embargoBlocksAtWarPartnersOnly() {
        assertTrue(canDispatchCaravanTo(partnerAtWar = false))
        assertFalse(canDispatchCaravanTo(partnerAtWar = true))
    }
}
