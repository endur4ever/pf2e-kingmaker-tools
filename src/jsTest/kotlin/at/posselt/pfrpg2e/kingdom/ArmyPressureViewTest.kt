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
        // Board mode left unset (null) -> armyPressureBoardModeOrDefault() = "basic" -> no forecast.
        // Guards the migration default: old kingdoms (field absent) must not compute a projection.
        assertNull(view.pressure!!.projection)
    }

    @Test
    fun resolvesGarrisonSettlementNameFromMap() {
        // Covers the garrison display path: garrisonedSettlementId is resolved to a name via
        // settlementNames, and an unknown id degrades gracefully to null (not a crash / stale id).
        val garrisoned = RawArmyDeployment(
            id = "d1", armyActorUuid = "Actor.x", armyName = "Home Guard", armyType = "infantry",
            assignedThreatId = null, garrisonedSettlementId = "s1", status = "deployed", deployedTurn = 0,
        )
        val unknownGarrison = RawArmyDeployment(
            id = "d2", armyActorUuid = "Actor.y", armyName = "Free Company", armyType = "infantry",
            assignedThreatId = null, garrisonedSettlementId = "missing", status = "deployed", deployedTurn = 0,
        )
        val view = buildArmyPressureView(
            null, arrayOf(garrisoned, unknownGarrison), null,
            settings(enabled = true, showDistance = false),
            settlementNames = mapOf("s1" to "Ironhaven"),
        )
        val resolved = view.deployments.first { it.garrisonedSettlementId == "s1" }
        assertEquals("Ironhaven", resolved.garrisonedSettlementName)
        val unresolved = view.deployments.first { it.garrisonedSettlementId == "missing" }
        assertNull(unresolved.garrisonedSettlementName)
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

    private fun threat(
        id: String = "w1",
        status: String = "active",
        triggeredTurn: Int? = null,
    ) = RawWarThreat(
        id = id, name = "Goblin Horde", description = "raiders", enemyFaction = null,
        escalationLevel = 1, maxEscalation = 4, eta = 2,
        targetSettlementSceneId = null, targetHexLocation = null,
        linkedQuestId = null, linkedEventId = null, pauseOnExpiry = false,
        status = status, triggeredTurn = triggeredTurn,
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

    @Test
    fun advancedModeIncludesProjection() {
        val threat = RawWarThreat(
            id = "w1", name = "Goblin Horde", description = "raiders", enemyFaction = null,
            escalationLevel = 1, maxEscalation = 4, eta = 2,
            targetSettlementSceneId = null, targetHexLocation = null,
            linkedQuestId = null, linkedEventId = null, pauseOnExpiry = false,
            status = "active", triggeredTurn = null,
        )
        val pressure = RawWarPressure(
            currentPressure = 30, pressurePerTurn = 5, unrestModifier = 0, consumptionModifier = 0,
            unrestThreshold = 50, ruinThreshold = 75, lastChange = 0,
        )
        val settingsObj = settings(enabled = true, showDistance = false)
        settingsObj.armyPressureBoardMode = "advanced"

        val view = buildArmyPressureView(
            arrayOf(threat),
            emptyArray(),
            pressure,
            settingsObj,
            currentTurn = 1,
        )

        assertNotNull(view.pressure)
        val proj = view.pressure!!.projection
        assertNotNull(proj)
        assertEquals(4, proj.turnsUntilUnrestThreshold) // (50-30)/5 = 4
        assertEquals(9, proj.turnsUntilRuinThreshold) // (75-30)/5 = 9
        assertEquals(1, proj.threatArrivals.size)
        assertEquals("w1", proj.threatArrivals[0].threatId)
        // Invasion (max escalation) trigger: eta=2 to the border + (maxEscalation 4 - escalationLevel 1)
        // escalations, overlapping the border tick by 1 = 2 + 3 - 1 = 4 turns (matches tickWarThreat).
        assertEquals(4, proj.threatArrivals[0].turnsUntilArrival)
    }

    @Test
    fun basicModeExcludesProjection() {
        val pressure = RawWarPressure(
            currentPressure = 30, pressurePerTurn = 5, unrestModifier = 0, consumptionModifier = 0,
            unrestThreshold = 50, ruinThreshold = 75, lastChange = 0,
        )
        val settingsObj = settings(enabled = true, showDistance = false)
        settingsObj.armyPressureBoardMode = "basic"

        val view = buildArmyPressureView(
            emptyArray(),
            emptyArray(),
            pressure,
            settingsObj,
            currentTurn = 1,
        )

        assertNotNull(view.pressure)
        assertNull(view.pressure!!.projection)
    }

    private fun visibilityThreat(id: String, visible: Boolean?) = RawWarThreat(
        id = id, name = "Threat $id", description = "d", enemyFaction = null,
        escalationLevel = 1, maxEscalation = 4, eta = 2,
        targetSettlementSceneId = null, targetHexLocation = "0101",
        linkedQuestId = null, linkedEventId = null, pauseOnExpiry = false,
        status = "active", triggeredTurn = null, visibleToPlayers = visible,
    )

    @Test
    fun playerViewExcludesHiddenThreatsEntirely() {
        val threats = arrayOf(
            visibilityThreat("visible", visible = true),
            visibilityThreat("hidden", visible = false),
            visibilityThreat("legacy", visible = null),  // migration-default: treated as visible
        )
        val playerView = buildArmyPressureView(
            threats, null, null, settings(enabled = true, showDistance = true), isGM = false,
        )
        val ids = playerView.threats.map { it.id }.toSet()
        // Zero hidden-threat data reaches a non-GM view — excluded at the source, not just in the UI.
        assertEquals(setOf("visible", "legacy"), ids)
        assertTrue(playerView.threats.none { it.hiddenFromPlayers })
    }

    @Test
    fun gmViewKeepsHiddenThreatsWithABadge() {
        val threats = arrayOf(
            visibilityThreat("visible", visible = true),
            visibilityThreat("hidden", visible = false),
        )
        val gmView = buildArmyPressureView(
            threats, null, null, settings(enabled = true, showDistance = true), isGM = true,
        )
        assertEquals(2, gmView.threats.size)
        assertTrue(gmView.threats.first { it.id == "hidden" }.hiddenFromPlayers)
        assertFalse(gmView.threats.first { it.id == "visible" }.hiddenFromPlayers)
    }

    @Test
    fun resolvedThreatsMoveToHistoryOnceTheyAreOldEnough() {
        val view = buildArmyPressureView(
            threats = arrayOf(
                threat(id = "fresh", status = "defeated", triggeredTurn = 9),
                threat(id = "old", status = "defeated", triggeredTurn = 2),
            ),
            deployments = emptyArray(),
            pressure = null,
            settings = settings(enabled = true, showDistance = true),
            currentTurn = 10,
        )
        // Resolved recently -> still on the board so the outcome is visible.
        assertEquals(listOf("fresh"), view.threats.map { it.id })
        assertEquals(listOf("old"), view.threatHistory.map { it.id })
    }

    @Test
    fun anActiveThreatNeverAgesOffTheBoard() {
        // An old war is still a war; only RESOLVED threats retire.
        val view = buildArmyPressureView(
            threats = arrayOf(threat(id = "ancient", status = "active", triggeredTurn = 1)),
            deployments = emptyArray(),
            pressure = null,
            settings = settings(enabled = true, showDistance = true),
            currentTurn = 40,
        )
        assertEquals(listOf("ancient"), view.threats.map { it.id })
        assertEquals(emptyList(), view.threatHistory.map { it.id })
    }

    @Test
    fun hiddenThreatsStayHiddenInHistoryToo() {
        // The fog-of-war exclusion must survive the partition -- history is built from the already
        // player-filtered list, not from the raw array.
        val hidden = RawWarThreat.copy(
            threat(id = "secret", status = "defeated", triggeredTurn = 1),
            visibleToPlayers = false,
        )
        val playerView = buildArmyPressureView(
            threats = arrayOf(hidden),
            deployments = emptyArray(),
            pressure = null,
            settings = settings(enabled = true, showDistance = true),
            currentTurn = 10,
            isGM = false,
        )
        assertEquals(emptyList(), playerView.threats.map { it.id })
        assertEquals(emptyList(), playerView.threatHistory.map { it.id })

        val gmView = buildArmyPressureView(
            threats = arrayOf(hidden),
            deployments = emptyArray(),
            pressure = null,
            settings = settings(enabled = true, showDistance = true),
            currentTurn = 10,
            isGM = true,
        )
        assertEquals(listOf("secret"), gmView.threatHistory.map { it.id })
    }
}
