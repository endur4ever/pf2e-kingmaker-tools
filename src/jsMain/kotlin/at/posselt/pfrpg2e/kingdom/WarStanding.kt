package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.applyStandingDelta
import at.posselt.pfrpg2e.kingdom.data.RawFactionStandingEntry
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import at.posselt.pfrpg2e.kingdom.data.WarThreatStatus

/**
 * Apply a war-driven standing change to the group named [factionName] and record it in that
 * faction's standing log.
 *
 * Mutates the caller's kingdom clone and does NOT persist — the caller owns the single
 * `setKingdom` for the whole handler, per the sheet's one-mutate-one-persist rule.
 *
 * Returns false when no group carries that name, which happens when a faction is renamed or deleted
 * after a war starts. Callers are expected to tell the GM rather than swallow it: the standing was
 * earned, and silently dropping it is how a tracker loses trust.
 */
fun KingdomData.applyWarStanding(factionName: String, delta: Int, reason: String): Boolean {
    val group = groups.find { it.name == factionName } ?: return false
    if (delta != 0) {
        group.standing = applyStandingDelta(group.standing, delta)
    }
    group.standingLog = (group.standingLog ?: emptyArray()) + RawFactionStandingEntry(
        turn = currentTurn ?: 0,
        delta = delta,
        reason = reason,
    )
    return true
}

/** How a threat ended, in the terms the pure peace logic understands. */
fun RawWarThreat.threatOutcome(): ThreatOutcome =
    when (WarThreatStatus.fromString(status)) {
        WarThreatStatus.DEFEATED -> ThreatOutcome.DEFEATED
        WarThreatStatus.ACTIVE, null -> ThreatOutcome.ACTIVE
        WarThreatStatus.EVADED, WarThreatStatus.EXPIRED -> ThreatOutcome.ENDED_OTHERWISE
    }

/** The kingdom's threats as [ThreatState]s, for [peaceEligible]. */
fun KingdomData.threatStates(): List<ThreatState> =
    (warThreats ?: emptyArray()).map {
        ThreatState(
            enemyFactionName = it.enemyFactionName,
            outcome = it.threatOutcome(),
            peaceSettled = it.peaceSettled == true,
        )
    }

/**
 * Whether the kingdom-wide war flag should remain set once [factionName] stops being at war.
 *
 * The kingdom flag carries a standing +1 unrest per turn, so it must survive making peace with one
 * of several enemies.
 */
fun KingdomData.kingdomStillAtWarWithout(factionName: String): Boolean =
    kingdomRemainsAtWar(
        otherFactionsAtWar = groups.filter { it.name != factionName }.map { it.atWar },
        anyThreatStillActive = (warThreats ?: emptyArray()).any { it.threatOutcome() == ThreatOutcome.ACTIVE },
    )

/**
 * Close the war with [factionName]: stamp every one of its threats as settled so no offer card --
 * this one or any still sitting in chat scrollback -- can conclude the same war twice.
 *
 * Mutates the caller's clone without persisting, like [applyWarStanding].
 */
fun KingdomData.settlePeaceWith(factionName: String) {
    warThreats = (warThreats ?: emptyArray()).map {
        if (it.enemyFactionName == factionName) RawWarThreat.copy(it, peaceSettled = true) else it
    }.toTypedArray()
}

/**
 * Standing a signed peace treaty raises the former enemy to.
 *
 * Defaults to 0 — the midpoint of the Indifferent band. Peace is not friendship: it stops the war
 * and wipes the grudge, it does not make them like you.
 */
fun KingdomSettings.peaceStandingFloorOrDefault(): Int = peaceStandingFloor ?: 0

/** One-time RP indemnity extracted by demanding tribute instead of signing peace. */
fun KingdomSettings.peaceTributeRpOrDefault(): Int = peaceTributeRp ?: 5
