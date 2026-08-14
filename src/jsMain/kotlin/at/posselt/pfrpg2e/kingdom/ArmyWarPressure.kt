package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.armies.ArmyCondition
import at.posselt.pfrpg2e.kingdom.data.ArmyDeploymentStatus
import at.posselt.pfrpg2e.kingdom.data.RawArmyBattle
import at.posselt.pfrpg2e.kingdom.data.RawArmyDeployment
import at.posselt.pfrpg2e.kingdom.data.RawWarPressure
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import at.posselt.pfrpg2e.kingdom.data.WarThreatStatus

/**
 * Pure war-pressure + threat-clock logic for the Army & War Pressure board
 * (roadmap #12). Operates on the JS-interop Raw types so it can be unit-tested
 * and reused by [TurnTickingEngine]. No Foundry dependencies.
 */

const val PRESSURE_PER_ACTIVE_THREAT = 5
const val PRESSURE_REDUCTION_PER_DEPLOYED_ARMY = 2
const val DEFAULT_UNREST_THRESHOLD = 50
const val DEFAULT_RUIN_THRESHOLD = 75

fun defaultWarPressure(): RawWarPressure = RawWarPressure(
    currentPressure = 0,
    pressurePerTurn = 0,
    unrestModifier = 0,
    consumptionModifier = 0,
    unrestThreshold = DEFAULT_UNREST_THRESHOLD,
    ruinThreshold = DEFAULT_RUIN_THRESHOLD,
    lastChange = null,
)

// ── KingdomSettings defensive accessors (nullable fields, back-compat) ──

fun KingdomSettings.isArmyPressureBoardEnabled(): Boolean = enableArmyPressureBoard == true
fun KingdomSettings.shouldAutoCalculateWarPressure(): Boolean = autoCalculateWarPressure != false
fun KingdomSettings.shouldShowThreatDistance(): Boolean = showThreatDistance != false
fun KingdomSettings.armyPressureBoardModeOrDefault(): String = armyPressureBoardMode ?: "basic"

// ── war pressure ──

private fun activeThreatCount(threats: Array<RawWarThreat>): Int =
    threats.count { it.status == WarThreatStatus.ACTIVE.value }

private fun supportingArmyCount(deployments: Array<RawArmyDeployment>): Int =
    deployments.count {
        val status = it.status
        // Only DEPLOYED and BATTLE armies count toward pressure reduction.
        // DESTROYED and RETREATED armies no longer contribute (they are effectively removed from the field).
        val onTheField = status == ArmyDeploymentStatus.DEPLOYED.value ||
            status == ArmyDeploymentStatus.BATTLE.value
        // A garrisoned army is holding one settlement, not projecting force across the realm, so it
        // does not also relieve war pressure — otherwise garrisoning would be strictly better than
        // deploying, granting the settlement defence AND the pressure relief for the same army.
        onTheField && it.garrisonedSettlementId == null
    }

/**
 * Recalculate the global war-pressure track. Pressure rises by 5 per active
 * threat and falls by 2 per supporting army each turn, accumulating into a
 * clamped 0-100 [RawWarPressure.currentPressure]. Threshold crossings set the
 * unrest/consumption modifiers the [TurnTickingEngine] applies.
 */
fun recalculateWarPressure(
    threats: Array<RawWarThreat>,
    deployments: Array<RawArmyDeployment>,
    current: RawWarPressure?,
): RawWarPressure {
    val base = current ?: defaultWarPressure()
    val threatCount = activeThreatCount(threats)
    val armyCount = supportingArmyCount(deployments)
    val perTurn = threatCount * PRESSURE_PER_ACTIVE_THREAT - armyCount * PRESSURE_REDUCTION_PER_DEPLOYED_ARMY
    val newPressure = (base.currentPressure + perTurn).coerceIn(0, 100)
    return RawWarPressure(
        currentPressure = newPressure,
        pressurePerTurn = perTurn,
        unrestModifier = if (newPressure >= base.unrestThreshold) 1 else 0,
        consumptionModifier = armyCount,
        unrestThreshold = base.unrestThreshold,
        ruinThreshold = base.ruinThreshold,
        lastChange = newPressure - base.currentPressure,
    )
}

// ── threat clock ──

fun RawWarThreat.copyWith(
    escalationLevel: Int = this.escalationLevel,
    eta: Int? = this.eta,
    status: String = this.status,
    triggeredTurn: Int? = this.triggeredTurn,
    offerConsumed: Boolean? = this.offerConsumed,
): RawWarThreat = RawWarThreat(
    id = id,
    name = name,
    description = description,
    enemyFaction = enemyFaction,
    escalationLevel = escalationLevel,
    maxEscalation = maxEscalation,
    eta = eta,
    targetSettlementSceneId = targetSettlementSceneId,
    targetHexLocation = targetHexLocation,
    linkedQuestId = linkedQuestId,
    linkedEventId = linkedEventId,
    pauseOnExpiry = pauseOnExpiry,
    status = status,
    triggeredTurn = triggeredTurn,
    offerConsumed = offerConsumed,
)

/**
 * Advance one threat by a turn: count the ETA down, escalate once it reaches
 * zero (or when ETA is unknown), and trigger expiry when escalation hits max —
 * unless [RawWarThreat.pauseOnExpiry] is set (Decision 3 soft-pause), in which
 * case the threat stays active with [RawWarThreat.triggeredTurn] recorded for
 * the GM to resolve. Non-active threats are returned unchanged.
 */
fun tickWarThreat(threat: RawWarThreat, currentTurn: Int): RawWarThreat {
    if (threat.status != WarThreatStatus.ACTIVE.value) return threat

    val eta = threat.eta
    val newEta = if (eta != null && eta > 0) eta - 1 else eta
    // Escalate once the ETA has reached zero (or when it is unknown).
    val arriving = newEta == null || newEta <= 0
    val newEscalation = if (arriving) {
        (threat.escalationLevel + 1).coerceAtMost(threat.maxEscalation)
    } else {
        threat.escalationLevel
    }

    val hitMax = newEscalation >= threat.maxEscalation
    return if (hitMax && threat.triggeredTurn == null) {
        threat.copyWith(
            escalationLevel = newEscalation,
            eta = newEta,
            status = if (threat.pauseOnExpiry) WarThreatStatus.ACTIVE.value else WarThreatStatus.EXPIRED.value,
            triggeredTurn = currentTurn,
            offerConsumed = false,
        )
    } else {
        threat.copyWith(escalationLevel = newEscalation, eta = newEta)
    }
}

/**
 * Updates army deployment statuses based on the outcome of a resolved battle.
 * Call this after a battle reaches VICTORY or DEFEAT to transition deployments:
 * - Armies that were in BATTLE and survive (not DESTROYED/ROUTED) -> back to DEPLOYED
 * - Armies that were in BATTLE and are ROUTED -> RETREATED
 * - Armies that were in BATTLE and are DESTROYED -> DESTROYED
 *
 * This operates on the raw deployment array and the battle's final engine state
 * to determine per-army outcomes. Returns the updated deployments array.
 */
fun updateDeploymentStatusesAfterBattle(
    deployments: Array<RawArmyDeployment>,
    battle: RawArmyBattle,
): Array<RawArmyDeployment> {
    // Build a lookup from armyActorUuid to deployment for attackers (kingdom armies)
    val deploymentByUuid = deployments.associateBy { it.armyActorUuid }
    val attackerCount = battle.attackers.size

    return deployments.map { deployment ->
        val uuid = deployment.armyActorUuid
        // Only transition deployments that are currently in BATTLE status
        if (deployment.status != ArmyDeploymentStatus.BATTLE.value) return@map deployment

        // Find this army in the battle's final state (attackers only, since deployments are kingdom armies)
        val battleArmyIndex = battle.attackers.indexOfFirst { it.armyActorUuid == uuid }
        if (battleArmyIndex < 0) return@map deployment // Not found in battle, keep as-is

        val finalConditions = battle.attackers[battleArmyIndex].conditions
        val isDestroyed = ArmyCondition.DESTROYED.value in finalConditions
        val isRouted = ArmyCondition.ROUTED.value in finalConditions

        val newStatus = when {
            isDestroyed -> ArmyDeploymentStatus.DESTROYED.value
            isRouted -> ArmyDeploymentStatus.RETREATED.value
            else -> ArmyDeploymentStatus.DEPLOYED.value
        }
        RawArmyDeployment.copy(deployment, status = newStatus)
    }.toTypedArray()
}

/**
 * Transitions a deployment to BATTLE status when a battle is created for its assigned threat.
 * Call this from the battle-creation flow.
 */
fun transitionDeploymentToBattle(deployments: Array<RawArmyDeployment>, threatId: String): Array<RawArmyDeployment> =
    deployments.map { deployment ->
        if (deployment.assignedThreatId == threatId && deployment.status == ArmyDeploymentStatus.DEPLOYED.value) {
            RawArmyDeployment.copy(deployment, status = ArmyDeploymentStatus.BATTLE.value)
        } else {
            deployment
        }
    }.toTypedArray()

fun tickWarThreats(threats: Array<RawWarThreat>, currentTurn: Int): Array<RawWarThreat> =
    threats.map { tickWarThreat(it, currentTurn) }.toTypedArray()
