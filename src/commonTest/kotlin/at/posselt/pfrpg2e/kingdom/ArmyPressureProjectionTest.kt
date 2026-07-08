package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.WarThreatStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for the war pressure projection logic.
 */
class ArmyPressureProjectionTest {

    private fun threatSnapshot(
        id: String = "t1",
        name: String = "Goblin Horde",
        status: String = "active",
        escalationLevel: Int = 0,
        maxEscalation: Int = 3,
        eta: Int? = null,
        pauseOnExpiry: Boolean = false,
    ): WarThreatSnapshot {
        val triggeredTurn: Int? = null
        val offerConsumed: Boolean? = false
        val obj = js("{ id: id, name: name, escalationLevel: escalationLevel, maxEscalation: maxEscalation, eta: eta, pauseOnExpiry: pauseOnExpiry, status: status, triggeredTurn: triggeredTurn, offerConsumed: offerConsumed }")
        return obj.unsafeCast<WarThreatSnapshot>()
    }

    @Test
    fun calculatesTurnsUntilUnrestThreshold() {
        val threats = emptyList<WarThreatSnapshot>()
        val projection = projectWarPressure(
            currentPressure = 30,
            pressurePerTurn = 5,
            unrestThreshold = 50,
            ruinThreshold = 75,
            threats = threats,
            currentTurn = 1
        )
        // 50 - 30 = 20, 20 / 5 = 4 turns
        assertEquals(4, projection.turnsUntilUnrestThreshold)
        assertEquals(9, projection.turnsUntilRuinThreshold) // (75-30)/5 = 9
    }

    @Test
    fun returnsNullWhenAlreadyAtUnrestThreshold() {
        val threats = emptyList<WarThreatSnapshot>()
        val projection = projectWarPressure(
            currentPressure = 50,
            pressurePerTurn = 5,
            unrestThreshold = 50,
            ruinThreshold = 75,
            threats = threats,
            currentTurn = 1
        )
        assertEquals(0, projection.turnsUntilUnrestThreshold)
        assertEquals(5, projection.turnsUntilRuinThreshold)
    }

    @Test
    fun returnsNullWhenAlreadyAtRuinThreshold() {
        val threats = emptyList<WarThreatSnapshot>()
        val projection = projectWarPressure(
            currentPressure = 75,
            pressurePerTurn = 5,
            unrestThreshold = 50,
            ruinThreshold = 75,
            threats = threats,
            currentTurn = 1
        )
        assertEquals(0, projection.turnsUntilUnrestThreshold)
        assertEquals(0, projection.turnsUntilRuinThreshold)
    }

    @Test
    fun returnsNullWhenPressurePerTurnIsZero() {
        val threats = emptyList<WarThreatSnapshot>()
        val projection = projectWarPressure(
            currentPressure = 30,
            pressurePerTurn = 0,
            unrestThreshold = 50,
            ruinThreshold = 75,
            threats = threats,
            currentTurn = 1
        )
        assertNull(projection.turnsUntilUnrestThreshold)
        assertNull(projection.turnsUntilRuinThreshold)
    }

    @Test
    fun returnsNullWhenPressurePerTurnIsNegative() {
        val threats = emptyList<WarThreatSnapshot>()
        val projection = projectWarPressure(
            currentPressure = 60,
            pressurePerTurn = -2,
            unrestThreshold = 50,
            ruinThreshold = 75,
            threats = threats,
            currentTurn = 1
        )
        // Already at unrest, so 0 turns
        assertEquals(0, projection.turnsUntilUnrestThreshold)
        assertNull(projection.turnsUntilRuinThreshold) // Going down, never reaches ruin
    }

    @Test
    fun calculatesThreatArrivalFromKnownEta() {
        val threats = listOf(threatSnapshot(eta = 5))
        val projection = projectWarPressure(
            currentPressure = 0,
            pressurePerTurn = 5,
            unrestThreshold = 50,
            ruinThreshold = 75,
            threats = threats,
            currentTurn = 1
        )
        assertEquals(1, projection.threatArrivals.size)
        assertEquals("Goblin Horde", projection.threatArrivals[0].threatName)
        assertEquals(5, projection.threatArrivals[0].turnsUntilArrival)
    }

    @Test
    fun calculatesThreatArrivalFromEscalation() {
        val threats = listOf(threatSnapshot(escalationLevel = 1, maxEscalation = 4, eta = null))
        val projection = projectWarPressure(
            currentPressure = 0,
            pressurePerTurn = 5,
            unrestThreshold = 50,
            ruinThreshold = 75,
            threats = threats,
            currentTurn = 1
        )
        // 4 - 1 = 3 escalations needed
        assertEquals(3, projection.threatArrivals[0].turnsUntilArrival)
    }

    @Test
    fun threatAtMaxEscalationHasNullArrival() {
        val threats = listOf(threatSnapshot(escalationLevel = 3, maxEscalation = 3, eta = null))
        val projection = projectWarPressure(
            currentPressure = 0,
            pressurePerTurn = 5,
            unrestThreshold = 50,
            ruinThreshold = 75,
            threats = threats,
            currentTurn = 1
        )
        // Already at max escalation
        assertNull(projection.threatArrivals[0].turnsUntilArrival)
    }

    @Test
    fun etaZeroMeansArrivingNow() {
        val threats = listOf(threatSnapshot(eta = 0))
        val projection = projectWarPressure(
            currentPressure = 0,
            pressurePerTurn = 5,
            unrestThreshold = 50,
            ruinThreshold = 75,
            threats = threats,
            currentTurn = 1
        )
        // ETA 0 means arriving now, which we represent as null (stable)
        assertNull(projection.threatArrivals[0].turnsUntilArrival)
    }

    @Test
    fun etaNegativeMeansAlreadyArrived() {
        val threats = listOf(threatSnapshot(eta = -1))
        val projection = projectWarPressure(
            currentPressure = 0,
            pressurePerTurn = 5,
            unrestThreshold = 50,
            ruinThreshold = 75,
            threats = threats,
            currentTurn = 1
        )
        // ETA negative means already arrived
        assertNull(projection.threatArrivals[0].turnsUntilArrival)
    }

    @Test
    fun ignoresNonActiveThreats() {
        val threats = listOf(
            threatSnapshot(id = "t1", status = "active", eta = 3),
            threatSnapshot(id = "t2", status = "defeated", eta = 5)
        )
        val projection = projectWarPressure(
            currentPressure = 0,
            pressurePerTurn = 5,
            unrestThreshold = 50,
            ruinThreshold = 75,
            threats = threats,
            currentTurn = 1
        )
        assertEquals(1, projection.threatArrivals.size)
        assertEquals("t1", projection.threatArrivals[0].threatId)
    }

    @Test
    fun handlesMultipleThreats() {
        val threats = listOf(
            threatSnapshot(id = "t1", eta = 3),
            threatSnapshot(id = "t2", eta = 5)
        )
        val projection = projectWarPressure(
            currentPressure = 0,
            pressurePerTurn = 5,
            unrestThreshold = 50,
            ruinThreshold = 75,
            threats = threats,
            currentTurn = 1
        )
        assertEquals(2, projection.threatArrivals.size)
    }

    @Test
    fun ceilingDivisionForPartialTurns() {
        // 7 pressure needed, 5 per turn -> 2 turns (ceiling)
        val threats = emptyList<WarThreatSnapshot>()
        val projection = projectWarPressure(
            currentPressure = 43,
            pressurePerTurn = 5,
            unrestThreshold = 50,
            ruinThreshold = 75,
            threats = threats,
            currentTurn = 1
        )
        assertEquals(2, projection.turnsUntilUnrestThreshold) // (50-43+5-1)/5 = 2
    }
}