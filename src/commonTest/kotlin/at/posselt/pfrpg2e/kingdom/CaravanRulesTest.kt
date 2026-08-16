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

    @Test
    fun onlyPartnersWhoseWarFlagJustFlippedAreOffered() {
        val newly = partnersNewlyAtWar(
            before = mapOf("Pitax" to false, "Mivon" to true, "Brevoy" to false),
            after = mapOf("Pitax" to true, "Mivon" to true, "Brevoy" to false),
        )
        // Pitax flipped. Mivon was ALREADY at war -- its shipments were offered when that war began
        // and must not be re-offered on every later sheet save.
        assertEquals(listOf("Pitax"), newly)
    }

    @Test
    fun makingPeaceOffersNothing() {
        val newly = partnersNewlyAtWar(
            before = mapOf("Pitax" to true),
            after = mapOf("Pitax" to false),
        )
        assertEquals(emptyList(), newly)
    }

    @Test
    fun aBrandNewGroupAtWarIsNotTreatedAsADeclaration() {
        // A group that did not exist before has no shipments in transit, so there is nothing to
        // offer -- and `before[name] == false` correctly excludes an absent key.
        val newly = partnersNewlyAtWar(before = emptyMap(), after = mapOf("Pitax" to true))
        assertEquals(emptyList(), newly)
    }
}
