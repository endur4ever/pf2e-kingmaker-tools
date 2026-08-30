package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.awaitablePrompt
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.app.forms.TextInput
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.localizeXpSource
import at.posselt.pfrpg2e.kingdom.xp.XpLedgerEntry
import at.posselt.pfrpg2e.kingdom.xp.XpOfferStatus
import at.posselt.pfrpg2e.kingdom.xp.XpSourceKind
import at.posselt.pfrpg2e.utils.t
import io.github.uuidjs.uuid.v4
import js.objects.recordOf
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface XpLedgerEntryData {
    val sourceKind: String
    val sourceRef: String
    val amount: Int
    val note: String
}

/**
 * A GM-entered ledger row. Every amount here is one the GM typed: the plan's per-source award
 * table is not signed off, so nothing in this build proposes a number on its own.
 *
 * The entry is recorded CONFIRMED with the typed amount as the granted amount -- a hand-entered
 * row is already the GM's decision, and parking it as an unanswered offer would ask them to
 * confirm their own input.
 */
suspend fun askXpLedgerEntry(currentTurn: Int): XpLedgerEntry? =
    awaitablePrompt<XpLedgerEntryData, XpLedgerEntry?>(
        title = t("kingdom.xpLedger.addEntry"),
        templatePath = "components/forms/form.hbs",
        templateContext = recordOf(
            "formRows" to formContext(
                Select(
                    name = "sourceKind",
                    label = t("kingdom.xpLedger.sourceColumn"),
                    value = XpSourceKind.MANUAL.value,
                    options = XpSourceKind.entries.map {
                        SelectOption(value = it.value, label = localizeXpSource(it))
                    },
                ),
                NumberInput(
                    name = "amount",
                    label = t("kingdom.xpLedger.amount"),
                    value = 0,
                ),
                TextInput(
                    name = "sourceRef",
                    label = t("kingdom.xpLedger.sourceRef"),
                    value = "",
                    required = false,
                    help = t("kingdom.xpLedger.sourceRefHelp"),
                ),
                TextInput(
                    name = "note",
                    label = t("kingdom.xpLedger.note"),
                    value = "",
                    required = false,
                ),
            )
        ),
        width = 480,
    ) { data, _ ->
        val amount = data.amount
        if (amount == 0) return@awaitablePrompt null
        val kind = XpSourceKind.fromValue(data.sourceKind) ?: XpSourceKind.MANUAL
        XpLedgerEntry(
            id = v4(),
            turn = currentTurn,
            timestamp = kotlin.js.Date().toISOString(),
            sourceKind = kind,
            // a blank ref still needs identity for the double-count guard, and a hand entry is
            // deliberate by definition -- so it gets a unique one rather than colliding on ""
            sourceRef = data.sourceRef.trim().ifBlank { "manual-${v4()}" },
            proposedAmount = amount,
            grantedAmount = amount,
            status = XpOfferStatus.CONFIRMED,
            note = data.note.trim().takeIf { it.isNotBlank() },
        )
    }
