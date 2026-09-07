package at.posselt.pfrpg2e.kingdom

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

data class AttentionRow(
    val id: String,
    val i18nKey: String,
    val count: Int,
    val highlight: Boolean,
)