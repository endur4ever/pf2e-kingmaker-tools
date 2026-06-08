package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.ArmyDeploymentStatus
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
        it.status == ArmyDeploymentStatus.DEPLOYED.value || it.status == ArmyDeploymentStatus.BATTLE.value
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

private fun RawWarThreat.copyWith(
    escalationLevel: Int = this.escalationLevel,
    eta: Int? = this.eta,
    status: String = this.status,
    triggeredTurn: Int? = this.triggeredTurn,
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
        )
    } else {
        threat.copyWith(escalationLevel = newEscalation, eta = newEta)
    }
}

fun tickWarThreats(threats: Array<RawWarThreat>, currentTurn: Int): Array<RawWarThreat> =
    threats.map { tickWarThreat(it, currentTurn) }.toTypedArray()
