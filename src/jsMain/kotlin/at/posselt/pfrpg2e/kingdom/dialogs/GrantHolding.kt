package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.Section
import at.posselt.pfrpg2e.app.forms.SectionsContext
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.app.forms.TextInput
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.data.kingdom.HoldingTier
import at.posselt.pfrpg2e.data.kingdom.MAX_HOLDINGS_PER_PC
import at.posselt.pfrpg2e.kingdom.data.RawPersonalHolding
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import com.foundryvtt.core.ui
import io.github.uuidjs.uuid.v4
import js.core.Void
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

/** One grantable owner: a PC actor plus its display name. */
data class HoldingOwnerOption(val actorUuid: String, val userId: String?, val label: String)

@JsPlainObject
external interface HoldingFormData {
    var owner: String
    var title: String
    var name: String
    var kind: String
    var location: String
    var tier: Int
}

@JsExport
class HoldingDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("owner")
            string("title")
            string("name")
            string("kind")
            string("location")
            int("tier")
        }
    }
}

@JsPlainObject
external interface HoldingFormContext : ValidatedHandlebarsContext, SectionsContext

/**
 * Grant or edit one personal holding (GM only; the sheet handlers gate before opening).
 *
 * The MAX_HOLDINGS_PER_PC guardrail lives HERE, at save: the dialog refuses a third holding for
 * the same PC with an i18n error, enforcing "flavor, not economy sim" where the GM can see it.
 * Editing an existing holding never trips the guard against itself.
 *
 * On edit, the engine-owned bookkeeping (income stamps, ledger, event label, claim edge) is
 * carried from [initial] explicitly -- rebuilding from form fields would zero the ledger.
 */
@JsExport
class GrantHolding(
    private val initial: RawPersonalHolding?,
    private val owners: List<HoldingOwnerOption>,
    private val existingHoldings: List<RawPersonalHolding>,
    private val onSave: (holding: RawPersonalHolding) -> Unit,
) : FormApp<HoldingFormContext, HoldingFormData>(
    title = if (initial == null) t("kingdom.holdings.grant") else t("kingdom.holdings.edit"),
    template = "components/forms/application-form.hbs",
    debug = false,
    dataModel = HoldingDataModel::class.js,
    width = 480,
    id = "kmGrantHolding",
) {
    private var data: HoldingFormData = HoldingFormData(
        owner = initial?.actorUuid ?: owners.firstOrNull()?.actorUuid ?: "",
        title = initial?.title ?: "",
        name = initial?.name ?: "",
        kind = initial?.kind ?: "manor",
        location = initial?.boundHexKey ?: "",
        tier = initial?.incomeTier ?: 1,
    )

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<HoldingFormContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        HoldingFormContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            sections = formContext(
                Section(
                    legend = t("kingdom.holdings.title"),
                    formRows = listOf(
                        Select(
                            name = "owner",
                            label = t("kingdom.holdings.dialog.owner"),
                            value = data.owner,
                            options = owners.map { SelectOption(it.label, it.actorUuid) },
                            stacked = false,
                        ),
                        TextInput(
                            name = "title",
                            label = t("kingdom.holdings.dialog.title"),
                            value = data.title,
                            required = false,
                            stacked = false,
                        ),
                        TextInput(
                            name = "name",
                            label = t("kingdom.holdings.dialog.name"),
                            value = data.name,
                            stacked = false,
                        ),
                        Select(
                            name = "kind",
                            label = t("kingdom.holdings.dialog.kind"),
                            value = data.kind,
                            options = listOf(
                                SelectOption(t("kingdom.holdings.kind.manor"), "manor"),
                                SelectOption(t("kingdom.holdings.kind.lodge"), "lodge"),
                                SelectOption(t("kingdom.holdings.kind.tavernStake"), "tavern-stake"),
                                SelectOption(t("kingdom.holdings.kind.farmstead"), "farmstead"),
                                SelectOption(t("kingdom.holdings.kind.workshop"), "workshop"),
                                SelectOption(t("kingdom.holdings.kind.other"), "other"),
                            ),
                            stacked = false,
                        ),
                        TextInput(
                            name = "location",
                            label = t("kingdom.holdings.dialog.location"),
                            value = data.location,
                            required = false,
                            stacked = false,
                            help = t("kingdom.holdings.dialog.locationHelp"),
                        ),
                        Select(
                            name = "tier",
                            label = t("kingdom.holdings.dialog.tier"),
                            value = data.tier.toString(),
                            options = HoldingTier.entries.map { tier ->
                                SelectOption(
                                    when (tier) {
                                        HoldingTier.MODEST -> t("kingdom.holdings.tier.modest")
                                        HoldingTier.COMFORTABLE -> t("kingdom.holdings.tier.comfortable")
                                        HoldingTier.LAVISH -> t("kingdom.holdings.tier.lavish")
                                    },
                                    tier.value.toString(),
                                )
                            },
                            stacked = false,
                        ),
                    )
                )
            )
        )
    }

    override fun onParsedSubmit(value: HoldingFormData): Promise<Void> = buildPromise {
        data = value
        undefined
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                if (data.name.isBlank() || data.owner.isBlank()) {
                    ui.notifications.error(t("kingdom.holdings.dialog.needNameAndOwner"))
                    return
                }
                // the guardrail: never a third holding for one PC (editing does not count itself)
                val ownedCount = existingHoldings.count {
                    it.actorUuid == data.owner && it.id != initial?.id
                }
                if (ownedCount >= MAX_HOLDINGS_PER_PC) {
                    ui.notifications.error(
                        t("kingdom.holdings.dialog.tooMany", js.objects.recordOf("max" to MAX_HOLDINGS_PER_PC.toString()))
                    )
                    return
                }
                close()
                val owner = owners.firstOrNull { it.actorUuid == data.owner }
                onSave(RawPersonalHolding(
                    id = initial?.id ?: v4(),
                    actorUuid = data.owner,
                    ownerUserId = owner?.userId ?: initial?.ownerUserId,
                    ownerLabel = owner?.label ?: initial?.ownerLabel,
                    title = data.title.ifBlank { null },
                    name = data.name,
                    kind = data.kind,
                    boundHexKey = data.location.ifBlank { null },
                    structureSceneId = initial?.structureSceneId,
                    structureRef = initial?.structureRef,
                    incomeTier = data.tier,
                    condition = initial?.condition ?: "sound",
                    grantedTurn = initial?.grantedTurn,
                    // engine-owned bookkeeping, carried explicitly: rebuilding from form fields
                    // would zero the ledger and re-fire idempotent offers
                    lastIncomeTurn = initial?.lastIncomeTurn,
                    incomeAwardedTurn = initial?.incomeAwardedTurn,
                    lifetimeIncomeGold = initial?.lifetimeIncomeGold,
                    lastEventLabel = initial?.lastEventLabel,
                    lastKnownClaimed = initial?.lastKnownClaimed,
                    visibleToPlayers = initial?.visibleToPlayers,
                ))
            }
        }
    }
}
