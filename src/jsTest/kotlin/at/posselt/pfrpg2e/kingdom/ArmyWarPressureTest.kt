package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.ArmyDeploymentStatus
import at.posselt.pfrpg2e.kingdom.data.RawArmyDeployment
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import at.posselt.pfrpg2e.kingdom.data.WarThreatStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private fun threat(
    status: WarThreatStatus = WarThreatStatus.ACTIVE,
    escalationLevel: Int = 0,
    maxEscalation: Int = 3,
    eta: Int? = null,
    pauseOnExpiry: Boolean = false,
    triggeredTurn: Int? = null,
): RawWarThreat = RawWarThreat(
    id = "t1", name = "Goblin Horde", description = "", enemyFaction = null,
    escalationLevel = escalationLevel, maxEscalation = maxEscalation, eta = eta,
    targetSettlementSceneId = null, targetHexLocation = null, linkedQuestId = null,
    linkedEventId = null, pauseOnExpiry = pauseOnExpiry, status = status.value,
    triggeredTurn = triggeredTurn,
)

private fun deployment(status: ArmyDeploymentStatus = ArmyDeploymentStatus.DEPLOYED): RawArmyDeployment =
    RawArmyDeployment(
        id = "d1", armyActorUuid = "Actor.x", armyName = "1st Legion", armyType = "infantry",
        assignedThreatId = null, garrisonedSettlementId = null, status = status.value, deployedTurn = 0,
    )

class WarPressureCalculationTest {
    @Test
    fun pressureRisesPerActiveThreat() {
        val p = recalculateWarPressure(arrayOf(threat(), threat()), emptyArray(), null)
        assertEquals(10, p.pressurePerTurn)      // 2 threats * 5
        assertEquals(10, p.currentPressure)      // 0 + 10
        assertEquals(10, p.lastChange)
    }

    @Test
    fun deployedArmiesReducePressureAndClampAtZero() {
        val p = recalculateWarPressure(arrayOf(threat()), arrayOf(deployment(), deployment(), deployment()), null)
        // 1*5 - 3*2 = -1, clamped to 0
        assertEquals(-1, p.pressurePerTurn)
        assertEquals(0, p.currentPressure)
    }

    @Test
    fun nonActiveThreatsDoNotAddPressure() {
        val p = recalculateWarPressure(arrayOf(threat(status = WarThreatStatus.DEFEATED)), emptyArray(), null)
        assertEquals(0, p.pressurePerTurn)
    }

    @Test
    fun crossingUnrestThresholdSetsModifier() {
        var pressure = defaultWarPressure() // threshold 50
        // 11 active threats => +55/turn, one tick crosses 50
        val many = Array(11) { threat() }
        pressure = recalculateWarPressure(many, emptyArray(), pressure)
        assertEquals(55, pressure.currentPressure)
        assertEquals(1, pressure.unrestModifier)
    }

    @Test
    fun consumptionModifierTracksSupportingArmies() {
        val p = recalculateWarPressure(arrayOf(threat()), arrayOf(deployment(), deployment(ArmyDeploymentStatus.BATTLE)), null)
        assertEquals(2, p.consumptionModifier)
    }
}

class WarThreatTickTest {
    @Test
    fun etaCountsDownWithoutEscalating() {
        val t = tickWarThreat(threat(eta = 3, escalationLevel = 0), currentTurn = 1)
        assertEquals(2, t.eta)
        assertEquals(0, t.escalationLevel)
    }

    @Test
    fun escalatesOnceEtaReachesZero() {
        val t = tickWarThreat(threat(eta = 1, escalationLevel = 0), currentTurn = 1)
        assertEquals(0, t.eta)
        assertEquals(1, t.escalationLevel)
    }

    @Test
    fun unknownEtaEscalatesEachTurn() {
        val t = tickWarThreat(threat(eta = null, escalationLevel = 0, maxEscalation = 5), currentTurn = 1)
        assertEquals(1, t.escalationLevel)
        assertNull(t.eta)
    }

    @Test
    fun expiresWhenEscalationHitsMax() {
        val t = tickWarThreat(threat(eta = 0, escalationLevel = 2, maxEscalation = 3), currentTurn = 7)
        assertEquals(3, t.escalationLevel)
        assertEquals(WarThreatStatus.EXPIRED.value, t.status)
        assertEquals(7, t.triggeredTurn)
    }

    @Test
    fun softPauseKeepsThreatActiveButRecordsTriggerTurn() {
        val t = tickWarThreat(threat(eta = 0, escalationLevel = 2, maxEscalation = 3, pauseOnExpiry = true), currentTurn = 7)
        assertEquals(WarThreatStatus.ACTIVE.value, t.status)
        assertEquals(7, t.triggeredTurn)
        assertNotNull(t.triggeredTurn)
    }

    @Test
    fun nonActiveThreatIsUnchanged() {
        val original = threat(status = WarThreatStatus.DEFEATED, eta = 3)
        val t = tickWarThreat(original, currentTurn = 1)
        assertEquals(3, t.eta)
        assertEquals(WarThreatStatus.DEFEATED.value, t.status)
    }
}
