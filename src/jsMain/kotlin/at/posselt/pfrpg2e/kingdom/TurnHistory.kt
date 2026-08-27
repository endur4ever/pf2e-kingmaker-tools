package at.posselt.pfrpg2e.kingdom


import at.posselt.pfrpg2e.kingdom.data.RawExpeditionChronicleEntry
import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord
import com.foundryvtt.core.AnyObject

/**
 * How many turn records are retained. The live append drops the oldest past this, and the workbook
 * importer reports how many its backfill will trim — both must mean the same number, so it lives
 * here rather than as a default-argument literal in each place.
 */
const val TURN_HISTORY_CAP = 100

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
    cap: Int = TURN_HISTORY_CAP,
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
    playerNotes: String? = null,
    level: Int? = null,
    size: Int? = null,
    ruinCorruption: Int? = null,
    ruinCrime: Int? = null,
    ruinDecay: Int? = null,
    ruinStrife: Int? = null,
    closedVoteIds: Array<String>? = null,
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
    closedVoteIds = closedVoteIds,
    playerNotes = playerNotes,
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
    battleDefeats: List<String> = emptyList(),
    turn: Int = 0,
    /** Pre-localized rival headlines (rival-realms SS4.4); the caller localizes because the
     *  headline template is picked from an enum pool, not from a single key. */
    rivalMoves: List<String> = emptyList(),
    localize: (key: String, data: AnyObject) -> String = ::defaultLocalize,
): String? {
    val gazetteEvents = mutableListOf<String>()

    if (tributeRp > 0) {
        val data = js("{}")
        data.tributeRp = tributeRp
        gazetteEvents.add(localize("kingdom.turnGazette.tribute", data.unsafeCast<AnyObject>()))
    }

    if (rivalMoves.isNotEmpty()) {
        val data = js("{}")
        data.list = rivalMoves.joinToString("; ")
        gazetteEvents.add(localize("kingdom.turnGazette.rivalMove", data.unsafeCast<AnyObject>()))
    }

    if (activities.isNotEmpty()) {
        val data = js("{}")
        data.activities = activities.joinToString(", ")
        gazetteEvents.add(localize("kingdom.turnGazette.activities", data.unsafeCast<AnyObject>()))
    }

    // Losing a war battle now leaves a trace in Recent Turns; previously a defeat was archived
    // silently and the turn record read exactly as if no battle had been fought.
    if (battleDefeats.isNotEmpty()) {
        val data = js("{}")
        data.battles = battleDefeats.joinToString(", ")
        gazetteEvents.add(localize("kingdom.turnGazette.battleDefeats", data.unsafeCast<AnyObject>()))
    }

    if (sizeChange > 0) {
        val data = js("{}")
        data.sizeChange = sizeChange
        data.currentSize = currentSize
        gazetteEvents.add(localize("kingdom.turnGazette.expansion", data.unsafeCast<AnyObject>()))
    }

    val caravanGazetteList = caravanEvents.map { event ->
        val data = js("{}")
        data.summary = event.summary
        when (event.kind) {
            CaravanEventKind.DELIVERED -> {
                if (event.bonusResourceDice > 0) {
                    data.bonusResourceDice = event.bonusResourceDice
                    localize("kingdom.turnGazette.caravanDeliveredRd", data.unsafeCast<AnyObject>())
                } else {
                    data.deliveredAmount = event.deliveredAmount
                    localize("kingdom.turnGazette.caravanDeliveredAmount", data.unsafeCast<AnyObject>())
                }
            }
            CaravanEventKind.RAIDED -> {
                data.cargoLost = event.cargoLost
                localize("kingdom.turnGazette.caravanRaided", data.unsafeCast<AnyObject>())
            }
            CaravanEventKind.LOST -> {
                localize("kingdom.turnGazette.caravanLost", data.unsafeCast<AnyObject>())
            }
        }
    }
    if (caravanGazetteList.isNotEmpty()) {
        val data = js("{}")
        data.list = caravanGazetteList.joinToString("; ")
        gazetteEvents.add(localize("kingdom.turnGazette.caravans", data.unsafeCast<AnyObject>()))
    }

    val shipmentGazetteList = shipmentEvents.map { event ->
        val data = js("{}")
        data.summary = event.summary
        when (event.kind) {
            CaravanEventKind.DELIVERED -> {
                localize("kingdom.turnGazette.shipmentArrived", data.unsafeCast<AnyObject>())
            }
            CaravanEventKind.RAIDED -> {
                data.cargoLost = event.cargoLost
                localize("kingdom.turnGazette.shipmentRaided", data.unsafeCast<AnyObject>())
            }
            CaravanEventKind.LOST -> {
                localize("kingdom.turnGazette.shipmentLost", data.unsafeCast<AnyObject>())
            }
        }
    }
    if (shipmentGazetteList.isNotEmpty()) {
        val data = js("{}")
        data.list = shipmentGazetteList.joinToString("; ")
        gazetteEvents.add(localize("kingdom.turnGazette.shipments", data.unsafeCast<AnyObject>()))
    }

    if (campaignClocks.isNotEmpty()) {
        val data = js("{}")
        data.clocks = campaignClocks.joinToString(", ")
        gazetteEvents.add(localize("kingdom.turnGazette.campaignClocks", data.unsafeCast<AnyObject>()))
    }

    // Expeditions section: filter chronicle entries for this turn
    val turnExpeditions = expeditionChronicle.filter { it.turn == turn }
    if (turnExpeditions.isNotEmpty()) {
        val expeditionLines = turnExpeditions.map { entry ->
            val data = js("{}")
            data.title = entry.title
            data.companionNames = entry.companionNames
            data.loot = if (entry.lootRp > 0) " (+${entry.lootRp} RP)" else ""
            data.faction = if (entry.factionStandingDelta != 0 && entry.targetFactionName != null)
                " (${entry.targetFactionName}: ${if (entry.factionStandingDelta > 0) "+" else ""}${entry.factionStandingDelta})" else ""

            when (entry.outcomeDegree) {
                "criticalSuccess" -> localize("kingdom.turnGazette.expeditionCriticalSuccess", data.unsafeCast<AnyObject>())
                "success" -> localize("kingdom.turnGazette.expeditionSuccess", data.unsafeCast<AnyObject>())
                "failure" -> localize("kingdom.turnGazette.expeditionFailure", data.unsafeCast<AnyObject>())
                "criticalFailure" -> localize("kingdom.turnGazette.expeditionCriticalFailure", data.unsafeCast<AnyObject>())
                else -> {
                    data.outcomeLabel = entry.outcomeDegree
                    localize("kingdom.turnGazette.expeditionOther", data.unsafeCast<AnyObject>())
                }
            }
        }
        val data = js("{}")
        data.list = expeditionLines.joinToString("; ")
        gazetteEvents.add(localize("kingdom.turnGazette.expeditions", data.unsafeCast<AnyObject>()))
    }

    return if (gazetteEvents.isNotEmpty()) gazetteEvents.joinToString(" | ") else null
}

fun defaultLocalize(key: String, data: AnyObject): String {
    val dyn = data.asDynamic()
    return when (key) {
        "kingdom.turnGazette.tribute" -> "Tribute: +${dyn.tributeRp} RP collected from vassal states"
        "kingdom.turnGazette.activities" -> "Activities: ${dyn.activities}"
        "kingdom.turnGazette.battleDefeats" -> "Defeated in battle: ${dyn.battles}"
        "kingdom.turnGazette.expansion" -> "Expansion: Claimed ${dyn.sizeChange} hex(es) (Size: ${dyn.currentSize})"
        "kingdom.turnGazette.caravanDeliveredRd" -> "Caravan delivered: ${dyn.summary} (+${dyn.bonusResourceDice} RD)"
        "kingdom.turnGazette.caravanDeliveredAmount" -> "Caravan delivered: ${dyn.summary} (delivered ${dyn.deliveredAmount})"
        "kingdom.turnGazette.caravanRaided" -> "Caravan raided: ${dyn.summary} (lost ${dyn.cargoLost})"
        "kingdom.turnGazette.caravanLost" -> "Caravan lost: ${dyn.summary}"
        "kingdom.turnGazette.caravans" -> "Caravans: ${dyn.list}"
        "kingdom.turnGazette.shipmentArrived" -> "Shipment arrived: ${dyn.summary}"
        "kingdom.turnGazette.shipmentRaided" -> "Shipment raided: ${dyn.summary} (lost ${dyn.cargoLost})"
        "kingdom.turnGazette.shipmentLost" -> "Shipment lost: ${dyn.summary}"
        "kingdom.turnGazette.shipments" -> "Shipments: ${dyn.list}"
        "kingdom.turnGazette.campaignClocks" -> "Campaign Clocks: ${dyn.clocks}"
        "kingdom.turnGazette.expeditionCriticalSuccess" -> "🏆 ${dyn.title} — ${dyn.companionNames}${dyn.loot}${dyn.faction}"
        "kingdom.turnGazette.expeditionSuccess" -> "✅ ${dyn.title} — ${dyn.companionNames}${dyn.loot}${dyn.faction}"
        "kingdom.turnGazette.expeditionFailure" -> "❌ ${dyn.title} — ${dyn.companionNames}${dyn.loot}${dyn.faction}"
        "kingdom.turnGazette.expeditionCriticalFailure" -> "💀 ${dyn.title} — ${dyn.companionNames}${dyn.loot}${dyn.faction}"
        "kingdom.turnGazette.expeditionOther" -> "${dyn.outcomeLabel} ${dyn.title} — ${dyn.companionNames}${dyn.loot}${dyn.faction}"
        "kingdom.turnGazette.expeditions" -> "Expeditions: ${dyn.list}"
        "kingdom.turnGazette.rivalMove" -> "Rival Realms: ${dyn.list}"
        else -> key
    }
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

