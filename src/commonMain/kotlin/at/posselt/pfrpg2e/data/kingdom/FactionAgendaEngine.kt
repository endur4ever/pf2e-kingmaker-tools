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

// ── Phase 2: the pure advance engine ─────────────────────────────────────────────────────────

/** Pure mirror of RawFactionAgenda; the jsMain adapter maps both ways. */
data class FactionAgendaState(
    val goalId: String,
    val goalTitle: String,
    val progress: Int,
    val segments: Int,
    val archetype: String,
    val moveCooldowns: Map<String, Int>,
    val lastAdvancedTurn: Int?,
    val targetFaction: String?,
)

/** What the engine may know about one faction. Standing is the PC-facing value RawGroup keeps. */
data class FactionSnapshot(
    val name: String,
    val standing: Int?,
    val atWar: Boolean,
    val hasHex: Boolean,
    val agenda: FactionAgendaState?,
)

/** Pure mirror of one RawFactionAgendaMove catalog row. */
data class AgendaMoveSpec(
    val id: String,
    val cooldownTurns: Int,
    val validTargets: String,
    val effect: String,
    val effectMagnitude: Int,
    val requires: String?,
)

/** Pure mirror of one RawFactionAgendaArchetype row. */
data class AgendaArchetypeSpec(
    val id: String,
    val weights: Map<String, Int>,
    val goals: List<String>,
)

/**
 * Everything a move wants to do to the world. NOTHING here is applied by the tick: standing
 * deltas, war threats and armies are all GM-confirmed offers (the faction-tracker rule), so the
 * tick stays preview-safe and the engine only advances agenda-internal state.
 */
sealed interface AgendaMoveEffect {
    /** Internal: the goal clock advanced. Applied by the tick itself, no offer. */
    data class ClockSegments(val segments: Int) : AgendaMoveEffect

    /**
     * Offer intent: shift [targetFaction]'s standing by [delta] on GM confirm. The crossing
     * flags implement section 6.3: they are set only when the HYPOTHETICAL post-delta attitude
     * crosses into HOSTILE (war threat) / FRIENDLY+ (diplomacy quest), never on a re-touch.
     */
    data class StandingDelta(
        val targetFaction: String,
        val delta: Int,
        val offerWarThreat: Boolean,
        val offerDiplomacyQuest: Boolean,
    ) : AgendaMoveEffect

    /** Offer intent: the faction courts the PCs; its own PC-facing standing rises on accept. */
    data class CourtPcs(val delta: Int, val offerDiplomacyQuest: Boolean) : AgendaMoveEffect

    /** Offer intent: the faction raises an army; the GM shapes it in AddWarThreat on confirm. */
    data class ArmyRaised(val strength: Int) : AgendaMoveEffect
}

/** One concrete move a faction took this turn. */
data class AgendaFactionMove(
    val factionName: String,
    val moveId: String,
    val targetFaction: String?,
    val effect: AgendaMoveEffect,
    val goalCompleted: Boolean,
    /** Clock reading after this move, for the gazette line; null on non-clock moves. */
    val progressAfter: Int? = null,
    val segments: Int? = null,
)

data class AgendaTickResult(
    val factions: List<FactionSnapshot>,
    val moves: List<AgendaFactionMove>,
)

private fun cooldownsTicked(cooldowns: Map<String, Int>): Map<String, Int> =
    cooldowns.mapValues { (_, v) -> v - 1 }.filterValues { it > 0 }

/** Rival = lowest PC-facing standing among the OTHER factions not at war; ties break by name. */
fun pickRival(actor: FactionSnapshot, all: List<FactionSnapshot>): FactionSnapshot? =
    all.filter { it.name != actor.name && !it.atWar }
        .minWithOrNull(compareBy({ it.standing ?: 0 }, { it.name }))

/** Ally = highest standing among the OTHER factions; ties break by first name. */
fun pickAlly(actor: FactionSnapshot, all: List<FactionSnapshot>): FactionSnapshot? =
    all.filter { it.name != actor.name }
        .sortedBy { it.name }
        .maxByOrNull { it.standing ?: 0 }

private fun requiresSatisfied(requires: String?, actor: FactionSnapshot): Boolean = when (requires) {
    null -> true
    "not-at-war" -> !actor.atWar
    "has-hex" -> actor.hasHex
    else -> false // an unknown precondition can never be satisfied -- fail closed
}

/**
 * The archetype-weighted pick (plan section 3.1). Eligible = weighted in this archetype, off
 * cooldown, precondition satisfied, and a live target exists. Iterates candidates sorted by id
 * so the RNG walk is order-stable; returns null when nothing is eligible (the faction idles).
 */
fun pickAgendaMove(
    actor: FactionSnapshot,
    agenda: FactionAgendaState,
    all: List<FactionSnapshot>,
    moves: Map<String, AgendaMoveSpec>,
    archetype: AgendaArchetypeSpec,
    rng: TurnRng,
): AgendaMoveSpec? {
    val cooldowns = agenda.moveCooldowns
    val eligible = archetype.weights
        .filterValues { it > 0 }
        .keys
        .mapNotNull { moves[it] }
        .filter { (cooldowns[it.id] ?: 0) <= 0 }
        .filter { requiresSatisfied(it.requires, actor) }
        .filter {
            when (it.validTargets) {
                "rival" -> pickRival(actor, all) != null
                "ally" -> pickAlly(actor, all) != null
                else -> true
            }
        }
        .sortedBy { it.id }
    if (eligible.isEmpty()) return null
    val total = eligible.sumOf { archetype.weights[it.id] ?: 0 }
    if (total <= 0) return null
    var roll = rng.next(total)
    for (candidate in eligible) {
        roll -= archetype.weights[candidate.id] ?: 0
        if (roll < 0) return candidate
    }
    return eligible.last()
}

/** Draw a new goal on completion; a >1 pool never redraws the finished goal. */
fun drawNextGoal(current: String, pool: List<String>, rng: TurnRng): String {
    if (pool.isEmpty()) return current
    if (pool.size == 1) return pool[0]
    val choices = pool.filter { it != current }.sorted()
    return choices[rng.next(choices.size)]
}

/**
 * Advance every faction's agenda for one kingdom turn. Factions iterate SORTED BY NAME -- the
 * RNG sequence depends on call order, so map order would break preview/commit parity.
 * [pendingWarThreatFactions] is the section 6.3 double-fire guard: a faction that already has
 * an unanswered war threat never gets its crossing flag set again.
 */
fun advanceAllAgendas(
    factions: List<FactionSnapshot>,
    currentTurn: Int,
    moves: Map<String, AgendaMoveSpec>,
    archetypes: Map<String, AgendaArchetypeSpec>,
    rng: TurnRng,
    pendingWarThreatFactions: Set<String> = emptySet(),
): AgendaTickResult {
    val emitted = mutableListOf<AgendaFactionMove>()
    // victims flagged THIS tick join the suppression set, so two same-turn crossings on one
    // faction can never mint two war-threat offers
    val flaggedThisTick = mutableSetOf<String>()
    // index is the identity through the sort: duplicate NAMES must not collapse rows
    val updatedByIndex = mutableMapOf<Int, FactionSnapshot>()
    factions.withIndex().sortedBy { it.value.name }.forEach { (index, faction) ->
        val agenda = faction.agenda
        if (agenda == null || agenda.lastAdvancedTurn == currentTurn) {
            updatedByIndex[index] = faction
            return@forEach
        }
        val archetype = archetypes[agenda.archetype]
        if (archetype == null) {
            updatedByIndex[index] = faction
            return@forEach
        }
        val ticked = agenda.copy(moveCooldowns = cooldownsTicked(agenda.moveCooldowns))
        val spec = pickAgendaMove(faction, ticked, factions, moves, archetype, rng)
        if (spec == null) {
            updatedByIndex[index] = faction.copy(agenda = ticked.copy(lastAdvancedTurn = currentTurn))
            return@forEach
        }
        val rival = if (spec.validTargets == "rival") pickRival(faction, factions) else null
        val ally = if (spec.validTargets == "ally") pickAlly(faction, factions) else null
        val target = rival ?: ally
        val effect: AgendaMoveEffect = when (spec.effect) {
            "clock" -> AgendaMoveEffect.ClockSegments(spec.effectMagnitude)
            "standing-delta" -> when (spec.validTargets) {
                "pcs" -> {
                    val after = applyStandingDelta(faction.standing, spec.effectMagnitude)
                    AgendaMoveEffect.CourtPcs(
                        delta = spec.effectMagnitude,
                        offerDiplomacyQuest = shouldOfferDiplomacyQuest(faction.standing, after),
                    )
                }
                else -> {
                    val victim = target
                    if (victim == null) {
                        updatedByIndex[index] = faction.copy(agenda = ticked.copy(lastAdvancedTurn = currentTurn))
                        return@forEach
                    }
                    val after = applyStandingDelta(victim.standing, spec.effectMagnitude)
                    val offerWar = victim.name !in pendingWarThreatFactions &&
                        victim.name !in flaggedThisTick &&
                        shouldOfferWarThreat(victim.standing, after)
                    if (offerWar) flaggedThisTick += victim.name
                    AgendaMoveEffect.StandingDelta(
                        targetFaction = victim.name,
                        delta = spec.effectMagnitude,
                        offerWarThreat = offerWar,
                        offerDiplomacyQuest = shouldOfferDiplomacyQuest(victim.standing, after),
                    )
                }
            }
            "war-threat", "army" -> AgendaMoveEffect.ArmyRaised(spec.effectMagnitude)
            else -> AgendaMoveEffect.ClockSegments(0)
        }
        val progressed = if (effect is AgendaMoveEffect.ClockSegments) {
            ticked.progress + effect.segments
        } else {
            ticked.progress
        }
        val completed = progressed >= ticked.segments
        val nextAgenda = if (completed) {
            ticked.copy(
                goalId = drawNextGoal(ticked.goalId, archetype.goals, rng),
                goalTitle = "",
                progress = 0,
                lastAdvancedTurn = currentTurn,
                moveCooldowns = if (spec.cooldownTurns > 0) {
                    ticked.moveCooldowns + (spec.id to spec.cooldownTurns)
                } else {
                    ticked.moveCooldowns
                },
                targetFaction = target?.name ?: ticked.targetFaction,
            )
        } else {
            ticked.copy(
                progress = progressed,
                lastAdvancedTurn = currentTurn,
                moveCooldowns = if (spec.cooldownTurns > 0) {
                    ticked.moveCooldowns + (spec.id to spec.cooldownTurns)
                } else {
                    ticked.moveCooldowns
                },
                targetFaction = target?.name ?: ticked.targetFaction,
            )
        }
        emitted += AgendaFactionMove(
            factionName = faction.name,
            moveId = spec.id,
            targetFaction = target?.name,
            effect = effect,
            goalCompleted = completed,
            progressAfter = if (effect is AgendaMoveEffect.ClockSegments) progressed else null,
            segments = if (effect is AgendaMoveEffect.ClockSegments) ticked.segments else null,
        )
        updatedByIndex[index] = faction.copy(agenda = nextAgenda)
    }
    // original order by INDEX: name keys would collapse duplicate-named groups
    return AgendaTickResult(
        factions = factions.mapIndexed { index, faction -> updatedByIndex[index] ?: faction },
        moves = emitted,
    )
}
