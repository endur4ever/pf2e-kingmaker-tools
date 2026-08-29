package at.posselt.pfrpg2e.data.kingdom

/**
 * Faction Agenda Engine, pure core (docs/plans/2026-07-09-plan-faction-agenda.md section 3.1).
 *
 * Phase 1 ships the deterministic identity pieces: archetype assignment, initial goal draw and
 * the iterated RNG. The plan's signatures name jsMain Raw* types, but commonMain stays
 * Foundry-free by contract, so the engine works on pure mirrors and the jsMain adapter maps.
 */

/** Stable order matters: the name hash indexes into this list. Never reorder. */
val FACTION_ARCHETYPE_IDS: List<String> = listOf("aggressive", "mercantile", "fey", "political", "monster")

/**
 * A portable 31-based fold, spelled out rather than relying on String.hashCode so the
 * assignment can never reshuffle across Kotlin targets or stdlib versions.
 */
fun stableFactionHash(name: String): Int =
    name.fold(0) { acc, c -> acc * 31 + c.code }

/** Deterministic archetype by name hash (plan 2.3 / open question 1's stated default). */
fun pickArchetypeForFaction(name: String): String {
    val index = stableFactionHash(name).mod(FACTION_ARCHETYPE_IDS.size)
    return FACTION_ARCHETYPE_IDS[index]
}

/**
 * The migration's one-shot initial goal draw: deterministic by name so a re-run of the backfill
 * can never land on a different goal. In play, completed goals redraw through [TurnRng] instead.
 */
fun initialGoalForFaction(name: String, goalPool: List<String>): String? {
    if (goalPool.isEmpty()) return null
    return goalPool[stableFactionHash(name).mod(goalPool.size)]
}

/**
 * Iterated LCG (plan 3.2). The earlier draft's salt-linear form collapsed `% 10` to two buckets;
 * this one ITERATES its state, so consecutive draws walk the full range. Callers must consume it
 * in a fixed order (factions sorted by name), or preview and commit diverge on the same seed.
 */
class TurnRng(seed: Int) {
    private var state: Int = seed

    /** Next value in [0, bound). Same seed + same call order = same sequence. */
    fun next(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        state = state * 1664525 + 1013904223
        return ((state ushr 1) % bound).let { if (it < 0) it + bound else it }
    }
}

/** The per-turn seed (plan 3.2): kingdom name stands in for the id KingdomData does not have. */
fun factionAgendaTurnSeed(kingdomName: String, currentTurn: Int): Int =
    stableFactionHash(kingdomName) * 31 + currentTurn
