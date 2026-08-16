package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawArmyDeployment
import at.posselt.pfrpg2e.kingdom.data.RawWarPressure
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import at.posselt.pfrpg2e.kingdom.data.WarThreatStatus

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
    val hasAssignedArmies: Boolean = false,
    val canResolveBattle: Boolean = false,
    /** GM-only badge: this threat is hidden from players (never true in a player-facing view). */
    val hiddenFromPlayers: Boolean = false,
)

data class ArmyDeploymentView(
    val id: String,
    val armyName: String,
    val armyType: String,
    val status: String,
    val assignedThreatId: String?,
    val garrisonedSettlementId: String?,
    val garrisonedSettlementName: String?,
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
    /** Projection forecast (advanced mode only). Null in basic mode or when pressurePerTurn <= 0. */
    val projection: WarPressureProjection?,
)

data class ArmyPressureView(
    val enabled: Boolean,
    val showThreatDistance: Boolean,
    val threats: List<WarThreatView>,
    /**
     * Resolved threats old enough to have left the live board, shown in a collapsed history list.
     *
     * This is a VIEW partition, not a delete: a defeated threat is still referenced by the gazette
     * and by any battle that named it, and silently destroying campaign history to tidy a table is
     * a trade nobody asked for. Capped so a long campaign's board does not grow without bound.
     */
    val threatHistory: List<WarThreatView>,
    val deployments: List<ArmyDeploymentView>,
    val pressure: WarPressureView?,
)

/** How many turns a resolved threat stays on the live board before moving to history. */
const val WAR_THREAT_HISTORY_AFTER_TURNS = 3

/** Most historical threats kept in the view, newest first. */
const val MAX_WAR_THREAT_HISTORY = 20

private fun RawWarThreat.toView(deployments: Array<RawArmyDeployment>, isGM: Boolean): WarThreatView {
    val max = if (maxEscalation > 0) maxEscalation else 1
    val assigned = deployments.any { it.assignedThreatId == id }
    val active = status == WarThreatStatus.ACTIVE.value
    return WarThreatView(
        id = id,
        name = name,
        description = description,
        enemyFaction = enemyFaction,
        escalationLevel = escalationLevel,
        maxEscalation = max,
        escalationPercent = (escalationLevel * 100 / max).coerceIn(0, 100),
        eta = eta,
        status = status,
        targetHexLocation = targetHexLocation,
        pauseOnExpiry = pauseOnExpiry,
        hasAssignedArmies = assigned,
        canResolveBattle = active && assigned,
        // Only the GM view ever sees a hidden threat, flagged for the badge. visibleToPlayers is
        // nullable for migration safety; null/true = visible, so only an explicit false hides it.
        hiddenFromPlayers = isGM && visibleToPlayers == false,
    )
}

/** A threat is hidden from players only when its flag is explicitly false (null/true = visible). */
private fun RawWarThreat.isVisibleToPlayers(): Boolean = visibleToPlayers != false

private fun RawArmyDeployment.toView(settlementNames: Map<String, String>): ArmyDeploymentView = ArmyDeploymentView(
    id = id,
    armyName = armyName,
    armyType = armyType,
    status = status,
    assignedThreatId = assignedThreatId,
    garrisonedSettlementId = garrisonedSettlementId,
    garrisonedSettlementName = garrisonedSettlementId?.let { settlementNames[it] },
)

private fun RawWarPressure.toView(projection: WarPressureProjection? = null): WarPressureView = WarPressureView(
    currentPressure = currentPressure,
    pressurePercent = currentPressure.coerceIn(0, 100),
    pressurePerTurn = pressurePerTurn,
    unrestThreshold = unrestThreshold,
    ruinThreshold = ruinThreshold,
    atUnrestThreshold = currentPressure >= unrestThreshold,
    atRuinThreshold = currentPressure >= ruinThreshold,
    unrestModifier = unrestModifier,
    consumptionModifier = consumptionModifier,
    projection = projection,
)

/**
 * Builds the Army & War Pressure view model from raw kingdom data.
 *
 * @param threats Array of raw war threats (nullable)
 * @param deployments Array of army deployments (nullable)
 * @param pressure Current war pressure state (nullable)
 * @param settings Kingdom settings (for mode selection)
 * @param currentTurn Current kingdom turn number (for projection calculations)
 * @param settlementNames Map of settlement scene ID to settlement name
 * @return The view model for the army pressure board
 */
fun buildArmyPressureView(
    threats: Array<RawWarThreat>?,
    deployments: Array<RawArmyDeployment>?,
    pressure: RawWarPressure?,
    settings: KingdomSettings,
    currentTurn: Int = 0,
    settlementNames: Map<String, String> = emptyMap(),
    isGM: Boolean = true,
): ArmyPressureView {
    val deploymentArray = deployments ?: emptyArray()
    val threatArray = threats ?: emptyArray()

    // Projection (war-pressure math) always runs over EVERY threat — hidden threats are real, just
    // unseen — so fog-of-war never changes the mechanics.
    val projection = if (settings.armyPressureBoardModeOrDefault() == "advanced" && pressure != null) {
        projectWarPressure(
            currentPressure = pressure.currentPressure,
            pressurePerTurn = pressure.pressurePerTurn,
            unrestThreshold = pressure.unrestThreshold,
            ruinThreshold = pressure.ruinThreshold,
            threats = threatArray.map { it.toSnapshot() },
            currentTurn = currentTurn,
        )
    } else {
        null
    }

    // Fog-of-war at the SOURCE: a non-GM view never carries hidden-threat data at all (the
    // NotesContext leak lesson — exclude here, never merely hide in the template).
    val visibleThreats = if (isGM) threatArray.toList() else threatArray.filter { it.isVisibleToPlayers() }
    // A resolved threat lingers on the board briefly so the outcome is visible, then moves to
    // history. Anything still ACTIVE stays regardless of age -- an old war is still a war.
    val (historic, displayThreats) = visibleThreats.partition {
        it.status != WarThreatStatus.ACTIVE.value &&
            it.triggeredTurn != null &&
            currentTurn - (it.triggeredTurn ?: 0) > WAR_THREAT_HISTORY_AFTER_TURNS
    }

    return ArmyPressureView(
        enabled = settings.isArmyPressureBoardEnabled(),
        showThreatDistance = settings.shouldShowThreatDistance(),
        threats = displayThreats.map { it.toView(deploymentArray, isGM) },
        threatHistory = historic
            .sortedByDescending { it.triggeredTurn ?: 0 }
            .take(MAX_WAR_THREAT_HISTORY)
            .map { it.toView(deploymentArray, isGM) },
        deployments = deploymentArray.map { it.toView(settlementNames) },
        pressure = pressure?.toView(projection),
    )
}

/**
 * Converts a RawWarThreat to a WarThreatSnapshot for projection calculations.
 */
private fun RawWarThreat.toSnapshot(): WarThreatSnapshot {
    val id = this.id
    val name = this.name
    val escalationLevel = this.escalationLevel
    val maxEscalation = this.maxEscalation
    val eta = this.eta
    val pauseOnExpiry = this.pauseOnExpiry
    val status = this.status
    val triggeredTurn = this.triggeredTurn
    val offerConsumed = this.offerConsumed
    val obj = js("{ id: id, name: name, escalationLevel: escalationLevel, maxEscalation: maxEscalation, eta: eta, pauseOnExpiry: pauseOnExpiry, status: status, triggeredTurn: triggeredTurn, offerConsumed: offerConsumed }")
    return obj.unsafeCast<WarThreatSnapshot>()
}