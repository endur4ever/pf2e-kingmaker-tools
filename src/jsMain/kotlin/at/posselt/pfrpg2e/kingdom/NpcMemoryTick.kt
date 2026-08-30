package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord
import at.posselt.pfrpg2e.kingdom.npcmemory.AttitudeBand
import at.posselt.pfrpg2e.kingdom.npcmemory.KingdomTurnFacts
import at.posselt.pfrpg2e.kingdom.npcmemory.MemoryRule
import at.posselt.pfrpg2e.kingdom.npcmemory.MemoryTriggerKind
import at.posselt.pfrpg2e.kingdom.npcmemory.MEMORY_LOG_CAP
import at.posselt.pfrpg2e.kingdom.npcmemory.NpcMemoryEntry
import at.posselt.pfrpg2e.kingdom.npcmemory.appendMemoryEntry
import at.posselt.pfrpg2e.kingdom.npcmemory.clampAttitude
import at.posselt.pfrpg2e.kingdom.npcmemory.crossedBand
import at.posselt.pfrpg2e.kingdom.npcmemory.decayAttitude
import at.posselt.pfrpg2e.kingdom.npcmemory.evaluateMemoryRules
import at.posselt.pfrpg2e.kingdom.structures.RawNpcMemoryEntry
import at.posselt.pfrpg2e.utils.t
import kotlin.js.jsTypeOf

/**
 * End Turn evaluation of the NPC memory ledger (plan section "Phase 3 -- tick"): tracked roster
 * residents remember what the kingdom did, expressed as deltas between the LAST TWO turn
 * records -- the evaluation runs right after the new record is appended, so both snapshots come
 * from history and preview never sees a half-written turn.
 */

private fun dynInt(v: dynamic): Int? = if (jsTypeOf(v) == "number") v.unsafeCast<Double>().toInt() else null
private fun dynStr(v: dynamic): String? = if (jsTypeOf(v) == "string") v.unsafeCast<String>() else null

@JsModule("./npc-memory-rules.json")
private external val npcMemoryRuleData: Array<dynamic>

/** The bundled rule catalog; a rule this build's trigger vocabulary cannot name is skipped. */
fun npcMemoryRules(): List<MemoryRule> =
    npcMemoryRuleData.mapNotNull { raw ->
        val id = dynStr(raw?.id) ?: return@mapNotNull null
        val trigger = MemoryTriggerKind.fromValue(dynStr(raw?.trigger)) ?: return@mapNotNull null
        val deltas = mutableMapOf<String, Int>()
        val rawDeltas = raw?.deltas
        if (rawDeltas != null) {
            js.objects.Object.keys(rawDeltas.unsafeCast<Any>()).forEach { key ->
                dynInt(rawDeltas[key])?.let { deltas[key] = it }
            }
        }
        MemoryRule(
            id = id,
            trigger = trigger,
            threshold = dynInt(raw?.threshold) ?: 0,
            ref = dynStr(raw?.ref),
            deltas = deltas,
            defaultDelta = dynInt(raw?.defaultDelta) ?: 0,
            cooldownTurns = dynInt(raw?.cooldownTurns) ?: 0,
        )
    }

/** Flattens one turn record into the pure engine's snapshot shape. */
fun RawTurnRecord.toTurnFacts(
    fameMax: Int,
    shipmentOutcomes: List<String>,
    milestonesEarnedThisTurn: Int,
): KingdomTurnFacts = KingdomTurnFacts(
    turn = turn,
    unrest = unrest,
    ruinCorruption = ruinCorruption ?: 0,
    ruinCrime = ruinCrime ?: 0,
    ruinDecay = ruinDecay ?: 0,
    ruinStrife = ruinStrife ?: 0,
    size = size ?: 0,
    level = level ?: 1,
    fame = fame,
    fameMax = fameMax,
    warPressure = warPressure,
    consumption = consumption,
    resourcePoints = resourcePoints,
    clockEventIds = clockEvents?.toSet() ?: emptySet(),
    shipmentOutcomes = shipmentOutcomes,
    milestonesEarnedThisTurn = milestonesEarnedThisTurn,
)

/** Kingdom-wide tracked count: the MAX_TRACKED_NPCS cap spans every settlement's roster. */
fun countTrackedNpcs(kingdom: KingdomData): Int =
    kingdom.settlements.unsafeCast<Array<at.posselt.pfrpg2e.kingdom.structures.RawSettlement>?>()
        ?.sumOf { s -> s.populationRoster?.npcs?.count { it.memoryTracked == true } ?: 0 } ?: 0

/** Names of every tracked resident, for the cap-refused message. */
fun trackedNpcNames(kingdom: KingdomData): List<String> =
    kingdom.settlements.unsafeCast<Array<at.posselt.pfrpg2e.kingdom.structures.RawSettlement>?>()
        ?.flatMap { s -> s.populationRoster?.npcs?.filter { it.memoryTracked == true }?.map { it.name } ?: emptyList() }
        ?: emptyList()

/** One band crossing, carried to the whispered offer card. */
data class NpcBandCrossing(
    val npcId: String,
    val npcName: String,
    val occupation: String,
    val settlementSceneId: String,
    val band: AttitudeBand,
    val score: Int,
    val firedRuleIds: List<String>,
)

data class NpcMemoryTickOutcome(
    val memoriesWritten: Int,
    val crossings: List<NpcBandCrossing>,
)

/**
 * Evaluates every tracked resident against the rule catalog and WRITES memory + attitude onto
 * the roster rows in place -- the caller persists via its single setKingdom. Cooldown state is
 * derived from each NPC's own log (last entry per rule), so there is nothing extra to store or
 * to wipe. Decay touches only NPCs who formed no memory this turn (plan section 5).
 */
fun evaluateNpcMemoriesForTurn(
    kingdom: KingdomData,
    currentTurn: Int,
    rules: List<MemoryRule>,
    fameMax: Int,
): NpcMemoryTickOutcome {
    val history = kingdom.turnHistory ?: return NpcMemoryTickOutcome(0, emptyList())
    val currRecord = history.find { it.turn == currentTurn } ?: return NpcMemoryTickOutcome(0, emptyList())
    val prevRecord = history.filter { it.turn < currentTurn }.maxByOrNull { it.turn }
    val shipmentsThisTurn = (kingdom.shipmentHistory ?: emptyArray())
        .filter { it.turn == currentTurn }
        .map { it.outcome }
    // deeds-chronicle's awardedOnTurn IS the per-turn capture this needed: a milestone awarded
    // on this turn is a milestone earned this turn. Hardcoding 0 left the rule permanently inert.
    // milestones is typed non-null but is undefined on kingdoms predating it (and in fixtures),
    // so the cast makes the guard a real runtime check rather than a compile-time formality
    val milestonesThisTurn = kingdom.milestones
        .unsafeCast<Array<at.posselt.pfrpg2e.kingdom.data.MilestoneChoice>?>()
        ?.count { it.completed && it.awardedOnTurn == currentTurn } ?: 0
    val curr = currRecord.toTurnFacts(fameMax, shipmentsThisTurn, milestonesEarnedThisTurn = milestonesThisTurn)
    val prev = prevRecord?.toTurnFacts(fameMax, emptyList(), 0)
    var written = 0
    val crossings = mutableListOf<NpcBandCrossing>()
    val settlements = kingdom.settlements.unsafeCast<Array<at.posselt.pfrpg2e.kingdom.structures.RawSettlement>?>()
        ?: return NpcMemoryTickOutcome(0, emptyList())
    settlements.forEach { settlement ->
        settlement.populationRoster?.npcs?.forEach npc@{ npc ->
            if (npc.memoryTracked != true) return@npc
            val oldScore = npc.attitudeScore ?: 0
            val log = npc.memoryLog ?: emptyArray()
            val firings = if (prev == null) emptyList() else {
                val lastFired = log.groupBy { it.ruleId }.mapValues { (_, rows) -> rows.maxOf { it.turn } }
                evaluateMemoryRules(rules, prev, curr, lastFired, npc.occupation)
            }
            val newScore: Int
            if (firings.isEmpty()) {
                newScore = decayAttitude(oldScore, formedMemoryThisTurn = false)
            } else {
                var pureLog = log.map { NpcMemoryEntry(it.ruleId, it.turn, it.delta, it.subject) }
                firings.forEach { firing ->
                    pureLog = appendMemoryEntry(
                        pureLog,
                        NpcMemoryEntry(ruleId = firing.rule.id, turn = currentTurn, delta = firing.delta),
                        cap = MEMORY_LOG_CAP,
                    )
                }
                npc.memoryLog = pureLog.map {
                    RawNpcMemoryEntry(ruleId = it.ruleId, turn = it.turn, delta = it.delta, subject = it.subject)
                }.toTypedArray()
                newScore = clampAttitude(oldScore + firings.sumOf { it.delta })
                written += firings.size
            }
            npc.attitudeScore = newScore
            crossedBand(oldScore, newScore)?.let { band ->
                crossings += NpcBandCrossing(
                    npcId = npc.id,
                    npcName = npc.name,
                    occupation = npc.occupation,
                    settlementSceneId = settlement.sceneId,
                    band = band,
                    score = newScore,
                    firedRuleIds = firings.map { it.rule.id },
                )
            }
        }
    }
    return NpcMemoryTickOutcome(written, crossings)
}

/** Literal keys only: the composed "npcMemory.$ruleId.entry" form is invisible to the i18n scan. */
fun localizeMemoryEntry(ruleId: String): String = when (ruleId) {
    "unrest-spike" -> t("kingdom.npcMemory.unrestSpike.entry")
    "unrest-calmed" -> t("kingdom.npcMemory.unrestCalmed.entry")
    "decay-worsens" -> t("kingdom.npcMemory.decayWorsens.entry")
    "crime-worsens" -> t("kingdom.npcMemory.crimeWorsens.entry")
    "corruption-worsens" -> t("kingdom.npcMemory.corruptionWorsens.entry")
    "strife-worsens" -> t("kingdom.npcMemory.strifeWorsens.entry")
    "ruins-cleared" -> t("kingdom.npcMemory.ruinsCleared.entry")
    "realm-expanded" -> t("kingdom.npcMemory.realmExpanded.entry")
    "kingdom-advanced" -> t("kingdom.npcMemory.kingdomAdvanced.entry")
    "renown-peaked" -> t("kingdom.npcMemory.renownPeaked.entry")
    "war-looms" -> t("kingdom.npcMemory.warLooms.entry")
    "war-relieved" -> t("kingdom.npcMemory.warRelieved.entry")
    "lean-year" -> t("kingdom.npcMemory.leanYear.entry")
    "clock-fired" -> t("kingdom.npcMemory.clockFired.entry")
    "caravan-raided" -> t("kingdom.npcMemory.caravanRaided.entry")
    "caravan-delivered" -> t("kingdom.npcMemory.caravanDelivered.entry")
    "milestone-earned" -> t("kingdom.npcMemory.milestoneEarned.entry")
    else -> ruleId
}

fun localizeAttitudeBand(band: AttitudeBand): String = when (band) {
    AttitudeBand.HOSTILE -> t("kingdom.npcMemory.band.hostile")
    AttitudeBand.UNFRIENDLY -> t("kingdom.npcMemory.band.unfriendly")
    AttitudeBand.INDIFFERENT -> t("kingdom.npcMemory.band.indifferent")
    AttitudeBand.FRIENDLY -> t("kingdom.npcMemory.band.friendly")
    AttitudeBand.HELPFUL -> t("kingdom.npcMemory.band.helpful")
}
