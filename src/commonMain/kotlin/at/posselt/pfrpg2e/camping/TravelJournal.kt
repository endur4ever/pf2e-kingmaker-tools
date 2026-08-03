package at.posselt.pfrpg2e.camping

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
enum class TravelJournalKind {
    ENTERED_HEX,
    ACTIVITY,
    ENCOUNTER,
    REST,
    MEAL,
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
