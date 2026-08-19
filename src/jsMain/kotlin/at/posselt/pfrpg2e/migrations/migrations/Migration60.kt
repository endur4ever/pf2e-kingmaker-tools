package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.camping.CampingData
import at.posselt.pfrpg2e.camping.RawTravelJournalEntry
import com.foundryvtt.core.Game

/**
 * Migration 60 — travel journal.
 *
 * Seeds `travelJournal` to an empty array on existing camping data. The reader
 * ([at.posselt.pfrpg2e.camping.travelJournalList]) already treats absent as empty, so this is
 * belt-and-braces rather than load-bearing: it makes the field visible to anyone inspecting the
 * flag, and keeps the shape consistent with the other array-valued camping fields.
 *
 * The null guard keeps it idempotent — re-running must never erase a journal already being written.
 */
class Migration60 : Migration(60) {

    override suspend fun migrateCamping(game: Game, camping: CampingData) {
        if (camping.travelJournal.unsafeCast<Array<RawTravelJournalEntry>?>() == null) {
            camping.travelJournal = emptyArray()
        }
    }
}
