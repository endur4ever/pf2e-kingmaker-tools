package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord

/**
 * Pure logic for turn-history recording (gap analysis item 2).
 *
 * [appendTurnRecord] appends a new record to the history array, keeping the
 * newest entry last and dropping the oldest once the cap is exceeded.
 *
 * [buildTurnRecord] snapshots post-tick kingdom values + clock event labels
 * into a [RawTurnRecord].
 */

fun appendTurnRecord(
    history: Array<RawTurnRecord>?,
    record: RawTurnRecord,
    cap: Int = 100,
): Array<RawTurnRecord> {
    val base = history ?: emptyArray()
    val combined = base + record
    return if (combined.size > cap) combined.sliceArray(combined.size - cap until combined.size) else combined
}

fun buildTurnRecord(
    turn: Int,
    timestamp: String,
    fame: Int,
    resourcePoints: Int,
    consumption: Int,
    unrest: Int,
    xpAwarded: Int? = null,
    clockEvents: Array<String>? = null,
    warPressure: Int? = null,
    notes: String? = null,
    level: Int? = null,
    size: Int? = null,
    ruinCorruption: Int? = null,
    ruinCrime: Int? = null,
    ruinDecay: Int? = null,
    ruinStrife: Int? = null,
): RawTurnRecord = RawTurnRecord(
    turn = turn,
    timestamp = timestamp,
    fame = fame,
    resourcePoints = resourcePoints,
    consumption = consumption,
    unrest = unrest,
    xpAwarded = xpAwarded,
    clockEvents = clockEvents,
    warPressure = warPressure,
    notes = notes,
    level = level,
    size = size,
    ruinCorruption = ruinCorruption,
    ruinCrime = ruinCrime,
    ruinDecay = ruinDecay,
    ruinStrife = ruinStrife,
)
