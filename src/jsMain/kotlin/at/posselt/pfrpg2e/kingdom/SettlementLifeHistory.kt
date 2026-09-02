package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.settlementlife.LifeEventHookKind
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import js.objects.recordOf
import kotlinx.js.JsPlainObject

/** One row of a settlement's town-life chronicle (settlement-life plan section 4.2). */
@Suppress("unused")
@JsPlainObject
external interface LifeHistoryRowContext {
    val turn: Int
    val name: String
    val line: String
    val statusLabel: String
}

/** The view windows to the most recent entries; the record keeps everything (section 9 Q4). */
const val LIFE_HISTORY_VIEW_ROWS = 20

/**
 * Re-localize a stored record for display.
 *
 * The record keeps cast NAMES in slot order, not a slot map, so the line is rebuilt by zipping the
 * template's slots against them: castTemplate fills slots in declaration order and never skips one
 * (an empty roster casts an ephemeral "someone"), so the orders agree. A record whose template has
 * left the catalog still renders — as its names and turn — rather than vanishing from a chronicle
 * that already happened.
 */
/**
 * The gazette line for a stored record, rebuilt from the catalog.
 *
 * The record deliberately stores no prose -- only the template id and the cast -- so a wording
 * fix reaches every chronicle already written. A record whose template has left the catalog falls
 * back to its cast names rather than a raw key.
 */
fun lifeEventGazetteLine(settlementName: String, record: RawSettlementLifeEventRecord): String {
    val raw = rawSettlementLifeTemplates().find { it.id == record.templateId }
        ?: return record.castNames.joinToString(", ")
    val slots = settlementLifeTemplates().find { it.id == record.templateId }?.castSlots?.map { it.first }.orEmpty()
    val params = recordOf<String, Any?>("settlement" to settlementName)
    slots.zip(record.castNames).forEach { (slot, name) -> params[slot] = name }
    return t(raw.gazette, params.unsafeCast<AnyObject>())
}

fun lifeHistoryRows(
    settlementName: String,
    history: Array<RawSettlementLifeEventRecord>?,
): Array<LifeHistoryRowContext> {
    val byId = rawSettlementLifeTemplates().associateBy { it.id }
    return (history ?: emptyArray())
        .sortedByDescending { it.turn }
        .take(LIFE_HISTORY_VIEW_ROWS)
        .map { record ->
            val raw = byId[record.templateId]
            val hook = LifeEventHookKind.fromValue(record.hookKind) ?: LifeEventHookKind.NONE
            LifeHistoryRowContext(
                turn = record.turn,
                name = raw?.let { t(it.name) } ?: record.templateId,
                line = lifeEventGazetteLine(settlementName, record),
                statusLabel = when {
                    hook == LifeEventHookKind.NONE -> ""
                    record.hookApplied == true -> t("settlementLife.history.resolved")
                    else -> t("settlementLife.history.pending")
                },
            )
        }
        .toTypedArray()
}
