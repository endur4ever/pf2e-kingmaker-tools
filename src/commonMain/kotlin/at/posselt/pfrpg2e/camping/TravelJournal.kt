package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.ValueEnum
import at.posselt.pfrpg2e.localization.Translatable
import at.posselt.pfrpg2e.toCamelCase

/**
 * Pure travel-journal core: a capped, append-only trail of what happened while exploring — hexes
 * entered, hexploration activities performed, encounters, rests, meals — so a campaign's exploration
 * leaves a readable record instead of vanishing. This is the deterministic append/prune + tally core;
 * the persisted `travelJournal` field + Migration, the recording call sites, and the display section
 * are the deferred jsMain wiring.
 *
 * (Named `TravelJournal*` to stay clear of any in-flight `TravelLog` WIP; the concept is the same.)
 *
 * See card t_579e9e67.
 */

/** What a journal entry records. */
enum class TravelJournalKind : Translatable, ValueEnum {
    ENTERED_HEX,
    ACTIVITY,
    ENCOUNTER,
    REST,
    MEAL;

    override val value: String
        get() = toCamelCase()
    override val i18nKey: String
        get() = "travelJournalKind.$value"
}

/** One line of the travel journal. */
data class TravelJournalEntry(
    /** World date (calendar ISO string) the entry was recorded. */
    val worldDate: String,
    val kind: TravelJournalKind,
    /** Hex key for ENTERED_HEX / ENCOUNTER entries. */
    val hexKey: String? = null,
    /** Activity name for ACTIVITY entries (Travel, Reconnoiter, Map the Area, Fortify Camp). */
    val activityType: String? = null,
    val note: String? = null,
)

/** Maximum journal entries retained before the oldest are pruned. */
const val MAX_TRAVEL_JOURNAL_ENTRIES = 200

/**
 * Append [entry] (the newest event) and keep only the most recent [cap] entries, dropping the oldest
 * from the front. Order is preserved oldest -> newest. A non-positive [cap] yields an empty journal.
 */
fun appendTravelJournalEntry(
    journal: List<TravelJournalEntry>,
    entry: TravelJournalEntry,
    cap: Int = MAX_TRAVEL_JOURNAL_ENTRIES,
): List<TravelJournalEntry> {
    if (cap <= 0) return emptyList()
    val appended = journal + entry
    return if (appended.size <= cap) appended else appended.takeLast(cap)
}

/** Count of entries per kind (every kind present, 0 when none) — for the journal header summary. */
fun travelJournalKindCounts(journal: List<TravelJournalEntry>): Map<TravelJournalKind, Int> =
    TravelJournalKind.entries.associateWith { kind -> journal.count { it.kind == kind } }

/** Distinct hex keys entered, in first-seen order — the trail of where the party has been. */
fun hexesVisited(journal: List<TravelJournalEntry>): List<String> =
    journal.filter { it.kind == TravelJournalKind.ENTERED_HEX }
        .mapNotNull { it.hexKey }
        .distinct()

/**
 * Whether entering [hexKey] is worth a new ENTERED_HEX entry.
 *
 * False when the most recent hex the party entered is already this one. Token-move hooks fire for
 * nudges within the same hex and for programmatic moves that may re-fire, so without this the trail
 * fills with consecutive duplicates of wherever the party is standing.
 *
 * Compares against the last ENTERED_HEX entry rather than the last entry of any kind, so an
 * encounter or a rest recorded in between does not make a stationary party look like it moved.
 * Genuine backtracking still records: A -> B -> A yields three entries, because only *consecutive*
 * repeats are suppressed.
 */
fun shouldRecordHexEntry(journal: List<TravelJournalEntry>, hexKey: String): Boolean =
    journal.lastOrNull { it.kind == TravelJournalKind.ENTERED_HEX }?.hexKey != hexKey

/**
 * One-line summary of a day's travel, for a calendar note.
 *
 * Takes the localized kind labels rather than resolving them, so this stays pure and the caller
 * controls language. Kinds with no entries are omitted — a note reading "Rest: 1" is useful, one
 * reading "Entered Hex: 0, Activity: 0, Encounter: 0, Rest: 1, Meal: 0" is not. Returns an empty
 * string for an empty journal so the caller can skip the note entirely.
 */
fun travelJournalDaySummary(
    journal: List<TravelJournalEntry>,
    kindLabels: Map<TravelJournalKind, String>,
): String {
    if (journal.isEmpty()) return ""
    val counts = travelJournalKindCounts(journal).filterValues { it > 0 }
    val parts = counts.entries.map { (kind, count) -> "${kindLabels[kind] ?: kind.name}: $count" }
    val visited = hexesVisited(journal)
    return if (visited.isEmpty()) {
        parts.joinToString(", ")
    } else {
        (parts + "Hexes: ${visited.size}").joinToString(", ")
    }
}
