package at.posselt.pfrpg2e.kingdom.data

import at.posselt.pfrpg2e.kingdom.pressure.PayloadKind
import at.posselt.pfrpg2e.kingdom.pressure.Recurrence
import at.posselt.pfrpg2e.kingdom.pressure.ScheduledPressure
import kotlinx.js.JsPlainObject

/**
 * Persisted shape of one scheduled pressure
 * (`docs/plans/2026-07-09-plan-scheduled-pressure-engine.md` §3).
 *
 * Days are WORLD DAY NUMBERS (`worldTimeSeconds.floorDiv(DAY_SECONDS)`), never date strings —
 * Foundry has no date string to store, and parsing dates would drag a clock into the pure engine.
 * [recurrence] and [payloadKind] are strings at this boundary; a value this build does not
 * recognise drops the schedule from evaluation rather than throwing, so one bad row cannot take
 * down the day's whole tick. Typed payload slots, not a JSON blob: a blob no guard can validate
 * and no migration can safely rewrite.
 */
@JsPlainObject
external interface RawScheduledPressure {
    var id: String
    var name: String
    var description: String?
    /** World day number of the first firing. */
    var startDay: Int
    /** none | daily | weekly | monthly */
    var recurrence: String
    /** Stop recurring after this day, inclusive. Null = until resolved or disabled. */
    var endDay: Int?
    /** Last day this schedule fired, so a re-tick over the same span cannot double-fire. */
    var lastFiredDay: Int?
    /** spawnEvent | spawnEncounter | advanceClock | postBeat */
    var payloadKind: String
    var payloadEventId: String?
    var payloadEncounterId: String?
    var payloadClockId: String?
    var payloadBeatText: String?
    /** Increments on each firing; escalating payloads read it. */
    var escalationCount: Int
    /** The most recent firing day the GM has ACTED on (confirmed or dismissed) via the digest
     * card. The double-apply guard: a firing is pending while lastFiredDay > lastHandledDay.
     * Nullable so old rows need no migration -- null reads as "never handled". */
    var lastHandledDay: Int?
    /** none | questCompleted | threatResolved */
    var resolveConditionKind: String?
    var resolveConditionRef: String?
    var active: Boolean
}

/** Null when recurrence or payloadKind is unrecognised — the schedule is skipped, never thrown on. */
fun RawScheduledPressure.toModel(): ScheduledPressure? {
    val recurrence = Recurrence.fromValue(recurrence) ?: return null
    val payloadKind = PayloadKind.fromValue(payloadKind) ?: return null
    return ScheduledPressure(
        id = id,
        name = name,
        startDay = startDay,
        recurrence = recurrence,
        endDay = endDay,
        lastFiredDay = lastFiredDay,
        payloadKind = payloadKind,
        escalationCount = escalationCount,
        active = active,
    )
}
