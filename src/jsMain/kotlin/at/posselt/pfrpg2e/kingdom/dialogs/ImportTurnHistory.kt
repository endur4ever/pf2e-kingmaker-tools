package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.confirm
import at.posselt.pfrpg2e.app.forms.TextArea
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.app.prompt
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.ParsedTurnRow
import at.posselt.pfrpg2e.kingdom.TURN_HISTORY_CAP
import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.parseTurnRows
import at.posselt.pfrpg2e.kingdom.planTurnHistoryMerge
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.ui
import js.objects.recordOf
import kotlinx.js.JsPlainObject

/**
 * Paste-and-import for pre-migration turn history from the Google Sheets workbook.
 *
 * A campaign that moved off the workbook starts with an empty kingdom.turnHistory, so Analytics is
 * blind to everything before the move. This is the backfill path: paste the History / Turn-Tracker
 * rows, see what parsed, confirm, and the records merge into history.
 *
 * The parsing and merge decisions are the pure, unit-tested [parseTurnRows] and
 * [planTurnHistoryMerge]; this dialog only collects the text, shows what they concluded, and asks
 * before writing. Nothing is applied until the confirm.
 *
 * See card t_39d469b1.
 */

@JsPlainObject
external interface ImportTurnHistoryData {
    val rows: String
}

/** How many parsed rows to show in the preview before committing. */
private const val PREVIEW_ROWS = 10

/** How many per-row parse errors to list before summarising the rest. */
private const val MAX_LISTED_ERRORS = 10

fun ParsedTurnRow.toRaw(): RawTurnRecord =
    RawTurnRecord(
        turn = turn,
        // Imported rows carry no real timestamp; an empty string keeps the field present and JSON
        // round-trippable without inventing a date the workbook never recorded.
        timestamp = "",
        fame = fame,
        resourcePoints = resourcePoints,
        consumption = consumption,
        unrest = unrest,
        xpAwarded = xpAwarded,
        notes = notes,
    )

private fun previewLine(row: ParsedTurnRow): String = buildString {
    append(t("kingdom.turnHistoryImport.previewRow", recordOf(
        "turn" to row.turn,
        "fame" to row.fame,
        "rp" to row.resourcePoints,
        "consumption" to row.consumption,
        "unrest" to row.unrest,
    )))
}

/**
 * Merge [imported] rows into [existing] history, keeping only [keptTurns].
 *
 * Imported rows REPLACE the same-turn existing record; every other existing record is preserved.
 * Restricting to [keptTurns] applies the cap that [planTurnHistoryMerge] already reported to the
 * GM, so what gets written is exactly what they agreed to in the confirm — the plan and the write
 * cannot disagree. Result is turn-sorted, which Analytics relies on.
 */
fun applyTurnHistoryImport(
    existing: List<RawTurnRecord>,
    imported: List<ParsedTurnRow>,
    keptTurns: Set<Int>,
): Array<RawTurnRecord> {
    val byTurn = existing.associateBy { it.turn }.toMutableMap()
    imported.forEach { byTurn[it.turn] = it.toRaw() }
    return byTurn.values
        .filter { it.turn in keptTurns }
        .sortedBy { it.turn }
        .toTypedArray()
}

/**
 * GM-only. Opens the paste dialog, then a confirm summarising exactly what will change before
 * anything is written.
 */
suspend fun importTurnHistoryDialog(game: Game, actor: KingdomActor) {
    if (!game.user.isGM) return
    prompt<ImportTurnHistoryData, Unit>(
        title = t("kingdom.turnHistoryImport.title"),
        templatePath = "components/forms/form.hbs",
        templateContext = recordOf(
            "formRows" to formContext(
                TextArea(
                    name = "rows",
                    label = t("kingdom.turnHistoryImport.rowsLabel"),
                    help = t("kingdom.turnHistoryImport.rowsHelp"),
                    value = "",
                    required = true,
                ),
            ),
        ),
    ) { data ->
        val parsed = parseTurnRows(data.rows)
        if (parsed.records.isEmpty()) {
            ui.notifications.error(
                t("kingdom.turnHistoryImport.nothingParsed", recordOf("errors" to parsed.errors.size)),
            )
            return@prompt
        }

        // Re-read inside the submit: the sheet may have been edited while the dialog was open.
        val kingdom = actor.getKingdom() ?: return@prompt
        val existing = (kingdom.turnHistory ?: emptyArray()).toList()
        val plan = planTurnHistoryMerge(
            existingTurns = existing.map { it.turn },
            importedTurns = parsed.records.map { it.turn },
            cap = TURN_HISTORY_CAP,
        )

        val preview = parsed.records.sortedBy { it.turn }.take(PREVIEW_ROWS).joinToString("<br>") { previewLine(it) }
        val errorList = parsed.errors.take(MAX_LISTED_ERRORS).joinToString("<br>") { it.message }
        val message = buildString {
            append(t("kingdom.turnHistoryImport.confirmParsed", recordOf("count" to parsed.records.size)))
            append("<hr>").append(preview)
            if (parsed.records.size > PREVIEW_ROWS) {
                append("<br>").append(
                    t("kingdom.turnHistoryImport.andMore", recordOf("count" to parsed.records.size - PREVIEW_ROWS)),
                )
            }
            if (parsed.errors.isNotEmpty()) {
                append("<hr>").append(
                    t("kingdom.turnHistoryImport.confirmErrors", recordOf("count" to parsed.errors.size)),
                )
                append("<br>").append(errorList)
                if (parsed.errors.size > MAX_LISTED_ERRORS) {
                    append("<br>").append(
                        t("kingdom.turnHistoryImport.andMore",
                            recordOf("count" to parsed.errors.size - MAX_LISTED_ERRORS)),
                    )
                }
            }
            if (plan.collisions.isNotEmpty()) {
                append("<hr>").append(
                    t("kingdom.turnHistoryImport.confirmCollisions", recordOf(
                        "count" to plan.collisions.size,
                        "turns" to plan.collisions.joinToString(", "),
                    )),
                )
            }
            if (plan.trimmed > 0) {
                append("<hr>").append(
                    t("kingdom.turnHistoryImport.confirmTrimmed",
                        recordOf("count" to plan.trimmed, "cap" to TURN_HISTORY_CAP)),
                )
            }
        }
        if (!confirm(message)) return@prompt

        // Imported rows replace same-turn existing records; everything else is preserved. Keeping
        // only plan.keptTurns applies the cap the plan already reported to the GM, so what is
        // written matches what they agreed to.
        kingdom.turnHistory = applyTurnHistoryImport(existing, parsed.records, plan.keptTurns.toSet())
        actor.setKingdom(kingdom)
        ui.notifications.info(
            t("kingdom.turnHistoryImport.imported", recordOf("count" to parsed.records.size)),
        )
    }
}
