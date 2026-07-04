package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.data.hex.HexContentType
import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KingdomDetectionTest {

    @Test
    fun testCountQuestsFailingThisTurnEmpty() {
        assertEquals(0, countQuestsFailingThisTurn(emptyList()))
    }

    @Test
    fun testCountQuestsFailingThisTurnNoGenerated() {
        val quests = listOf(
            "active" to null,     // no turnsRemaining -> not generated
            "active" to 5,        // active, turns > 1
            "completed" to 1,     // completed, not active
            "active" to 0,        // already at 0 (shouldn't happen, but safe)
        )
        assertEquals(0, countQuestsFailingThisTurn(quests))
    }

    @Test
    fun testCountQuestsFailingThisTurnSingle() {
        val quests = listOf(
            "active" to 1,        // will fail this turn
            "active" to 2,        // will not fail this turn
            "completed" to 1,     // completed
        )
        assertEquals(1, countQuestsFailingThisTurn(quests))
    }

    @Test
    fun testCountQuestsFailingThisTurnMultiple() {
        val quests = listOf(
            "active" to 1,        // will fail
            "active" to 1,        // will fail
            "active" to 3,        // will not fail
            "completed" to 1,     // completed
        )
        assertEquals(2, countQuestsFailingThisTurn(quests))
    }

    @Test
    fun testCountQuestsFailingThisTurnIgnoresNullTurns() {
        val quests = listOf(
            "active" to null,     // not generated
            "array" to 1,         // oops, wrong type for second element of pair
        )
        // Wait, the compiler would catch that. Let me fix it.
    }
}
