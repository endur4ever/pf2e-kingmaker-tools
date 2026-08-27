package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.forms.Section
import at.posselt.pfrpg2e.app.forms.SectionsContext
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import js.core.Void
import js.objects.recordOf
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

/**
 * One selectable turn. [label] is what the checkbox reads: the turn number plus whatever the turn
 * left behind (its player notes, else its GM gazette, else the timestamp), so the GM can tell
 * turns apart without leaving the dialog.
 */
data class LinkableTurn(val turn: Int, val label: String, val linked: Boolean)

/**
 * Which turns can be linked as consequences of a vote closed on [closedTurn].
 *
 * Strictly LATER turns only: a consequence follows the decision it came from, so offering the
 * closing turn or an earlier one would invite a link that reads backwards. Newest first, because
 * the consequence a GM is linking is almost always the one that just happened.
 */
fun linkableTurnsFor(
    history: Array<RawTurnRecord>?,
    closedTurn: Int?,
    alreadyLinked: Array<Int>?,
): List<LinkableTurn> {
    val linked = (alreadyLinked ?: emptyArray()).toSet()
    return (history ?: emptyArray())
        .filter { closedTurn == null || it.turn > closedTurn }
        .sortedByDescending { it.turn }
        .map { record ->
            val note = record.playerNotes?.takeIf { it.isNotBlank() }
                ?: record.notes?.takeIf { it.isNotBlank() }
                ?: record.timestamp
            LinkableTurn(
                turn = record.turn,
                label = t("kingdom.councilVotes.linkPickerRow", recordOf("turn" to record.turn.toString()))
                    + " — " + note.take(80),
                linked = record.turn in linked,
            )
        }
}

@JsPlainObject
external interface LinkConsequenceFormData {
    var turns: Array<Boolean>
}

@JsExport
class LinkConsequenceDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            booleanArray("turns")
        }
    }
}

@JsPlainObject
external interface LinkConsequenceFormContext : ValidatedHandlebarsContext, SectionsContext

/**
 * GM picker for a vote's consequence turns. Checkboxes rather than a one-shot pick because links
 * are removable: unchecking a linked turn is how it comes off, so [onSave] receives the COMPLETE
 * desired set and the caller diffs it against what the vote currently carries.
 */
@JsExport
class LinkVoteConsequence(
    private val question: String,
    private val turns: List<LinkableTurn>,
    private val onSave: (linkedTurns: Set<Int>) -> Unit,
) : FormApp<LinkConsequenceFormContext, LinkConsequenceFormData>(
    title = t("kingdom.councilVotes.linkPickerTitle"),
    template = "components/forms/application-form.hbs",
    debug = false,
    dataModel = LinkConsequenceDataModel::class.js,
    width = 480,
    id = "kmLinkVoteConsequence",
) {
    private var checked: MutableList<Boolean> = turns.map { it.linked }.toMutableList()

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<LinkConsequenceFormContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        LinkConsequenceFormContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            sections = formContext(
                Section(
                    legend = question,
                    formRows = turns.mapIndexed { index, turn ->
                        CheckboxInput(
                            name = "turns.$index",
                            label = turn.label,
                            value = checked.getOrElse(index) { false },
                        )
                    }
                )
            )
        )
    }

    override fun onParsedSubmit(value: LinkConsequenceFormData): Promise<Void> = buildPromise {
        checked = value.turns.toMutableList()
        undefined
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                close()
                onSave(
                    turns.filterIndexed { index, _ -> checked.getOrElse(index) { false } }
                        .map { it.turn }
                        .toSet()
                )
            }
        }
    }
}
