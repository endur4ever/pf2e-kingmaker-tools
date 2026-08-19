package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.utils.getPF2EWorldTime
import com.foundryvtt.core.Game
import kotlinx.js.JsPlainObject

/**
 * Persistence bridge for the travel journal.
 *
 * [TravelJournalEntry] is a commonMain data class and cannot go into a Foundry flag, so entries are
 * stored as plain JS objects and converted at the boundary — the same Raw/model split the encounter
 * curator uses for rumors.
 *
 * See card t_579e9e67.
 */
@JsPlainObject
external interface RawTravelJournalEntry {
    var worldDate: String
    var kind: String
    var hexKey: String?
    var activityType: String?
    var note: String?
}

/**
 * Parse one stored entry, or null when its `kind` is not one this build understands.
 *
 * Dropping an unparseable entry rather than throwing keeps a journal written by a newer version (or
 * hand-edited) from breaking the whole camping sheet — the journal is a record, never a dependency.
 */
fun RawTravelJournalEntry.toModel(): TravelJournalEntry? =
    fromCamelCase<TravelJournalKind>(kind)?.let { parsed ->
        TravelJournalEntry(
            worldDate = worldDate,
            kind = parsed,
            hexKey = hexKey,
            activityType = activityType,
            note = note,
        )
    }

fun TravelJournalEntry.toRaw(): RawTravelJournalEntry =
    RawTravelJournalEntry(
        worldDate = worldDate,
        kind = kind.value,
        hexKey = hexKey,
        activityType = activityType,
        note = note,
    )

/** The stored journal as models, oldest first. Empty when the field has never been written. */
fun CampingData.travelJournalList(): List<TravelJournalEntry> =
    travelJournal?.mapNotNull { it.toModel() } ?: emptyList()

/**
 * The world date to stamp on a journal entry.
 *
 * Reads the PF2e system's own world clock rather than any calendar module, so a misconfigured
 * Seasons & Stars calendar — which is what makes `game.time.advance` throw — cannot stop an entry
 * being recorded. Falls back to an empty string rather than propagating a failure into the caller,
 * because losing a date is a far better outcome than abandoning a rest or a route mid-way.
 */
fun Game.travelJournalWorldDate(): String =
    runCatching { getPF2EWorldTime().date.toString() }.getOrDefault("")

/** One rendered travel-journal row (newest first in the sheet context). */
@JsPlainObject
external interface TravelJournalRow {
    val worldDate: String
    /** Localized kind name, e.g. "Entered Hex". */
    val kindLabel: String
    /** Raw kind value, for per-kind icon/colour in CSS. */
    val kindValue: String
    /** Pre-joined detail column: activity, hex coordinate and note. */
    val detail: String
}

/** A selectable hexploration activity type. */
@JsPlainObject
external interface TravelJournalOption {
    val value: String
    val label: String
}
