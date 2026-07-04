package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.kingdom.WarThreatSnapshot

/**
 * Pure detection helpers for the Turn Wizard attention rows.
 * These functions operate on primitive data (counts/flags) only — no jsMain types.
 * This keeps them unit-testable in commonTest and usable from jsMain via data projection.
 */

/**
 * Counts generated quests that will fail THIS turn (turnsRemaining transitions from 1 to 0).
 *
 * @param questStatuses List of (status, turnsRemaining) pairs for generated quests.
 * @return Number of generated active quests with turnsRemaining == 1 (will hit 0 this tick).
 */
fun countQuestsFailingThisTurn(questStatuses: List<Pair<String, Int?>>): Int {
    return questStatuses.count { (status, turns) ->
        status == "active" && turns != null && turns == 1
    }
}

/**
 * Counts war threats at maximum escalation level.
 *
 * @param threats List of (escalationLevel, maxEscalation) pairs.
 * @return Number of threats where escalationLevel >= maxEscalation.
 */
fun countWarThreatsAtMaxEscalation(threats: List<Pair<Int, Int>>): Int {
    return threats.count { (level, max) -> level >= max }
}

/**
 * Counts expeditions awaiting GM resolution.
 *
 * @param expeditionStatuses List of expedition status strings.
 * @return Number of expeditions with status == "awaitingResolution".
 */
fun countExpeditionsAwaitingResolution(expeditionStatuses: List<String>): Int {
    return expeditionStatuses.count { it == "awaitingResolution" }
}

/**
 * Counts companions with an active injury (injuryDaysRemaining != null).
 *
 * @param injuryDays List of injuryDaysRemaining values (null = not injured).
 * @return Number of companions with injuryDaysRemaining != null.
 */
fun countInjuredCompanions(injuryDays: List<Int?>): Int {
    return injuryDays.count { it != null }
}

/**
 * Builds the attention row data for the Turn Wizard checklist.
 * Each row contains: id, i18n key, count, highlight (true if count > 0).
 *
 * @param questsFailingThisTurn Number of generated quests hitting 0 turns this tick.
 * @param warThreatsAtMax Number of war threats at max escalation.
 * @param expeditionsAwaiting Number of expeditions awaiting resolution.
 * @param companionsInjured Number of injured companions.
 * @return List of attention row data (id, i18nKey, count, highlight).
 */
fun buildAttentionRows(
    questsFailingThisTurn: Int,
    warThreatsAtMax: Int,
    expeditionsAwaiting: Int,
    companionsInjured: Int,
): List<AttentionRow> {
    return listOf(
        AttentionRow(
            id = "questsFailing",
            i18nKey = "kingdom.turnWizard.checklist.questsFailing",
            count = questsFailingThisTurn,
            highlight = questsFailingThisTurn > 0
        ),
        AttentionRow(
            id = "warThreatsMax",
            i18nKey = "kingdom.turnWizard.checklist.warThreatsMax",
            count = warThreatsAtMax,
            highlight = warThreatsAtMax > 0
        ),
        AttentionRow(
            id = "expeditionsAwaiting",
            i18nKey = "kingdom.turnWizard.checklist.expeditionsAwaiting",
            count = expeditionsAwaiting,
            highlight = expeditionsAwaiting > 0
        ),
        AttentionRow(
            id = "companionsInjured",
            i18nKey = "kingdom.turnWizard.checklist.companionsInjured",
            count = companionsInjured,
            highlight = companionsInjured > 0
        )
    )
}

/**
 * Detects which war threats were newly triggered (arrived) this turn.
 *
 * A threat is "newly triggered" if its [triggeredTurn] was null before the tick
 * and equals [currentTurn] after the tick. This corresponds to the moment
 * a threat's escalation hits max and [triggeredTurn] gets set (see [tickWarThreat]).
 *
 * @param beforeThreats War threats state before the tick.
 * @param afterThreats War threats state after the tick.
 * @param currentTurn Current kingdom turn number.
 * @return List of threat IDs that were newly triggered this turn.
 */
fun detectNewlyTriggeredWarThreats(
    beforeThreats: Array<WarThreatSnapshot>,
    afterThreats: Array<WarThreatSnapshot>,
    currentTurn: Int,
): List<String> {
    val beforeMap = beforeThreats.associateBy { it.id }
    return afterThreats
        .filter { after ->
            val before = beforeMap[after.id]
            // Newly triggered: had no triggeredTurn before, now has currentTurn
            before?.triggeredTurn == null && after.triggeredTurn == currentTurn
        }
        .map { it.id }
}

/**
 * Detects which war threats have a linked hex content entry.
 *
 * @param threats War threats to check.
 * @param hexContents Kingdom hex contents.
 * @return Set of threat IDs that have at least one hex content linking to them.
 */
fun detectWarThreatsWithLinkedHex(
    threats: Array<WarThreatSnapshot>,
    hexContents: Array<HexContent>,
): Set<String> {
    val linkedThreatIds = hexContents
        .mapNotNull { it.linkedWarThreatId?.takeIf { it.isNotBlank() } }
        .toSet()
    return threats
        .filter { it.id in linkedThreatIds }
        .map { it.id }
        .toSet()
}

data class AttentionRow(
    val id: String,
    val i18nKey: String,
    val count: Int,
    val highlight: Boolean,
)