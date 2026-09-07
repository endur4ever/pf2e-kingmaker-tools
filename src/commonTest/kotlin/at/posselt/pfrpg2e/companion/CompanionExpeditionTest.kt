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
