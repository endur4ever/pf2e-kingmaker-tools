package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals

class PerformedActivitiesTest {

    @Test
    fun `sums performed counts by phase`() {
        val performed = mapOf(
            "craft-luxuries" to 1,
            "creative-solution" to 2,
            "build-structure" to 1,
        )
        val phases = mapOf(
            "craft-luxuries" to "leadership",
            "creative-solution" to "leadership",
            "build-structure" to "civic",
        )

        val result = sumPerformedByPhase(performed, phases)

        assertEquals(3, result["leadership"], "two leadership activities, one performed twice")
        assertEquals(1, result["civic"])
    }

    @Test
    fun `ignores activities with no known phase`() {
        val result = sumPerformedByPhase(mapOf("unknown-activity" to 5), emptyMap())
        assertEquals(0, result.size, "activities without a phase mapping are dropped")
    }

    @Test
    fun `empty performed map yields empty totals`() {
        assertEquals(0, sumPerformedByPhase(emptyMap(), mapOf("a" to "leadership")).size)
    }
}
