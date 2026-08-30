package at.posselt.pfrpg2e.kingdom.settlementlife

/**
 * Pure core of the settlement life-event generator (`docs/plans/2026-07-09-plan-settlement-life.md`).
 *
 * Everything here is deliberately blind to Foundry: the jsMain catalog and roster types
 * (RawSettlementLifeEvent, RawNpcEntry and friends) never cross into this package. The phase-2
 * jsMain adapter parses them into the pure mirrors below — [LifeEventTemplate] and [RosterMember] —
 * so eligibility, weighting and casting stay testable without a browser, and so the Turn-Wizard
 * preview computes on exactly the data the End-Turn commit will (§3.4: preview parity is a
 * determinism guarantee, and determinism starts with keeping the engine free of live-object reads).
 */

/**
 * The CLOSED mechanical-hook vocabulary (§5.2): a life event may nudge unrest, grant RP, offer a
 * quest, plant a rumor, or be pure flavor. Templates are data files, so this enum is the whole
 * contract — a new consequence kind is a new feature, not a new template — and an unknown stored
 * kind maps to null so one bad file loses its hook instead of crashing the tick.
 */
enum class LifeEventHookKind(val value: String) {
    // the serialized spellings are the plan's section 5.2 vocabulary verbatim, which is also what
    // the shipped template JSON carries -- the phase-1 core had shortened them, and a template
    // written to the plan would then have parsed to a null hook
    UNREST("unrest-delta"),
    RP("rp-delta"),
    QUEST("quest-spawn"),
    RUMOR("rumor-spawn"),
    NONE("none");

    companion object {
        fun fromValue(value: String?): LifeEventHookKind? = entries.find { it.value == value }
    }
}

/**
 * A life event nudges, never swings a turn (§5.2): whatever magnitude a template's JSON claims,
 * unrest moves by at most one point either way. Applied at parse time — before the magnitude is
 * ever stored on a record or echoed onto an offer button — so no template, however miswritten,
 * can smuggle a larger swing past the GM's Apply click.
 */
fun clampLifeEventUnrest(delta: Int): Int = delta.coerceIn(-1, 1)

/**
 * Pure mirror of one catalog entry (§2.1), reduced to the phase-1 shape: one optional required
 * structure, season and occupation instead of the full multiplier tables. The jsMain adapter maps
 * the Raw catalog onto this in phase 2; keeping the mirror flat keeps every eligibility rule a
 * plain comparison a test can hit from both sides.
 *
 * [weight] is relative to the other eligible templates — see [weightedPick]. [hookMagnitude] is
 * expected to have passed [clampLifeEventUnrest] at parse time when [hookKind] is unrest.
 */
data class LifeEventTemplate(
    val id: String,
    /** Relative selection weight BEFORE structure and season multipliers. */
    val weight: Int,
    /** ALL of these base structure ids must be present; empty = always eligible. */
    val requiresStructures: List<String> = emptyList(),
    /** (anyOf ids, multiplier): having ANY id in the group multiplies the weight once. */
    val structureWeights: List<Pair<List<String>, Double>> = emptyList(),
    /** Per-season multiplier; a season absent from the map is 1.0, never 0. */
    val seasonWeights: Map<String, Double> = emptyMap(),
    val minSettlementLevel: Int = 0,
    val minPopulation: Int = 0,
    /** (slot, preferredOccupations, distinctFromSlots) in cast order. */
    val castSlots: List<Triple<String, List<String>, List<String>>> = emptyList(),
    val hookKind: LifeEventHookKind,
    val hookMagnitude: Int = 0,
    val cooldownTurns: Int = 0,
)

/**
 * Kingdom-wide hard cap on life events surfaced in one turn (§3): the digest is flavor, and
 * flavor that floods the chat log stops being read. The per-settlement cadence — at most one
 * roll per settlement per turn — is the phase-3 caller's job; this constant only bounds what
 * the whole kingdom may surface after those rolls.
 */
const val MAX_LIFE_EVENTS_PER_TURN = 2

/**
 * Filters [templates] down to the ones this settlement may roll (§3.2). Hard filters run before
 * weighting, so an ineligible template can never leak back in through a large weight.
 *
 * [LifeEventTemplate.requiresStructure] matches [structureNames] ignoring case and padding —
 * structure names are GM-typed, and "Theater " must not read as a missing theater. A required
 * season matches [season] exactly (seasons come from the calendar code, not a GM's keyboard) and
 * is unmet when [season] is null: a calendar that reports no season cannot host a winter-only
 * feast. Order is preserved so [weightedPick]'s cumulative ranges stay stable.
 */
fun eligibleTemplates(
    templates: List<LifeEventTemplate>,
    structureIds: Set<String>,
    season: String?,
    population: Int,
    settlementLevel: Int = 0,
    /** templateId -> the turn it last fired here; a template on cooldown is filtered out. */
    lastFiredByTemplate: Map<String, Int> = emptyMap(),
    currentTurn: Int = 0,
): List<LifeEventTemplate> =
    templates.filter { template ->
        template.requiresStructures.all { required -> structureMatches(required, structureIds) } &&
            population >= template.minPopulation &&
            settlementLevel >= template.minSettlementLevel &&
            cooldownElapsed(lastFiredByTemplate[template.id], currentTurn, template.cooldownTurns) &&
            // a season the template weights at ZERO is not merely unlikely, it is out of season
            seasonWeightOf(template, season) > 0.0
    }

/** Structure ids are data, not GM prose, but trimmed/case-insensitive matching costs nothing. */
private fun structureMatches(required: String, structureIds: Set<String>): Boolean {
    val wanted = required.trim()
    return structureIds.any { it.trim().equals(wanted, ignoreCase = true) }
}

private fun cooldownElapsed(lastFired: Int?, currentTurn: Int, cooldownTurns: Int): Boolean =
    lastFired == null || cooldownTurns <= 0 || currentTurn - lastFired > cooldownTurns

/** A season the template says nothing about is neutral (1.0); a null season is likewise neutral. */
fun seasonWeightOf(template: LifeEventTemplate, season: String?): Double {
    if (season == null) return 1.0
    return template.seasonWeights[season.trim().lowercase()] ?: 1.0
}

/**
 * The template's weight in THIS settlement: base, times each structure group it satisfies, times
 * the season multiplier, floored at 0 and rounded to an Int so [weightedPick]'s cumulative walk
 * stays integral. A multiplier group counts ONCE however many of its ids the settlement has --
 * owning three taverns does not make a feast eight times likelier.
 */
fun effectiveWeight(
    template: LifeEventTemplate,
    structureIds: Set<String>,
    season: String?,
): Int {
    var weight = template.weight.toDouble()
    template.structureWeights.forEach { (anyOf, multiplier) ->
        if (anyOf.any { structureMatches(it, structureIds) }) weight *= multiplier
    }
    weight *= seasonWeightOf(template, season)
    return weight.coerceAtLeast(0.0).toInt()
}

/** The eligible templates re-weighted for this settlement, ready for [weightedPick]. */
fun weightedForSettlement(
    templates: List<LifeEventTemplate>,
    structureIds: Set<String>,
    season: String?,
): List<LifeEventTemplate> =
    templates.map { it.copy(weight = effectiveWeight(it, structureIds, season)) }

/**
 * Deterministic cumulative-weight walk: the positive weights partition `0 until total` (total
 * being the sum of POSITIVE weights only) into contiguous ranges in list order, and [roll]
 * selects the range it falls in. A template with a non-positive weight owns no range at all, so
 * no roll can ever land on it. A roll outside `0 until total` — which includes every roll
 * against an empty or all-nonpositive list, whose total is zero — returns null.
 *
 * The caller supplies [roll] from the existing SeededRng (§3.4): keeping the draw outside the
 * engine keeps this function free of randomness, which is what lets the Turn-Wizard preview and
 * the End-Turn commit pick the same event from the same seed.
 */
fun weightedPick(templates: List<LifeEventTemplate>, roll: Int): LifeEventTemplate? {
    val total = templates.sumOf { maxOf(it.weight, 0) }
    if (roll < 0 || roll >= total) return null
    var cumulative = 0
    for (template in templates) {
        if (template.weight <= 0) continue
        cumulative += template.weight
        if (roll < cumulative) return template
    }
    return null
}

/** Pure mirror of one roster resident (jsMain RawNpcEntry); the phase-2 adapter maps it here. */
data class RosterMember(
    val id: String,
    val name: String,
    val occupation: String,
)

/**
 * One name filling a template's cast slot. [npcId] is null for an EPHEMERAL member invented when
 * the roster had nobody to offer: the null is the guard that keeps such an invention from ever
 * being mistaken for a roster resident or persisted back into the roster (§3.3 — the generator
 * never resurrects user-deleted NPCs).
 */
data class CastMember(
    val npcId: String?,
    val name: String,
)

/**
 * Picks who a life event happens to (§3.3). Occupation is a soft preference: a match — trimmed
 * and case-insensitive, occupations being GM-typed like structure names — wins over list
 * position, and no match falls back to the first resident rather than failing to cast.
 *
 * An empty roster casts [fallbackName] as an ephemeral [CastMember] with a null npcId (see
 * [CastMember]) so an event can still name someone in a settlement whose GM never filled the
 * roster; with no fallback either, nobody is cast and the caller drops the slot.
 */
fun castFromRoster(
    roster: List<RosterMember>,
    preferredOccupation: String?,
    fallbackName: String?,
): CastMember? {
    if (roster.isEmpty()) {
        return fallbackName?.let { CastMember(npcId = null, name = it) }
    }
    val wanted = preferredOccupation?.trim()
    val preferred = wanted?.let { occupation ->
        roster.firstOrNull { it.occupation.trim().equals(occupation, ignoreCase = true) }
    }
    val chosen = preferred ?: roster.first()
    return CastMember(npcId = chosen.id, name = chosen.name)
}
