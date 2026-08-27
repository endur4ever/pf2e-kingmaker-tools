package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.Section
import at.posselt.pfrpg2e.app.forms.SectionsContext
import at.posselt.pfrpg2e.app.forms.TextInput
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import com.foundryvtt.core.ui
import js.core.Void
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

/** Upper bound on options the dialog offers; the plan's shape is "two to six option labels". */
const val MAX_COUNCIL_VOTE_OPTIONS = 6

/** Below this a "vote" is a statement, not a question. */
const val MIN_COUNCIL_VOTE_OPTIONS = 2

@JsPlainObject
external interface CouncilVoteFormData {
    var question: String
    var option1: String
    var option2: String
    var option3: String
    var option4: String
    var option5: String
    var option6: String
}

@JsExport
class CouncilVoteDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("question")
            string("option1")
            string("option2")
            string("option3")
            string("option4")
            string("option5")
            string("option6")
        }
    }
}

@JsPlainObject
external interface CouncilVoteFormContext : ValidatedHandlebarsContext, SectionsContext

/**
 * Opens a council vote: the GM types the question and two to six options.
 *
 * Six fixed inputs rather than an add-a-row list because the form toolkit renders a static row
 * set per section; blank trailing options are dropped on save, so "two options" is just leaving
 * four empty. [onSave] receives the cleaned question and label list -- the caller mints the id,
 * stamps the turn, appends under the cap, and posts the cards.
 */
@JsExport
class OpenCouncilVote(
    private val onSave: (question: String, options: List<String>) -> Unit,
) : FormApp<CouncilVoteFormContext, CouncilVoteFormData>(
    title = t("kingdom.councilVotes.open"),
    template = "components/forms/application-form.hbs",
    debug = false,
    dataModel = CouncilVoteDataModel::class.js,
    width = 480,
    id = "kmCouncilVote",
) {
    private var data: CouncilVoteFormData = CouncilVoteFormData(
        question = "",
        option1 = "",
        option2 = "",
        option3 = "",
        option4 = "",
        option5 = "",
        option6 = "",
    )

    private fun optionValues(): List<String> = listOf(
        data.option1, data.option2, data.option3,
        data.option4, data.option5, data.option6,
    )

    /** Blank-stripped, order-preserving. A blank in the MIDDLE closes up rather than shipping an
     *  empty button, so option indices always address a real label. */
    private fun cleanedOptions(): List<String> =
        optionValues().map { it.trim() }.filter { it.isNotBlank() }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<CouncilVoteFormContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        CouncilVoteFormContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            sections = formContext(
                Section(
                    legend = t("kingdom.councilVotes.ballotTitle"),
                    formRows = listOf(
                        TextInput(
                            name = "question",
                            label = t("kingdom.councilVotes.dialogQuestion"),
                            value = data.question,
                            stacked = false,
                        ),
                    ) + (1..MAX_COUNCIL_VOTE_OPTIONS).map { index ->
                        TextInput(
                            name = "option$index",
                            label = t(
                                "kingdom.councilVotes.dialogOption",
                                js.objects.recordOf("n" to index.toString()),
                            ),
                            value = optionValues()[index - 1],
                            required = index <= MIN_COUNCIL_VOTE_OPTIONS,
                            stacked = false,
                        )
                    }
                )
            )
        )
    }

    override fun onParsedSubmit(value: CouncilVoteFormData): Promise<Void> = buildPromise {
        data = value
        undefined
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                val question = data.question.trim()
                val options = cleanedOptions()
                // a vote with no question or one option is a broken card nobody can answer; block
                // the save with feedback rather than posting it
                if (question.isBlank() || options.size < MIN_COUNCIL_VOTE_OPTIONS) {
                    ui.notifications.error(t("kingdom.councilVotes.needQuestionAndOptions"))
                    return
                }
                close()
                onSave(question, options)
            }
        }
    }
}
