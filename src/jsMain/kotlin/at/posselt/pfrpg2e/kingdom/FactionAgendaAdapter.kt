package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.AgendaArchetypeSpec
import at.posselt.pfrpg2e.data.kingdom.AgendaFactionMove
import at.posselt.pfrpg2e.data.kingdom.AgendaMoveEffect
import at.posselt.pfrpg2e.data.kingdom.AgendaMoveSpec
import at.posselt.pfrpg2e.data.kingdom.FactionAgendaState
import at.posselt.pfrpg2e.data.kingdom.FactionSnapshot
import at.posselt.pfrpg2e.data.kingdom.TurnRng
import at.posselt.pfrpg2e.data.kingdom.advanceAllAgendas
import at.posselt.pfrpg2e.data.kingdom.factionAgendaTurnSeed
import at.posselt.pfrpg2e.kingdom.data.RawFactionAgenda
import at.posselt.pfrpg2e.kingdom.data.RawGroup
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import at.posselt.pfrpg2e.utils.t
import js.objects.Object
import js.objects.Record
import js.objects.recordOf

/**
 * Raw <-> pure mapping for the faction agenda engine (plan section 3): the tick engine hands
 * groups through here so the commonMain core never sees a Record or an external interface.
 */

private fun cooldownMap(record: Record<String, Int>?): Map<String, Int> {
    if (record == null) return emptyMap()
    return Object.keys(record.unsafeCast<Any>()).associateWith { key ->
        record[key].unsafeCast<Int>()
    }
}

private fun cooldownRecord(map: Map<String, Int>): Record<String, Int> {
    val record = recordOf<String, Int>()
    map.forEach { (k, v) -> record[k] = v }
    return record
}

fun RawFactionAgenda.toAgendaState(): FactionAgendaState = FactionAgendaState(
    goalId = goalId,
    goalTitle = goalTitle,
    progress = progress,
    segments = segments,
    archetype = archetype,
    moveCooldowns = cooldownMap(moveCooldowns),
    lastAdvancedTurn = lastAdvancedTurn,
    targetFaction = targetFaction,
)

fun FactionAgendaState.toRaw(): RawFactionAgenda = RawFactionAgenda(
    goalId = goalId,
    goalTitle = goalTitle,
    progress = progress,
    segments = segments,
    archetype = archetype,
    moveCooldowns = cooldownRecord(moveCooldowns),
    lastAdvancedTurn = lastAdvancedTurn,
    targetFaction = targetFaction,
)

fun RawGroup.toFactionSnapshot(): FactionSnapshot = FactionSnapshot(
    name = name,
    standing = standing,
    atWar = atWar,
    hasHex = hexKey != null,
    agenda = agenda?.toAgendaState(),
)

fun factionAgendaMoveSpecs(): Map<String, AgendaMoveSpec> =
    factionAgendaMovesById().mapValues { (_, raw) ->
        AgendaMoveSpec(
            id = raw.id,
            cooldownTurns = raw.cooldownTurns,
            validTargets = raw.validTargets,
            effect = raw.effect,
            effectMagnitude = raw.effectMagnitude,
            requires = raw.requires,
        )
    }

fun factionAgendaArchetypeSpecs(): Map<String, AgendaArchetypeSpec> =
    factionAgendaArchetypesById().mapValues { (_, raw) ->
        AgendaArchetypeSpec(
            id = raw.id,
            weights = cooldownMap(raw.weights),
            goals = raw.goals.toList(),
        )
    }

data class FactionAgendaTickOutcome(
    val groups: Array<RawGroup>,
    val moves: List<AgendaFactionMove>,
)

/**
 * The whole per-turn advance over raw groups. The pure engine mutates NOTHING external -- the
 * only raw change written back is each group's agenda; standing stays whatever the drift step
 * left it at, because every standing delta is a GM-confirmed offer.
 */
fun advanceFactionAgendasOnGroups(
    groups: Array<RawGroup>,
    kingdomName: String,
    currentTurn: Int,
    moves: Map<String, AgendaMoveSpec>,
    archetypes: Map<String, AgendaArchetypeSpec>,
    warThreats: Array<RawWarThreat>,
): FactionAgendaTickOutcome {
    val pending = warThreats
        .filter { it.offerConsumed != true }
        .mapNotNull { it.enemyFactionName }
        .toSet()
    val result = advanceAllAgendas(
        factions = groups.map { it.toFactionSnapshot() },
        currentTurn = currentTurn,
        moves = moves,
        archetypes = archetypes,
        rng = TurnRng(factionAgendaTurnSeed(kingdomName, currentTurn)),
        pendingWarThreatFactions = pending,
    )
    val stateByName = result.factions.associateBy { it.name }
    val updated = groups.map { group ->
        val nextAgenda = stateByName[group.name]?.agenda
        if (nextAgenda == null) group else RawGroup.copy(group, agenda = nextAgenda.toRaw())
    }.toTypedArray()
    return FactionAgendaTickOutcome(groups = updated, moves = result.moves)
}

/** Goal label: the GM's free-text override wins; otherwise a literal-key when over the pools. */
fun localizeAgendaGoal(goalId: String, goalTitle: String): String {
    if (goalTitle.isNotBlank()) return goalTitle
    return when (goalId) {
        "conquer-neighbor" -> t("kingdom.factionAgenda.goal.conquerNeighbor")
        "build-army" -> t("kingdom.factionAgenda.goal.buildArmy")
        "raid-trade-routes" -> t("kingdom.factionAgenda.goal.raidTradeRoutes")
        "secure-trade-route" -> t("kingdom.factionAgenda.goal.secureTradeRoute")
        "monopoly-resource" -> t("kingdom.factionAgenda.goal.monopolyResource")
        "ally-major-power" -> t("kingdom.factionAgenda.goal.allyMajorPower")
        "claim-grove" -> t("kingdom.factionAgenda.goal.claimGrove")
        "bind-mortal" -> t("kingdom.factionAgenda.goal.bindMortal")
        "veil-territory" -> t("kingdom.factionAgenda.goal.veilTerritory")
        "marriage-alliance" -> t("kingdom.factionAgenda.goal.marriageAlliance")
        "treaty-network" -> t("kingdom.factionAgenda.goal.treatyNetwork")
        "court-favor" -> t("kingdom.factionAgenda.goal.courtFavor")
        "expand-lair" -> t("kingdom.factionAgenda.goal.expandLair")
        "gather-horde" -> t("kingdom.factionAgenda.goal.gatherHorde")
        "sack-settlement" -> t("kingdom.factionAgenda.goal.sackSettlement")
        else -> goalId
    }
}

fun localizeAgendaArchetype(archetype: String): String = when (archetype) {
    "aggressive" -> t("kingdom.factionAgenda.archetype.aggressive")
    "mercantile" -> t("kingdom.factionAgenda.archetype.mercantile")
    "fey" -> t("kingdom.factionAgenda.archetype.fey")
    "political" -> t("kingdom.factionAgenda.archetype.political")
    "monster" -> t("kingdom.factionAgenda.archetype.monster")
    else -> archetype
}

/** Literal keys only -- composed "gazette.$id" keys would be invisible to the i18n scan. */
fun localizeAgendaMoveLine(move: AgendaFactionMove): String {
    val data = recordOf<String, Any?>("faction" to move.factionName)
    move.targetFaction?.let { data["target"] = it }
    move.progressAfter?.let { data["progress"] = it }
    move.segments?.let { data["segments"] = it }
    return when (move.moveId) {
        "expand" -> t("kingdom.factionAgenda.gazette.expand", data)
        "sabotage-rival" -> t("kingdom.factionAgenda.gazette.sabotage", data)
        "court-ally" -> t("kingdom.factionAgenda.gazette.courtAlly", data)
        "raise-army" -> t("kingdom.factionAgenda.gazette.raiseArmy", data)
        "court-pcs" -> t("kingdom.factionAgenda.gazette.courtPCs", data)
        else -> "${move.factionName}: ${move.moveId}"
    }
}
