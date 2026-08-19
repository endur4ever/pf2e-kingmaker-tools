package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game

/**
 * Recording seams for the travel journal.
 *
 * Two shapes, because the call sites differ:
 * - [appendTravelEntry] mutates camping data the caller is already going to save (daily
 *   preparations, meal commit) — adding a second write there would race the caller's own.
 * - [recordTravelEntry] does its own read-modify-write, for sites holding only the actor.
 *
 * See card t_579e9e67.
 */

/** Append [entry] to camping data the CALLER will persist. Does not save. */
fun CampingData.appendTravelEntry(entry: TravelJournalEntry) {
    travelJournal = appendTravelJournalEntry(travelJournalList(), entry)
        .map { it.toRaw() }
        .toTypedArray()
}

/** Append [entry] and persist immediately. For callers that hold no camping instance of their own. */
suspend fun CampingActor.recordTravelEntry(entry: TravelJournalEntry) {
    val camping = getCamping() ?: return
    camping.appendTravelEntry(entry)
    setCamping(camping)
}

/**
 * Record that the party entered [hexKey], unless they were already there.
 *
 * Camping data is an actor flag the players own, so an unguarded write from a player client
 * SUCCEEDS silently rather than erroring — and because reads are deep clones, two clients appending
 * concurrently lose one side's entries. Movement and encounters are therefore GM-only writes.
 */
suspend fun CampingActor.recordHexEntry(game: Game, hexKey: String) {
    if (!game.user.isGM) return
    val camping = getCamping() ?: return
    if (!shouldRecordHexEntry(camping.travelJournalList(), hexKey)) return
    camping.appendTravelEntry(
        TravelJournalEntry(
            worldDate = game.travelJournalWorldDate(),
            kind = TravelJournalKind.ENTERED_HEX,
            hexKey = hexKey,
        )
    )
    setCamping(camping)
}

/** Record an encounter rolled at [hexKey]. GM-only, for the same reason as [recordHexEntry]. */
suspend fun CampingActor.recordEncounter(game: Game, hexKey: String?, note: String?) {
    if (!game.user.isGM) return
    recordTravelEntry(
        TravelJournalEntry(
            worldDate = game.travelJournalWorldDate(),
            kind = TravelJournalKind.ENCOUNTER,
            hexKey = hexKey,
            note = note,
        )
    )
}

/**
 * Entry for a hexploration activity spend. Not persisted here — the caller saves.
 *
 * [activityType] is the i18n key of the activity, resolved for display at render time so a journal
 * written in one language still reads correctly in another.
 */
fun hexplorationActivityEntry(game: Game, activityType: String, hexKey: String?): TravelJournalEntry =
    TravelJournalEntry(
        worldDate = game.travelJournalWorldDate(),
        kind = TravelJournalKind.ACTIVITY,
        hexKey = hexKey,
        activityType = activityType,
    )

/**
 * The hexploration activities a party can spend an activity on (Player Core / Kingmaker).
 * Stored as i18n keys so a journal written in one language still reads correctly in another;
 * the first entry is the default when nothing is selected.
 */
val HEXPLORATION_ACTIVITY_TYPES = listOf(
    "camping.hexActivity.travel",
    "camping.hexActivity.reconnoiter",
    "camping.hexActivity.mapTheArea",
    "camping.hexActivity.fortifyCamp",
)

/** Entry for a completed rest. Not persisted here — daily preparations saves. */
fun restEntry(game: Game): TravelJournalEntry =
    TravelJournalEntry(
        worldDate = game.travelJournalWorldDate(),
        kind = TravelJournalKind.REST,
    )

/** Entry for a notable (critical) cooked meal. Not persisted here — the caller saves. */
fun mealEntry(game: Game, recipeName: String, note: String?): TravelJournalEntry =
    TravelJournalEntry(
        worldDate = game.travelJournalWorldDate(),
        kind = TravelJournalKind.MEAL,
        activityType = recipeName,
        note = note,
    )
