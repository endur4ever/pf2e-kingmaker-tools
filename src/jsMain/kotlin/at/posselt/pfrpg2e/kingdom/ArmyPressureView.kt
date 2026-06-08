package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawArmyDeployment
import at.posselt.pfrpg2e.kingdom.data.RawWarPressure
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat

/**
 * Display view-models for the Army & War Pressure board (roadmap #12).
 *
 * [buildArmyPressureView] parses the persisted Raw war data into clean,
 * template-ready models with computed fields (escalation %, pressure %,
 * threshold flags). Pure + testable; the sheet section template (phase 4 UI)
 * consumes the result.
 */

data class WarThreatView(
    val id: String,
    val name: String,
    val description: String,
    val enemyFaction: String?,
    val escalationLevel: Int,
    val maxEscalation: Int,
    val escalationPercent: Int,
    val eta: Int?,
    val status: String,
    val targetHexLocation: String?,
    val pauseOnExpiry: Boolean,
)

data class ArmyDeploymentView(
    val id: String,
    val armyName: String,
    val armyType: String,
    val status: String,
    val assignedThreatId: String?,
    val garrisonedSettlementId: String?,
)

data class WarPressureView(
    val currentPressure: Int,
    val pressurePercent: Int,
    val pressurePerTurn: Int,
    val unrestThreshold: Int,
    val ruinThreshold: Int,
    val atUnrestThreshold: Boolean,
    val atRuinThreshold: Boolean,
    val unrestModifier: Int,
    val consumptionModifier: Int,
)

data class ArmyPressureView(
    val enabled: Boolean,
    val showThreatDistance: Boolean,
    val threats: List<WarThreatView>,
    val deployments: List<ArmyDeploymentView>,
    val pressure: WarPressureView?,
)

private fun RawWarThreat.toView(): WarThreatView {
    val max = if (maxEscalation > 0) maxEscalation else 1
    return WarThreatView(
        id = id,
        name = name,
        description = description,
        enemyFaction = enemyFaction,
        escalationLevel = escalationLevel,
        maxEscalation = maxEscalation,
        escalationPercent = (escalationLevel * 100 / max).coerceIn(0, 100),
        eta = eta,
        status = status,
        targetHexLocation = targetHexLocation,
        pauseOnExpiry = pauseOnExpiry,
    )
}

private fun RawArmyDeployment.toView(): ArmyDeploymentView = ArmyDeploymentView(
    id = id,
    armyName = armyName,
    armyType = armyType,
    status = status,
    assignedThreatId = assignedThreatId,
    garrisonedSettlementId = garrisonedSettlementId,
)

private fun RawWarPressure.toView(): WarPressureView = WarPressureView(
    currentPressure = currentPressure,
    pressurePercent = currentPressure.coerceIn(0, 100),
    pressurePerTurn = pressurePerTurn,
    unrestThreshold = unrestThreshold,
    ruinThreshold = ruinThreshold,
    atUnrestThreshold = currentPressure >= unrestThreshold,
    atRuinThreshold = currentPressure >= ruinThreshold,
    unrestModifier = unrestModifier,
    consumptionModifier = consumptionModifier,
)

fun buildArmyPressureView(
    threats: Array<RawWarThreat>?,
    deployments: Array<RawArmyDeployment>?,
    pressure: RawWarPressure?,
    settings: KingdomSettings,
): ArmyPressureView = ArmyPressureView(
    enabled = settings.isArmyPressureBoardEnabled(),
    showThreatDistance = settings.shouldShowThreatDistance(),
    threats = (threats ?: emptyArray()).map { it.toView() },
    deployments = (deployments ?: emptyArray()).map { it.toView() },
    pressure = pressure?.toView(),
)
