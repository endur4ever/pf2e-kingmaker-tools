package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals

class SiegeDamageTest {
    @Test
    fun aNonEscalatedThreatRazesNothingButStillStirsUnrest() {
        val r = calculateSiegeDamage(threatEscalation = 0, maxEscalation = 4, defensiveStructureCount = 0)
        assertEquals(0, r.structuresDestroyed)
        assertEquals(1, r.unrestGain)  // the assault itself
    }

    @Test
    fun aFullyEscalatedThreatRazesTheMaximum() {
        val r = calculateSiegeDamage(threatEscalation = 4, maxEscalation = 4, defensiveStructureCount = 0)
        assertEquals(MAX_SIEGE_STRUCTURES_DESTROYED, r.structuresDestroyed)
        assertEquals(4, r.unrestGain)  // 1 + 3
    }

    @Test
    fun anyPositiveEscalationRazesAtLeastOne() {
        val r = calculateSiegeDamage(threatEscalation = 1, maxEscalation = 6, defensiveStructureCount = 0)
        assertEquals(1, r.structuresDestroyed)
        assertEquals(2, r.unrestGain)
    }

    @Test
    fun everyTwoDefensiveStructuresPreventOneDestruction() {
        val r = calculateSiegeDamage(threatEscalation = 4, maxEscalation = 4, defensiveStructureCount = 4)
        assertEquals(1, r.structuresDestroyed)  // 3 base - (4/2)
    }

    @Test
    fun heavyDefensesCanFullyRepelTheSack() {
        val r = calculateSiegeDamage(threatEscalation = 4, maxEscalation = 4, defensiveStructureCount = 8)
        assertEquals(0, r.structuresDestroyed)  // never below 0
        assertEquals(1, r.unrestGain)
    }

    @Test
    fun escalationAndMaxAreClampedDefensively() {
        // over-max escalation clamps to max; a zero/negative max is treated as 1
        assertEquals(MAX_SIEGE_STRUCTURES_DESTROYED, calculateSiegeDamage(99, 4, 0).structuresDestroyed)
        assertEquals(MAX_SIEGE_STRUCTURES_DESTROYED, calculateSiegeDamage(5, 0, 0).structuresDestroyed)
        assertEquals(0, calculateSiegeDamage(-3, 4, 0).structuresDestroyed)
    }

    @Test
    fun defensiveCountTreatsVkVariantsAsBaseIds() {
        val ids = listOf("wall-stone", "garrison-vk", "castle-vk", "market", "wall-wooden-vk", "tavern")
        // wall-stone, garrison-vk->garrison, castle-vk->castle, wall-wooden-vk->wall-wooden = 4
        assertEquals(4, countDefensiveStructures(ids))
    }

    @Test
    fun nonDefensiveStructuresAreNotCounted() {
        assertEquals(0, countDefensiveStructures(listOf("market", "tavern", "mill", "shrine")))
    }
}
