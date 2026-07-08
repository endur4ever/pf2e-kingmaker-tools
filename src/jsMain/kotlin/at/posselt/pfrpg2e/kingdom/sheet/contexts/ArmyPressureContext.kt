package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.ArmyPressureView
import at.posselt.pfrpg2e.kingdom.ThreatArrival
import at.posselt.pfrpg2e.kingdom.data.ArmyDeploymentStatus
import at.posselt.pfrpg2e.kingdom.data.WarThreatStatus
import at.posselt.pfrpg2e.utils.t
import kotlinx.js.JsPlainObject

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
}

@JsPlainObject
external interface ArmyDeploymentContext {
    val id: String
    val armyName: String
    val armyType: String
    val status: String
    val statusLabel: String
    val assignedThreatId: String?
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
    val deployments: Array<ArmyDeploymentContext>
    val pressure: ArmyPressureMeterContext?
}

fun buildArmyPressureContext(view: ArmyPressureView): ArmyPressureContext {
    val threats = view.threats.map { tv ->
        ArmyThreatContext(
            id = tv.id,
            name = tv.name,
            description = tv.description,
            enemyFaction = tv.enemyFaction,
            escalationLevel = tv.escalationLevel,
            maxEscalation = tv.maxEscalation,
            escalationPercent = tv.escalationPercent,
            eta = tv.eta,
            status = tv.status,
            statusLabel = WarThreatStatus.fromString(tv.status)?.let { t(it.i18nKey) } ?: tv.status,
            targetHexLocation = tv.targetHexLocation,
            pauseOnExpiry = tv.pauseOnExpiry,
            canResolveBattle = tv.canResolveBattle,
        )
    }.toTypedArray()
    val deployments = view.deployments.map { dv ->
        ArmyDeploymentContext(
            id = dv.id,
            armyName = dv.armyName,
            armyType = dv.armyType,
            status = dv.status,
            statusLabel = ArmyDeploymentStatus.fromString(dv.status)?.let { t(it.i18nKey) } ?: dv.status,
            assignedThreatId = dv.assignedThreatId,
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
        deployments = deployments,
        pressure = pressure,
    )
}