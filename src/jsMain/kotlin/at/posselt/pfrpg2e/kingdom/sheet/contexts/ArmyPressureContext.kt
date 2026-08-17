package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.ArmyPressureView
import at.posselt.pfrpg2e.kingdom.ThreatArrival
import at.posselt.pfrpg2e.kingdom.data.ArmyDeploymentStatus
import at.posselt.pfrpg2e.kingdom.data.WarThreatStatus
import at.posselt.pfrpg2e.utils.t
import kotlinx.js.JsPlainObject
import at.posselt.pfrpg2e.kingdom.WarThreatView

/**
 * Handlebars context for the Army & War Pressure sheet section (roadmap #12),
 * built from the pure [ArmyPressureView] (which the tests cover). JS-friendly
 * external interfaces so the template can read the fields directly.
 */

@JsPlainObject
external interface ArmyThreatContext {
    val id: String
    val name: String
    val description: String
    val enemyFaction: String?
    val escalationLevel: Int
    val maxEscalation: Int
    val escalationPercent: Int
    val eta: Int?
    val status: String
    val statusLabel: String
    val targetHexLocation: String?
    val pauseOnExpiry: Boolean
    val canResolveBattle: Boolean
    val hiddenFromPlayers: Boolean
}

@JsPlainObject
external interface ArmyDeploymentContext {
    val id: String
    val armyName: String
    val armyType: String
    val status: String
    val statusLabel: String
    val assignedThreatId: String?
    val garrisonedSettlementId: String?
    val garrisonedSettlementName: String?
}

@JsPlainObject
external interface ThreatArrivalContext {
    val threatId: String
    val threatName: String
    val turnsUntilArrival: Int?
}

@JsPlainObject
external interface WarPressureProjectionContext {
    val turnsUntilUnrestThreshold: Int?
    val turnsUntilRuinThreshold: Int?
    val threatArrivals: Array<ThreatArrivalContext>
}

@JsPlainObject
external interface ArmyPressureMeterContext {
    val currentPressure: Int
    val pressurePercent: Int
    val pressurePerTurn: Int
    val unrestThreshold: Int
    val ruinThreshold: Int
    val atUnrestThreshold: Boolean
    val atRuinThreshold: Boolean
    val unrestModifier: Int
    val consumptionModifier: Int
    /** Projection forecast (advanced mode only). */
    val projection: WarPressureProjectionContext?
}

@JsPlainObject
external interface ArmyPressureContext {
    val enabled: Boolean
    val showThreatDistance: Boolean
    val hasThreats: Boolean
    val hasDeployments: Boolean
    val threats: Array<ArmyThreatContext>

    /** Resolved threats retired to the collapsed history list. */
    val threatHistory: Array<ArmyThreatContext>
    val deployments: Array<ArmyDeploymentContext>
    val pressure: ArmyPressureMeterContext?
}

/** One mapper for both the live board and the history list, so they cannot drift apart. */
private fun WarThreatView.toContext() = ArmyThreatContext(
    id = id,
    name = name,
    description = description,
    enemyFaction = enemyFaction,
    escalationLevel = escalationLevel,
    maxEscalation = maxEscalation,
    escalationPercent = escalationPercent,
    eta = eta,
    status = status,
    statusLabel = WarThreatStatus.fromString(status)?.let { t(it.i18nKey) } ?: status,
    targetHexLocation = targetHexLocation,
    pauseOnExpiry = pauseOnExpiry,
    canResolveBattle = canResolveBattle,
    hiddenFromPlayers = hiddenFromPlayers,
)

fun buildArmyPressureContext(view: ArmyPressureView): ArmyPressureContext {
    val threats = view.threats.map { it.toContext() }.toTypedArray()
    val deployments = view.deployments.map { dv ->
        ArmyDeploymentContext(
            id = dv.id,
            armyName = dv.armyName,
            armyType = dv.armyType,
            status = dv.status,
            statusLabel = ArmyDeploymentStatus.fromString(dv.status)?.let { t(it.i18nKey) } ?: dv.status,
            assignedThreatId = dv.assignedThreatId,
            garrisonedSettlementId = dv.garrisonedSettlementId,
            garrisonedSettlementName = dv.garrisonedSettlementName,
        )
    }.toTypedArray()
    val pressure = view.pressure?.let { pv ->
        ArmyPressureMeterContext(
            currentPressure = pv.currentPressure,
            pressurePercent = pv.pressurePercent,
            pressurePerTurn = pv.pressurePerTurn,
            unrestThreshold = pv.unrestThreshold,
            ruinThreshold = pv.ruinThreshold,
            atUnrestThreshold = pv.atUnrestThreshold,
            atRuinThreshold = pv.atRuinThreshold,
            unrestModifier = pv.unrestModifier,
            consumptionModifier = pv.consumptionModifier,
            projection = pv.projection?.let { proj ->
                WarPressureProjectionContext(
                    turnsUntilUnrestThreshold = proj.turnsUntilUnrestThreshold,
                    turnsUntilRuinThreshold = proj.turnsUntilRuinThreshold,
                    threatArrivals = proj.threatArrivals.map { ta ->
                        ThreatArrivalContext(
                            threatId = ta.threatId,
                            threatName = ta.threatName,
                            turnsUntilArrival = ta.turnsUntilArrival,
                        )
                    }.toTypedArray(),
                )
            },
        )
    }
    return ArmyPressureContext(
        enabled = view.enabled,
        showThreatDistance = view.showThreatDistance,
        hasThreats = threats.isNotEmpty(),
        hasDeployments = deployments.isNotEmpty(),
        threats = threats,
        // Previously omitted, which left the template's "Past Threats" block reading an undefined
        // length and never rendering at all.
        threatHistory = view.threatHistory.map { it.toContext() }.toTypedArray(),
        deployments = deployments,
        pressure = pressure,
    )
}