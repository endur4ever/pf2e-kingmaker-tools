package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.data.kingdom.MAX_RIVAL_CHARTER_PARTIES
import at.posselt.pfrpg2e.data.kingdom.RIVAL_KIND_CONTESTED_CLAIM
import at.posselt.pfrpg2e.data.kingdom.RIVAL_KIND_LANDMARK
import at.posselt.pfrpg2e.data.kingdom.RIVAL_KIND_UNCLEARED_LAIR
import at.posselt.pfrpg2e.data.kingdom.RIVAL_KIND_UNEXPLORED
import at.posselt.pfrpg2e.data.kingdom.RIVAL_STATUS_ACTIVE
import at.posselt.pfrpg2e.data.kingdom.RIVAL_STATUS_DEFECTED
import at.posselt.pfrpg2e.data.kingdom.RIVAL_STATUS_JOINED
import at.posselt.pfrpg2e.data.kingdom.RIVAL_STATUS_RETIRED
import at.posselt.pfrpg2e.data.kingdom.activeRivalBandCount
import at.posselt.pfrpg2e.kingdom.data.RawGroup
import at.posselt.pfrpg2e.kingdom.data.RawRivalCharterParty
import at.posselt.pfrpg2e.kingdom.data.isActive
import at.posselt.pfrpg2e.kingdom.data.objectiveKindOrNull
import at.posselt.pfrpg2e.kingdom.rival.rivalEtaTurns
import at.posselt.pfrpg2e.utils.t
import kotlinx.js.JsPlainObject

@Suppress("unused")
@JsPlainObject
external interface RivalCharterRowContext {
    val id: String
    val band: String
    val faction: String
    val linked: Boolean
    val statusLabel: String
    val inactive: Boolean
    val positionLabel: String
    val objectiveLabel: String
    val objectiveKindLabel: String
    val etaTurns: Int?
    val queuedCount: Int
    val arrivals: Int
    /** null for players: blanked, not merely hidden (plan section 4.2). */
    val aggression: Int?
    val aggressionMax: Int?
}

@Suppress("unused")
@JsPlainObject
external interface RivalCharterContext {
    val rows: Array<RivalCharterRowContext>
    val isGM: Boolean
    val atCap: Boolean
    val hasRows: Boolean
}

/** Literal keys: a composed "status.$value" is invisible to the i18n scan. */
private fun statusLabel(status: String?): String = when (status ?: RIVAL_STATUS_ACTIVE) {
    RIVAL_STATUS_ACTIVE -> t("kingdom.rivalCharter.status.active")
    RIVAL_STATUS_DEFECTED -> t("kingdom.rivalCharter.status.defected")
    RIVAL_STATUS_RETIRED -> t("kingdom.rivalCharter.status.retired")
    RIVAL_STATUS_JOINED -> t("kingdom.rivalCharter.status.joined")
    else -> status ?: ""
}

private fun kindLabel(kind: String?): String = when (kind) {
    RIVAL_KIND_LANDMARK -> t("kingdom.rivalCharter.kind.landmark")
    RIVAL_KIND_UNCLEARED_LAIR -> t("kingdom.rivalCharter.kind.unclearedLair")
    RIVAL_KIND_CONTESTED_CLAIM -> t("kingdom.rivalCharter.kind.contestedClaim")
    RIVAL_KIND_UNEXPLORED -> t("kingdom.rivalCharter.kind.unexplored")
    else -> ""
}

fun buildRivalCharterContext(
    parties: Array<RawRivalCharterParty>,
    groups: Array<RawGroup>,
    isGM: Boolean,
    hexLabel: (String) -> String,
): RivalCharterContext {
    val groupNames = groups.map { it.name }.toSet()
    // a hidden band is STRIPPED for players, not blanked: the row itself is the leak
    val visible = if (isGM) parties.toList() else parties.filter { it.visibleToPlayers != false }
    val rows = visible.map { band ->
        val threshold = band.aggressionThreshold
        RivalCharterRowContext(
            id = band.id,
            band = band.name,
            faction = band.factionRef ?: t("kingdom.rivalCharter.unaffiliated"),
            linked = band.factionRef == null || band.factionRef in groupNames,
            statusLabel = statusLabel(band.status),
            inactive = !band.isActive(),
            positionLabel = band.currentHexKey?.let(hexLabel) ?: t("kingdom.rivalCharter.offMap"),
            objectiveLabel = band.objectiveHexKey?.let(hexLabel) ?: "\u2014",
            objectiveKindLabel = kindLabel(band.objectiveKindOrNull()),
            etaTurns = rivalEtaTurns(band),
            queuedCount = band.agenda?.size ?: 0,
            arrivals = band.arrivals ?: 0,
            aggression = if (isGM) (band.aggression ?: 0) else null,
            aggressionMax = if (isGM) (threshold ?: 10) else null,
        )
    }.toTypedArray()
    return RivalCharterContext(
        rows = rows,
        isGM = isGM,
        atCap = activeRivalBandCount(parties.map { it.status }) >= MAX_RIVAL_CHARTER_PARTIES,
        hasRows = rows.isNotEmpty(),
    )
}
