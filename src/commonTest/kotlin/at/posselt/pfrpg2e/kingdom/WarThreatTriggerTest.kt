package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarThreatTriggerTest {

    private fun threat(
        id: String = "t1",
        name: String = "Goblin Horde",
        escalationLevel: Int = 0,
        maxEscalation: Int = 3,
        eta: Int? = null,
        pauseOnExpiry: Boolean = false,
        status: String = "active",
        triggeredTurn: Int? = null,
        offerConsumed: Boolean? = null,
    ): WarThreatSnapshot {
        val obj = js("{ id: id, name: name, escalationLevel: escalationLevel, maxEscalation: maxEscalation, eta: eta, pauseOnExpiry: pauseOnExpiry, status: status, triggeredTurn: triggeredTurn, offerConsumed: offerConsumed }")
        return obj.unsafeCast<WarThreatSnapshot>()
    }

    @Test
    fun detectsHardExpiryTrigger() {
        // Before: active, eta=0, escalationLevel=2, maxEscalation=3, triggeredTurn=null
        // After: tickWarThreat escalates to 3 (hits max), status=EXPIRED, triggeredTurn=7
        val before = arrayOf(threat(eta = 0, escalationLevel = 2, maxEscalation = 3, triggeredTurn = null))
        val after = arrayOf(threat(eta = 0, escalationLevel = 3, maxEscalation = 3, status = "expired", triggeredTurn = 7))

        val result = detectNewlyTriggeredThreats(before, after, currentTurn = 7)

        assertEquals(1, result.size)
        assertEquals("t1", result[0].id)
        assertEquals(7, result[0].triggeredTurn)
    }

    @Test
    fun detectsSoftPauseTrigger() {
        // Before: active, eta=0, escalationLevel=2, maxEscalation=3, pauseOnExpiry=true, triggeredTurn=null
        // After: tickWarThreat escalates to 3 (hits max), status stays ACTIVE (pauseOnExpiry), triggeredTurn=7
        val before = arrayOf(threat(
            eta = 0, escalationLevel = 2, maxEscalation = 3,
            pauseOnExpiry = true, triggeredTurn = null
        ))
        val after = arrayOf(threat(
            eta = 0, escalationLevel = 3, maxEscalation = 3,
            pauseOnExpiry = true, triggeredTurn = 7
        ))
    }
}
