package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.getMilestones
import at.posselt.pfrpg2e.utils.t
import js.objects.recordOf
import kotlinx.js.JsPlainObject

/**
 * One completed milestone as the Chronicle renders it (deeds-chronicle plan 6).
 *
 * The row's [name] is the milestone's ALREADY-TRANSLATED catalog name, never a key assembled from
 * the id: a runtime-composed key is invisible to check_i18n_keys.py and would ship as raw text on
 * screen with every guard green.
 */
@Suppress("unused")
@JsPlainObject
external interface DeedChronicleRowContext {
    val turn: Int?
    val turnLabel: String
    val name: String
    val xp: Int
    /** Auto-detected deed rather than a GM-awarded story beat; the row marks it. */
    val isDeed: Boolean
}

/**
 * Completed milestones, newest first, cult beats withheld from players. Rows with no [awardedOnTurn] -- milestones ticked by hand
 * before the Chronicle existed -- sort last and render an em dash rather than inventing a turn.
 */
fun buildChronicleRows(kingdom: KingdomData, isGM: Boolean): Array<DeedChronicleRowContext> {
    val catalog = kingdom.getMilestones().associateBy { it.id }
    return kingdom.milestones
        .filter { it.completed }
        .mapNotNull { choice ->
            val milestone = catalog[choice.id] ?: return@mapNotNull null
            // cult milestones are GM-only everywhere else (see MilestoneContext); a read-only
            // Chronicle that lists them would leak the campaign's cult beats to the table
            if (milestone.isCultMilestone && !isGM) return@mapNotNull null
            DeedChronicleRowContext(
                turn = choice.awardedOnTurn,
                turnLabel = choice.awardedOnTurn
                    ?.let { t("kingdom.chronicle.turnLabel", recordOf("turn" to it)) }
                    ?: "—",
                name = milestone.name,
                xp = milestone.xp,
                isDeed = milestone.detectionId != null,
            )
        }
        .sortedByDescending { it.turn ?: Int.MIN_VALUE }
        .toTypedArray()
}
