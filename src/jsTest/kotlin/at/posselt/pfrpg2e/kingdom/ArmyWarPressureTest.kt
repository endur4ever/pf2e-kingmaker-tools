package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.armies.ArmyCondition
import at.posselt.pfrpg2e.data.armies.BattleStatus
import at.posselt.pfrpg2e.kingdom.data.ArmyDeploymentStatus
import at.posselt.pfrpg2e.kingdom.data.RawArmyBattle
import at.posselt.pfrpg2e.kingdom.data.RawArmyDeployment
import at.posselt.pfrpg2e.kingdom.data.RawBattleArmy
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import at.posselt.pfrpg2e.kingdom.data.WarThreatStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

private fun deployment(
    id: String = "d1",
    armyActorUuid: String = "Actor.x",
    armyName: String = "1st Legion",
    armyType: String = "infantry",
    assignedThreatId: String? = null,
    garrisonedSettlementId: String? = null,
    status: ArmyDeploymentStatus = ArmyDeploymentStatus.DEPLOYED,
    deployedTurn: Int = 0,
): RawArmyDeployment = RawArmyDeployment(
    id = id, armyActorUuid = armyActorUuid, armyName = armyName, armyType = armyType,
    assignedThreatId = assignedThreatId, garrisonedSettlementId = garrisonedSettlementId,
    status = status.value, deployedTurn = deployedTurn,
)

private fun battleArmy(
    uuid: String = "Actor.x",
    name: String = "1st Legion",
    conditions: Array<String> = emptyArray(),
): RawBattleArmy = RawBattleArmy(
    armyActorUuid = uuid,
    name = name,
    level = 4,
    currentHp = 20,
    maxHp = 20,
    conditions = conditions,
    xp = 0,
)

private fun rawArmyBattle(
    id: String = "battle-1",
    threatId: String = "w1",
    name: String = "Skirmish",
    round: Int = 1,
    terrain: String? = "forest",
    attackers: Array<RawBattleArmy> = arrayOf(battleArmy()),
    defenders: Array<RawBattleArmy> = arrayOf(battleArmy("Enemy", "Goblin Scouts")),
    log: Array<String> = arrayOf("Battle begins."),
    status: String = BattleStatus.ACTIVE.value,
): RawArmyBattle = RawArmyBattle(
    id = id,
    threatId = threatId,
    name = name,
    round = round,
    terrain = terrain,
    attackers = attackers,
    defenders = defenders,
    log = log,
    status = status,
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
        val p = recalculateWarPressure(arrayOf(threat()), arrayOf(deployment(), deployment(status = ArmyDeploymentStatus.BATTLE)), null)
        assertEquals(2, p.consumptionModifier)
    }

    @Test
    fun destroyedAndRetreatedArmiesDoNotReducePressure() {
        // 1 threat = +5/turn, 4 armies but only 2 are supporting (DEPLOYED + BATTLE)
        val deploys = arrayOf(
            deployment(),                                    // DEPLOYED
            deployment(status = ArmyDeploymentStatus.BATTLE),         // BATTLE
            deployment(status = ArmyDeploymentStatus.DESTROYED),      // DESTROYED - should NOT count
            deployment(status = ArmyDeploymentStatus.RETREATED),      // RETREATED - should NOT count
        )
        val p = recalculateWarPressure(arrayOf(threat()), deploys, null)
        // pressurePerTurn = 1*5 - 2*2 = +1
        assertEquals(1, p.pressurePerTurn)
        assertEquals(2, p.consumptionModifier)
    }

    @Test
    fun transitionDeploymentToBattleOnlyAffectsAssignedDeployed() {
        val deploys = arrayOf(
            deployment(id = "d1", assignedThreatId = "w1", status = ArmyDeploymentStatus.DEPLOYED),
            deployment(id = "d2", assignedThreatId = "w1", status = ArmyDeploymentStatus.BATTLE),
            deployment(id = "d3", assignedThreatId = "w2", status = ArmyDeploymentStatus.DEPLOYED),
        )
        val updated = transitionDeploymentToBattle(deploys, "w1")
        assertEquals(ArmyDeploymentStatus.BATTLE.value, updated[0].status)
        assertEquals(ArmyDeploymentStatus.BATTLE.value, updated[1].status)
        assertEquals(ArmyDeploymentStatus.DEPLOYED.value, updated[2].status)
    }

    @Test
    fun garrisonedArmyDestroyedInBattleIsMarkedDestroyed() {
        // A garrisoned army joins the battle for its own settlement without ever being assigned
        // to the threat. The lifecycle has to carry it, or it dies in the fight and the board still
        // shows it Deployed -- wrong status, wrong cleanup button, and it would relieve pressure
        // again the moment its garrison duty is cleared.
        val garrisoned = deployment(
            id = "d1",
            armyActorUuid = "Actor.garrison",
            assignedThreatId = null,
            garrisonedSettlementId = "Scene.capital",
            status = ArmyDeploymentStatus.DEPLOYED,
        )
        val elsewhere = deployment(
            id = "d2",
            armyActorUuid = "Actor.other",
            assignedThreatId = null,
            garrisonedSettlementId = "Scene.otherTown",
            status = ArmyDeploymentStatus.DEPLOYED,
        )

        val inBattle = transitionDeploymentToBattle(
            arrayOf(garrisoned, elsewhere),
            "w1",
            targetSettlementSceneId = "Scene.capital",
        )
        assertEquals(ArmyDeploymentStatus.BATTLE.value, inBattle[0].status)
        // A garrison in a different settlement is not in this battle.
        assertEquals(ArmyDeploymentStatus.DEPLOYED.value, inBattle[1].status)

        val afterBattle = updateDeploymentStatusesAfterBattle(
            inBattle,
            rawArmyBattle(
                attackers = arrayOf(
                    battleArmy(
                        uuid = "Actor.garrison",
                        conditions = arrayOf(ArmyCondition.DESTROYED.value),
                    ),
                ),
            ),
        )
        assertEquals(ArmyDeploymentStatus.DESTROYED.value, afterBattle[0].status)
    }

    @Test
    fun copyWithPreservesFieldsItDoesNotName() {
        // copyWith used to rebuild the threat field by field, dropping everything unlisted. The
        // visible symptom: consuming an offer un-hid a threat the GM had hidden from players.
        val hidden = RawWarThreat.copy(threat(), visibleToPlayers = false)

        val consumed = hidden.copyWith(offerConsumed = true)

        assertEquals(false, consumed.visibleToPlayers)
        assertEquals(true, consumed.offerConsumed)
        assertEquals("t1", consumed.id)
    }

    @Test
    fun transitionDeploymentToBattleIgnoresGarrisonsWhenThreatTargetsNoSettlement() {
        val deploys = arrayOf(
            deployment(id = "d1", assignedThreatId = null, garrisonedSettlementId = "Scene.capital"),
        )
        // targetSettlementSceneId omitted: a field threat pulls in no garrisons.
        val updated = transitionDeploymentToBattle(deploys, "w1")
        assertEquals(ArmyDeploymentStatus.DEPLOYED.value, updated[0].status)
    }

    @Test
    fun updateDeploymentStatusesAfterBattleDestroyedBecomesDestroyed() {
        val deploys = arrayOf(
            deployment(id = "d1", assignedThreatId = "w1", status = ArmyDeploymentStatus.BATTLE),
        )
        val battle = rawArmyBattle(
            attackers = arrayOf(
                battleArmy("Actor.x", "1st Legion", arrayOf("destroyed")),
            ),
        )

        val updated = updateDeploymentStatusesAfterBattle(deploys, battle)

        assertEquals(ArmyDeploymentStatus.DESTROYED.value, updated[0].status)
    }

    @Test
    fun updateDeploymentStatusesAfterBattleRoutedBecomesRetreated() {
        val deploys = arrayOf(
            deployment(id = "d1", assignedThreatId = "w1", status = ArmyDeploymentStatus.BATTLE),
        )
        val battle = rawArmyBattle(
            attackers = arrayOf(
                battleArmy("Actor.x", "1st Legion", arrayOf("routed")),
            ),
        )

        val updated = updateDeploymentStatusesAfterBattle(deploys, battle)

        assertEquals(ArmyDeploymentStatus.RETREATED.value, updated[0].status)
    }

    @Test
    fun updateDeploymentStatusesAfterBattleSurvivingReturnsToDeployed() {
        val deploys = arrayOf(
            deployment(id = "d1", assignedThreatId = "w1", status = ArmyDeploymentStatus.BATTLE),
        )
        val battle = rawArmyBattle(
            attackers = arrayOf(
                battleArmy("Actor.x", "1st Legion", emptyArray()),
            ),
        )

        val updated = updateDeploymentStatusesAfterBattle(deploys, battle)

        assertEquals(ArmyDeploymentStatus.DEPLOYED.value, updated[0].status)
    }

    @Test
    fun updateDeploymentStatusesAfterBattleIgnoresNonBattleDeployments() {
        val deploys = arrayOf(
            deployment(id = "d1", assignedThreatId = "w1", status = ArmyDeploymentStatus.DEPLOYED),
            deployment(id = "d2", assignedThreatId = "w1", status = ArmyDeploymentStatus.BATTLE),
        )
        val battle = rawArmyBattle(
            attackers = arrayOf(
                battleArmy("Actor.x", "1st Legion"),
                battleArmy("Actor.y", "2nd Legion"),
            ),
        )

        val updated = updateDeploymentStatusesAfterBattle(deploys, battle)

        assertEquals(ArmyDeploymentStatus.DEPLOYED.value, updated[0].status)
        assertEquals(ArmyDeploymentStatus.DEPLOYED.value, updated[1].status)
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

    @Test
    fun `a garrisoned army does not also relieve war pressure`() {
        // Garrisoning already buys settlement defence and siege mitigation. Letting it ALSO relieve
        // pressure would make garrisoning strictly better than deploying for the same army.
        val free = recalculateWarPressure(arrayOf(threat()), arrayOf(deployment()), null)
        val garrisoned = recalculateWarPressure(
            arrayOf(threat()),
            arrayOf(deployment(garrisonedSettlementId = "scene-1")),
            null,
        )
        assertTrue(
            garrisoned.pressurePerTurn > free.pressurePerTurn,
            "a garrisoned army must not reduce pressure the way a deployed one does",
        )
    }

    @Test
    fun `mixed deployments only count the un-garrisoned armies`() {
        val mixed = recalculateWarPressure(
            arrayOf(threat()),
            arrayOf(
                deployment(id = "d1"),
                deployment(id = "d2", garrisonedSettlementId = "scene-1"),
                deployment(id = "d3", garrisonedSettlementId = "scene-2"),
            ),
            null,
        )
        val oneFree = recalculateWarPressure(arrayOf(threat()), arrayOf(deployment(id = "d1")), null)
        assertEquals(oneFree.pressurePerTurn, mixed.pressurePerTurn)
    }
}
