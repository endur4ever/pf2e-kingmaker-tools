package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals

class GarrisonDefenseTest {
    private val assignments = listOf(
        GarrisonAssignment("army-1", "settlement-A"),
        GarrisonAssignment("army-2", "settlement-B"),
        GarrisonAssignment("army-3", "settlement-A"),
        GarrisonAssignment("army-4", null),
    )

    @Test
    fun garrisonedArmiesForASettlementAreFilteredInOrder() {
        assertEquals(listOf("army-1", "army-3"), garrisonedArmyIdsFor("settlement-A", assignments))
        assertEquals(listOf("army-2"), garrisonedArmyIdsFor("settlement-B", assignments))
        assertEquals(emptyList(), garrisonedArmyIdsFor("settlement-Z", assignments))
    }

    @Test
    fun garrisonDefendersAreUnionedWithoutDuplicates() {
        // army-1 already a defender; garrison adds army-3, army-1 not duplicated
        val defenders = withGarrisonDefenders(listOf("army-1"), listOf("army-1", "army-3"))
        assertEquals(listOf("army-1", "army-3"), defenders)
    }

    @Test
    fun aPresentGarrisonSparesOneStructure() {
        // no defenses, full escalation -> 3 destroyed; garrison spares 1 -> 2
        val withoutG = siegeDamageWithGarrison(4, 4, 0, garrisonPresent = false)
        val withG = siegeDamageWithGarrison(4, 4, 0, garrisonPresent = true)
        assertEquals(3, withoutG.structuresDestroyed)
        assertEquals(2, withG.structuresDestroyed)
        assertEquals(3, withG.unrestGain)  // 1 + 2, recomputed from reduced count
    }

    @Test
    fun garrisonReductionNeverGoesNegativeAndKeepsAssaultUnrest() {
        // already 0 destroyed (heavy walls) -> garrison can't push below 0
        val r = siegeDamageWithGarrison(1, 6, 8, garrisonPresent = true)
        assertEquals(0, r.structuresDestroyed)
        assertEquals(1, r.unrestGain)  // the assault itself
    }

    @Test
    fun garrisonMatchesPlainSiegeWhenAbsent() {
        val plain = calculateSiegeDamage(3, 4, 2)
        val absent = siegeDamageWithGarrison(3, 4, 2, garrisonPresent = false)
        assertEquals(plain, absent)
    }

    @Test
    fun defensiveBonusOnlyAppliesWithAGarrisonStructure() {
        assertEquals(1, garrisonDefensiveBonus(inSettlementWithGarrisonStructure = true))
        assertEquals(0, garrisonDefensiveBonus(inSettlementWithGarrisonStructure = false))
    }
}
