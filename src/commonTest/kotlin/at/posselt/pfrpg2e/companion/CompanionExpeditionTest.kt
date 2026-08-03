package at.posselt.pfrpg2e.companion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompanionExpeditionTest {

    private val sampleExpedition = CompanionExpedition(
        id = "exp-1",
        name = "Scout the Borderlands",
        description = "Scout the northern border for threats.",
        dc = 15,
        durationDays = 3,
        xpReward = 80,
        influenceReward = 1,
        loot = "Potion of Healing",
        tags = arrayOf("exploration"),
    )

    @Test
    fun testResolveExpedition_criticalSuccess() {
        val result = resolveExpedition(sampleExpedition, roll = 25, degreeOfSuccess = "critical_success")
        assertEquals(82, result.xpEarned) // 80 + 2 bonus
        assertEquals(2, result.influenceEarned) // 1 + 1 bonus
        assertTrue(result.lootObtained)
        assertEquals("critical_success", result.degreeOfSuccess)
    }

    @Test
    fun testResolveExpedition_success() {
        val result = resolveExpedition(sampleExpedition, roll = 18, degreeOfSuccess = "success")
        assertEquals(80, result.xpEarned)
        assertEquals(1, result.influenceEarned)
        assertFalse(result.lootObtained)
        assertEquals("success", result.degreeOfSuccess)
    }

    @Test
    fun testResolveExpedition_failure() {
        val result = resolveExpedition(sampleExpedition, roll = 10, degreeOfSuccess = "failure")
        assertEquals(40, result.xpEarned) // 80 / 2
        assertEquals(0, result.influenceEarned)
        assertFalse(result.lootObtained)
        assertEquals("failure", result.degreeOfSuccess)
    }

    @Test
    fun testResolveExpedition_criticalFailure() {
        val result = resolveExpedition(sampleExpedition, roll = 2, degreeOfSuccess = "critical_failure")
        assertEquals(0, result.xpEarned)
        assertEquals(0, result.influenceEarned)
        assertFalse(result.lootObtained)
        assertEquals("critical_failure", result.degreeOfSuccess)
    }

    @Test
    fun testResolveExpedition_noLootOnSuccessWithoutLoot() {
        val noLootExpedition = sampleExpedition.copy(loot = null)
        val result = resolveExpedition(noLootExpedition, roll = 25, degreeOfSuccess = "critical_success")
        assertFalse(result.lootObtained)
    }

    @Test
    fun testCompanionExpedition_equality() {
        val a = sampleExpedition
        val b = sampleExpedition.copy(name = "Different Name")
        assertEquals(a, b) // Equal by id
    }

    @Test
    fun testCompanionExpedition_inequality() {
        val a = sampleExpedition
        val b = sampleExpedition.copy(id = "exp-2")
        assertFalse(a == b)
    }
}
