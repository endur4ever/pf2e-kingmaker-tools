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
    UNREST("unrest"),
    RP("rp"),
    QUEST("quest"),
    RUMOR("rumor"),
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
    val weight: Int,
    val requiresStructure: String? = null,
    val requiresSeason: String? = null,
    val minPopulation: Int = 0,
    val hookKind: LifeEventHookKind,
    val hookMagnitude: Int = 0,
    val castOccupation: String? = null,
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
    structureNames: Set<String>,
    season: String?,
    population: Int,
): List<LifeEventTemplate> =
    templates.filter { template ->
        structureSatisfied(template.requiresStructure, structureNames) &&
            (template.requiresSeason == null || template.requiresSeason == season) &&
            population >= template.minPopulation
    }

private fun structureSatisfied(required: String?, structureNames: Set<String>): Boolean {
    if (required == null) return true
    val wanted = required.trim()
    return structureNames.any { it.trim().equals(wanted, ignoreCase = true) }
}

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
