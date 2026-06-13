package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawArmyDeployment
import at.posselt.pfrpg2e.kingdom.data.RawWarPressure
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArmyPressureViewTest {
    private fun settings(enabled: Boolean, showDistance: Boolean): KingdomSettings {
        val o = js("({})").unsafeCast<KingdomSettings>()
        o.enableArmyPressureBoard = enabled
        o.showThreatDistance = showDistance
        return o
    }

    @Test
    fun parsesThreatsDeploymentsAndPressure() {
        val threat = RawWarThreat(
            id = "w1", name = "Goblin Horde", description = "raiders", enemyFaction = "Goblins",
            escalationLevel = 1, maxEscalation = 4, eta = 2,
            targetSettlementSceneId = null, targetHexLocation = "0203",
            linkedQuestId = null, linkedEventId = null, pauseOnExpiry = false,
            status = "active", triggeredTurn = null,
        )
        val dep = RawArmyDeployment(
            id = "d1", armyActorUuid = "Actor.x", armyName = "1st Legion", armyType = "infantry",
            assignedThreatId = "w1", garrisonedSettlementId = null, status = "deployed", deployedTurn = 0,
        )
        val pressure = RawWarPressure(
            currentPressure = 60, pressurePerTurn = 5, unrestModifier = 1, consumptionModifier = 2,
            unrestThreshold = 50, ruinThreshold = 75, lastChange = 5,
        )
        val view = buildArmyPressureView(arrayOf(threat), arrayOf(dep), pressure, settings(enabled = true, showDistance = false))

        assertTrue(view.enabled)
        assertFalse(view.showThreatDistance)
        assertEquals(1, view.threats.size)
        assertEquals(25, view.threats[0].escalationPercent)   // 1 of 4
        assertEquals("0203", view.threats[0].targetHexLocation)
        assertEquals(1, view.deployments.size)
        assertEquals("1st Legion", view.deployments[0].armyName)
        assertNotNull(view.pressure)
        assertEquals(60, view.pressure!!.currentPressure)
        assertTrue(view.pressure!!.atUnrestThreshold)
        assertFalse(view.pressure!!.atRuinThreshold)
    }

    @Test
    fun emptyWhenNoWarData() {
        val view = buildArmyPressureView(null, null, null, settings(enabled = false, showDistance = true))
        assertFalse(view.enabled)
        assertTrue(view.threats.isEmpty())
        assertTrue(view.deployments.isEmpty())
        assertNull(view.pressure)
    }

    // ── Resolve Battle availability (roadmap #12, phase 4) ──────────────

    private fun threat(id: String = "w1", status: String = "active") = RawWarThreat(
        id = id, name = "Goblin Horde", description = "raiders", enemyFaction = null,
        escalationLevel = 1, maxEscalation = 4, eta = 2,
        targetSettlementSceneId = null, targetHexLocation = null,
        linkedQuestId = null, linkedEventId = null, pauseOnExpiry = false,
        status = status, triggeredTurn = null,
    )

    private fun deployment(assignedThreatId: String?) = RawArmyDeployment(
        id = "d1", armyActorUuid = "Actor.x", armyName = "1st Legion", armyType = "infantry",
        assignedThreatId = assignedThreatId, garrisonedSettlementId = null,
        status = "deployed", deployedTurn = 0,
    )

    @Test
    fun activeThreatWithAssignedArmiesCanResolveBattle() {
        val view = buildArmyPressureView(
            arrayOf(threat()),
            arrayOf(deployment(assignedThreatId = "w1")),
            null,
            settings(enabled = true, showDistance = false),
        )
        assertTrue(view.threats[0].hasAssignedArmies)
        assertTrue(view.threats[0].canResolveBattle)
    }

    @Test
    fun threatWithoutAssignedArmiesCannotResolveBattle() {
        val view = buildArmyPressureView(
            arrayOf(threat()),
            arrayOf(deployment(assignedThreatId = null)),
            null,
            settings(enabled = true, showDistance = false),
        )
        assertFalse(view.threats[0].hasAssignedArmies)
        assertFalse(view.threats[0].canResolveBattle)
    }

    @Test
    fun nonActiveThreatCannotResolveBattleEvenWithArmies() {
        val view = buildArmyPressureView(
            arrayOf(threat(status = "defeated")),
            arrayOf(deployment(assignedThreatId = "w1")),
            null,
            settings(enabled = true, showDistance = false),
        )
        assertTrue(view.threats[0].hasAssignedArmies)
        assertFalse(view.threats[0].canResolveBattle)
    }
}
