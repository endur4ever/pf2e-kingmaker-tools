package at.posselt.pfrpg2e.kingdom.data

import js.objects.Record
import kotlinx.js.JsPlainObject

/**
 * One NPC faction's living agenda (docs/plans/2026-07-09-plan-faction-agenda.md section 2.1).
 * Nests on RawGroup.agenda; null = the faction is idle (pre-migration or GM-cleared).
 */
@JsPlainObject
external interface RawFactionAgenda {
    /** Stable goal identifier from the archetype's pool, e.g. "conquer-neighbor". */
    var goalId: String

    /**
     * GM-typed display override. Blank = derive the label from goalId via the literal-key
     * localizer (composed i18n keys are invisible to the scan, so the key never lives here).
     */
    var goalTitle: String

    /** Progress clock: 0..segments; >= segments completes the goal and draws a new one. */
    var progress: Int
    var segments: Int

    /** Which weighted move table applies: one of FACTION_ARCHETYPE_IDS. */
    var archetype: String

    /** moveId -> turns remaining before that move may fire again. */
    var moveCooldowns: Record<String, Int>?

    /** Last kingdom turn this agenda advanced (idempotency guard). */
    var lastAdvancedTurn: Int?

    /** The rival faction this agenda currently targets, by RawGroup.name. */
    var targetFaction: String?
}

/** One row of the static move catalog (data/faction-agenda-moves/). */
@JsPlainObject
external interface RawFactionAgendaMove {
    var id: String
    var label: String
    var weight: Int
    var cooldownTurns: Int
    /** "self" | "rival" | "ally" | "pcs" | "any-faction". */
    var validTargets: String
    /** "clock" | "standing-delta" | "war-threat" | "quest" | "army". */
    var effect: String
    var effectMagnitude: Int
    var requires: String?
}

/** One archetype's weight table + goal pool (data/faction-agenda-archetypes/). */
@JsPlainObject
external interface RawFactionAgendaArchetype {
    var id: String
    var weights: Record<String, Int>
    var goals: Array<String>
}
