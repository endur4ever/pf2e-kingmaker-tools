package at.posselt.pfrpg2e.kingdom


import at.posselt.pfrpg2e.kingdom.data.RawExpeditionChronicleEntry
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
    pressurePerTurn: Int? = null,
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
    pressurePerTurn = pressurePerTurn,
    notes = notes,
    level = level,
    size = size,
    ruinCorruption = ruinCorruption,
    ruinCrime = ruinCrime,
    ruinDecay = ruinDecay,
    ruinStrife = ruinStrife,
)

/**
 * Build a one-line gazette summary for the monthly kingdom journal.
 * [expeditionChronicle] and [turn] are used to include expeditions that were
 * applied during this turn (they resolve on the DAILY tick, not the turn tick).
 */
fun formatTurnGazette(
    activities: List<String>,
    sizeChange: Int,
    currentSize: Int,
    caravanEvents: List<CaravanEvent> = emptyList(),
    shipmentEvents: List<CaravanEvent> = emptyList(),
    campaignClocks: List<String> = emptyList(),
    tributeRp: Int = 0,
    expeditionChronicle: List<RawExpeditionChronicleEntry> = emptyList(),
    turn: Int = 0,
): String? {
    val gazetteEvents = mutableListOf<String>()

    if (tributeRp > 0) {
        gazetteEvents.add("Tribute: +$tributeRp RP collected from vassal states")
    }

    if (activities.isNotEmpty()) {
        gazetteEvents.add("Activities: " + activities.joinToString(", "))
    }

    if (sizeChange > 0) {
        gazetteEvents.add("Expansion: Claimed $sizeChange hex(es) (Size: $currentSize)")
    }

    val caravanGazetteList = caravanEvents.map { event ->
        when (event.kind) {
            CaravanEventKind.DELIVERED -> {
                val reward = if (event.bonusResourceDice > 0) " (+${event.bonusResourceDice} RD)" else " (delivered ${event.deliveredAmount})"
                "Caravan delivered: ${event.summary}$reward"
            }
            CaravanEventKind.RAIDED -> "Caravan raided: ${event.summary} (lost ${event.cargoLost})"
            CaravanEventKind.LOST -> "Caravan lost: ${event.summary}"
        }
    }
    if (caravanGazetteList.isNotEmpty()) {
        gazetteEvents.add("Caravans: " + caravanGazetteList.joinToString("; "))
    }

    val shipmentGazetteList = shipmentEvents.map { event ->
        when (event.kind) {
            CaravanEventKind.DELIVERED -> "Shipment arrived: ${event.summary}"
            CaravanEventKind.RAIDED -> "Shipment raided: ${event.summary} (lost ${event.cargoLost})"
            CaravanEventKind.LOST -> "Shipment lost: ${event.summary}"
        }
    }
    if (shipmentGazetteList.isNotEmpty()) {
        gazetteEvents.add("Shipments: " + shipmentGazetteList.joinToString("; "))
    }

    if (campaignClocks.isNotEmpty()) {
        gazetteEvents.add("Campaign Clocks: " + campaignClocks.joinToString(", "))
    }

    // Expeditions section: filter chronicle entries for this turn
    val turnExpeditions = expeditionChronicle.filter { it.turn == turn }
    if (turnExpeditions.isNotEmpty()) {
        val expeditionLines = turnExpeditions.map { entry ->
            val outcomeLabel = when (entry.outcomeDegree) {
                "criticalSuccess" -> "🏆"
                "success" -> "✅"
                "failure" -> "❌"
                "criticalFailure" -> "💀"
                else -> entry.outcomeDegree
            }
            val lootSuffix = if (entry.lootRp > 0) " (+${entry.lootRp} RP)" else ""
            val factionSuffix = if (entry.factionStandingDelta != 0 && entry.targetFactionName != null)
                " (${entry.targetFactionName}: ${if (entry.factionStandingDelta > 0) "+" else ""}${entry.factionStandingDelta})" else ""
            "$outcomeLabel ${entry.title} — ${entry.companionNames}$lootSuffix$factionSuffix"
        }
        // Whole-gazette localization is deferred (all other lines are hardcoded English + emoji);
        // localizing only this prefix is inconsistent and t() does not resolve in the test harness.
        gazetteEvents.add("Expeditions: " + expeditionLines.joinToString("; "))
    }

    return if (gazetteEvents.isNotEmpty()) gazetteEvents.joinToString(" | ") else null
}

/**
 * A GM-facing recap of the most recently completed turn: the change vs the prior turn for the key
 * economy stats plus the gazette notes. Pure so it is unit-testable; [postLastTurnRecap]
 * (TurnWizardApplication) renders it into the last-turn-recap chat card and GM-whispers it.
 */
data class LastTurnRecap(
    val turn: Int,
    val fameDelta: Int,
    val fameNow: Int,
    val rpDelta: Int,
    val rpNow: Int,
    val unrestDelta: Int,
    val unrestNow: Int,
    val hasWarPressure: Boolean,
    val warPressureDelta: Int,
    val warPressureNow: Int,
    val xpAwarded: Int?,
    val notes: String?,
)

/**
 * Builds a recap of the latest turn record vs the one before it. Deltas are 0 when there is no
 * prior record (the first recorded turn). Returns null when there is no history to recap.
 */
fun computeLastTurnRecap(history: Array<RawTurnRecord>?): LastTurnRecap? {
    val latest = history?.lastOrNull() ?: return null
    val previous = history.getOrNull(history.size - 2)
    fun delta(current: Int, prior: Int?): Int = current - (prior ?: current)
    val warPressureNow = latest.warPressure
    return LastTurnRecap(
        turn = latest.turn,
        fameDelta = delta(latest.fame, previous?.fame),
        fameNow = latest.fame,
        rpDelta = delta(latest.resourcePoints, previous?.resourcePoints),
        rpNow = latest.resourcePoints,
        unrestDelta = delta(latest.unrest, previous?.unrest),
        unrestNow = latest.unrest,
        hasWarPressure = warPressureNow != null,
        warPressureDelta = if (warPressureNow != null) delta(warPressureNow, previous?.warPressure) else 0,
        warPressureNow = warPressureNow ?: 0,
        xpAwarded = latest.xpAwarded,
        notes = latest.notes,
    )
}

