package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.pruneStandingLog
import at.posselt.pfrpg2e.kingdom.data.RawFactionStandingEntry
import at.posselt.pfrpg2e.kingdom.data.RawGroup

/**
 * Append [entry] to this faction's standing log, keeping it capped.
 *
 * Every append goes through here so the cap cannot be forgotten at one of the five sites that write
 * to the log — drift ticks, expeditions, war results, annexation and manual adjustment.
 */
fun appendStandingEntry(
    existing: Array<RawFactionStandingEntry>?,
    entry: RawFactionStandingEntry,
): Array<RawFactionStandingEntry> =
    pruneStandingLog(((existing ?: emptyArray()) + entry).toList()).toTypedArray()

/** Append to [RawGroup.standingLog] in place, capped. */
fun RawGroup.addStandingEntry(entry: RawFactionStandingEntry) {
    standingLog = appendStandingEntry(standingLog, entry)
}
