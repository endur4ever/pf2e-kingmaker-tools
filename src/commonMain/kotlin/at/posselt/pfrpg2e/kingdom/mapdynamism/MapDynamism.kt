package at.posselt.pfrpg2e.kingdom.mapdynamism

/**
 * Pure core of map dynamism (`docs/plans/2026-07-09-plan-map-dynamism.md`, §3.1): re-wild timers
 * for cleared-but-unclaimed hexes, and one-hex-per-turn migration steps for wandering war threats.
 *
 * Everything computed here is a PROPOSAL (§5). The GM owns the canonical map, and every externally
 * visible consequence — moving a threat marker, lapsing a hex's `cleared` flag — happens only
 * behind a GM-confirmed offer button. This core therefore never writes `kingmaker.state`, never
 * mutates a threat, and never touches the kingdom flag; it turns persisted state plus the current
 * turn number into values the tick surfaces as offers.
 *
 * The runtime types (RawWarThreat's mobile fields, RawRewildTracker) are jsMain and cannot be
 * referenced from commonMain, so [WanderingThreat] and [RewildTracker] are pure mirrors: the
 * phase-2 jsMain adapter maps the raw interfaces into them before calling in, and injects the real
 * hex adjacency as a closure. No clock and no randomness anywhere, so the End-Turn preview tick
 * and the commit tick — `TurnTickingEngine.tick` runs both — yield identical proposals from
 * identical state (the §3.2 parity invariant).
 */

/**
 * Pure mirror of one RawRewildTracker side-table entry (jsMain; the phase-2 adapter maps it).
 *
 * The timer lives on the kingdom flag's side-table because the native `kingmaker.state` hex
 * entries are read-only to this module and carry no turn field (§2.1): "cleared but unclaimed" is
 * a live predicate over two booleans, so WHEN a hex entered that state has to be remembered
 * elsewhere. [clearedSinceTurn] is the kingdom turn the hex was first observed cleared and
 * unclaimed; the authoritative cleared/claimed booleans stay in `kingmaker.state`.
 */
data class RewildTracker(
    val hexKey: String,
    val clearedSinceTurn: Int,
)

/**
 * Reconciles the re-wild side-table against the live set of cleared-but-unclaimed hexes for one
 * turn. This is the one output the tick persists without a GM offer: starting, keeping, or
 * dropping a timer writes nothing to the shared map (§3.3) — only the candidates become offers.
 *
 * A hex still in [clearedUnclaimedHexes] keeps its tracker with its ORIGINAL
 * [RewildTracker.clearedSinceTurn]: this reconcile runs every turn, so a timer that restarted on
 * each pass would never reach the delay and the mechanic would silently do nothing. A hex that
 * left the set was claimed, re-wilded, or un-cleared — in every case the pressure is over, and the
 * timer resets by dropping; a later re-clear starts a fresh tracker at that turn. Result order is
 * surviving trackers in [existing] order, then new hexes in sorted order, so the persisted flag
 * and the digest built from it stay byte-identical between the preview and commit ticks.
 */
fun updateRewildTrackers(
    existing: List<RewildTracker>,
    clearedUnclaimedHexes: Set<String>,
    currentTurn: Int,
): List<RewildTracker> {
    val surviving = existing.filter { it.hexKey in clearedUnclaimedHexes }
    val tracked = surviving.map { it.hexKey }.toSet()
    val started = (clearedUnclaimedHexes - tracked)
        .sorted()
        .map { RewildTracker(hexKey = it, clearedSinceTurn = currentTurn) }
    return surviving + started
}

/**
 * Hex keys whose timer has elapsed: an age of `currentTurn - clearedSinceTurn` at or beyond
 * [delayTurns]. Each is an offer for the GM, never an applied change (§5.2).
 *
 * A [delayTurns] of zero or below DISABLES re-wilding entirely — the settings dial reads
 * "0 = never" — it does not mean "instantly". Without the early return, zero would satisfy the
 * age comparison for every tracker on the very turn it starts and offer to re-wild the whole
 * frontier at once. Callers keep reconciling trackers while disabled, so the timers stay honest
 * if the GM later re-enables the dial.
 */
fun rewildCandidates(
    trackers: List<RewildTracker>,
    currentTurn: Int,
    delayTurns: Int,
): List<String> {
    if (delayTurns <= 0) return emptyList()
    return trackers
        .filter { currentTurn - it.clearedSinceTurn >= delayTurns }
        .map { it.hexKey }
}

/**
 * Pure mirror of RawWarThreat's mobile fields (jsMain; the phase-2 adapter maps it, resolving the
 * raw null-current-hex fallback to the target hex before calling in, which is why [currentHexKey]
 * is non-null here).
 *
 * [migrationConsumedTurn] is deliberately per-turn rather than a permanent flag: a held threat is
 * re-offered next turn from the same hex, so its momentum resumes unless the GM turns wandering
 * off — one Hold must never permanently silence a threat (§5.1).
 */
data class WanderingThreat(
    val threatId: String,
    val currentHexKey: String,
    val targetHexKey: String,
    val migrationConsumedTurn: Int? = null,
)

/** A proposed one-hex step for one threat. Nothing moves until the GM clicks Advance (§5). */
data class ThreatMigrationProposal(
    val threatId: String,
    val fromHex: String,
    val toHex: String,
)

/**
 * The first step along a shortest path from [currentHexKey] to [targetHexKey], or null when the
 * threat is already at its target, the target is unreachable, or the search would have to expand
 * beyond [maxSearchDepth] breadth-first levels.
 *
 * Adjacency is an INJECTED closure because the real hex graph is Foundry-runtime state —
 * `KingmakerHexGridProvider` reads `kingmaker.region` — and this core must stay unit-testable
 * against fakes; the jsMain adapter injects the provider exactly as the milestone road check
 * does. One call is one BFS, and the tick makes one call per wandering threat (single digits in
 * practice), never one per hex — the cost-discipline bound the plan commits to. [maxSearchDepth]
 * serves the same discipline: a corrupt or disconnected region graph must cap out instead of
 * walking an unbounded frontier inside the End-Turn tick.
 *
 * Deterministic by construction: each frontier hex expands its neighbors in sorted order, so when
 * several shortest paths exist the same first step wins on every call — the preview and commit
 * ticks must propose the identical move.
 */
fun nextThreatHex(
    currentHexKey: String,
    targetHexKey: String,
    neighbors: (String) -> Set<String>,
    maxSearchDepth: Int = 64,
): String? {
    if (currentHexKey == targetHexKey) return null
    val visited = mutableSetOf(currentHexKey)
    // For each reached hex, the neighbor of the start through which it was FIRST reached: BFS
    // reaches every hex at its shortest distance, so the recorded step lies on a shortest path.
    val firstStep = mutableMapOf<String, String>()
    var frontier = listOf(currentHexKey)
    var depth = 0
    while (frontier.isNotEmpty() && depth < maxSearchDepth) {
        depth += 1
        val next = mutableListOf<String>()
        for (hex in frontier) {
            for (neighbor in neighbors(hex).sorted()) {
                if (!visited.add(neighbor)) continue
                val step = firstStep[hex] ?: neighbor
                if (neighbor == targetHexKey) return step
                firstStep[neighbor] = step
                next.add(neighbor)
            }
        }
        frontier = next
    }
    return null
}

/**
 * One proposed step for every wandering threat that should move this turn. Proposals only, never
 * writes (§5): the tick surfaces each as an Advance-or-Hold offer, and the threat's position
 * changes only in the GM's click handler.
 *
 * Skipped: a threat whose [WanderingThreat.migrationConsumedTurn] equals [currentTurn] — its offer
 * was already resolved (advanced OR held) this turn, and without the guard the next tick would
 * immediately re-offer the step the GM just took or declined — and a threat already at its target,
 * because arrival is the war-threat escalation flow's hand-off, not a migration (§5.3). At most
 * one proposal per threat: a step is one hex, whatever the remaining path length.
 */
fun proposeThreatSteps(
    threats: List<WanderingThreat>,
    currentTurn: Int,
    neighbors: (String) -> Set<String>,
): List<ThreatMigrationProposal> =
    threats.mapNotNull { threat ->
        if (threat.migrationConsumedTurn == currentTurn) return@mapNotNull null
        nextThreatHex(threat.currentHexKey, threat.targetHexKey, neighbors)?.let { toHex ->
            ThreatMigrationProposal(
                threatId = threat.threatId,
                fromHex = threat.currentHexKey,
                toHex = toHex,
            )
        }
    }
