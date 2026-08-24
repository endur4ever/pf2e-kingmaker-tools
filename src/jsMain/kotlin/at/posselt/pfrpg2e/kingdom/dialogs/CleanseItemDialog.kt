package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.kingdom.CleanseItemSettlement
import at.posselt.pfrpg2e.kingdom.CleanseItemStructure
import at.posselt.pfrpg2e.kingdom.cleanseItemPlan
import at.posselt.pfrpg2e.kingdom.eligibleCleanseSettlements
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import com.foundryvtt.core.ui
import com.foundryvtt.pf2e.item.PF2EItem
import com.foundryvtt.pf2e.item.itemFromUuid
import js.objects.recordOf
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

/**
 * Preparation step for the Cleanse Item activity.
 *
 * The activity has always been rollable, but as a plain leadership check: nothing asked which item
 * was being cleansed, so the DC never depended on the item's level, no structure was required, and
 * no luxuries were ever spent. [cleanseItemPlan] shipped with tests and no caller. This dialog is
 * what supplies it an item and a settlement.
 */

/** What the GM chose, once the dialog is submitted. */
data class CleanseItemPreparation(
    val itemUuid: String,
    val itemName: String,
    val itemLevel: Int,
    val settlement: CleanseItemSettlement,
    val dc: Int,
    val luxuryCost: Int,
    val counteractLevel: Int,
)

@Suppress("unused")
@JsPlainObject
external interface CleanseItemData {
    val itemUuid: String?
    val settlementId: String?
}

@Suppress("unused")
@JsPlainObject
external interface CleanseItemContext : ValidatedHandlebarsContext {
    var formRows: Array<Any>
    var itemName: String
    var itemLevel: Int
    var hasItem: Boolean
    var dc: Int
    var luxuryCost: Int
    var counteractLevel: Int
    var requiredStructure: String
    var hasEligibleSettlement: Boolean
    /** Both an item and a settlement that can host it; the roll button is dead without both. */
    var canRoll: Boolean
}

@JsExport
class CleanseItemModel(
    value: AnyObject? = undefined,
    context: DocumentConstructionContext? = undefined,
) : DataModel(value, context) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("itemUuid", nullable = true)
            string("settlementId", nullable = true)
        }
    }
}

/**
 * An item's level from PF2e system data, 0 when it has none.
 *
 * Level is not on the typed facade, so this reaches through `system.level.value`. Guarded, because
 * a GM can drop anything droppable here -- including items whose type carries no level at all.
 */
fun cleanseItemLevelOf(item: PF2EItem): Int =
    runCatching { item.asDynamic().system?.level?.value as? Int }.getOrNull() ?: 0

private class CleanseItemDialog(
    private val kingdomLevel: Int,
    private val settlements: List<CleanseItemSettlement>,
    preselected: PF2EItem?,
    private val onPrepared: (CleanseItemPreparation) -> Unit,
) : FormApp<CleanseItemContext, CleanseItemData>(
    title = t("activities.cleanse-item.title"),
    template = "applications/kingdom/cleanse-item.hbs",
    dataModel = CleanseItemModel::class.js,
    id = "kmCleanseItem",
    width = 520,
) {
    // Seeded when the caller already knows the item -- e.g. the cursed row of a loot award, where
    // making the GM drag back an item the module just handed them is the friction this removes.
    private var itemUuid: String? = preselected?.uuid
    private var itemName: String = preselected?.name ?: ""
    private var itemLevel: Int = preselected?.let(::cleanseItemLevelOf) ?: 0
    private var settlementId: String? = null

    init {
        // Accepting a drop of any Item keeps this usable for the cursed oddities a GM invents,
        // which are rarely of a tidy equipment subtype.
        onDocumentRefDrop(".km-cleanse-drop", { it.type == "Item" }) { _, ref ->
            buildPromise {
                // Every item ref subtype carries a uuid, but they share no typed supertype that
                // exposes it, and matching all fourteen by hand would silently drop new ones.
                val uuid = ref.asDynamic().uuid as? String
                val item = uuid?.let { itemFromUuid(it) }
                if (item == null) {
                    ui.notifications.error(t("activities.cleanse-item.error.unreadableItem"))
                } else {
                    itemUuid = uuid
                    itemName = item.name ?: ""
                    itemLevel = cleanseItemLevelOf(item)
                    render()
                }
            }
        }
    }

    private fun plan() = cleanseItemPlan(itemLevel = itemLevel, kingdomLevel = kingdomLevel)

    private fun eligible() = eligibleCleanseSettlements(settlements, plan().requiredStructure)

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions,
    ): Promise<CleanseItemContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        val plan = plan()
        val eligible = eligible()
        // Re-picking a heavier item can disqualify the settlement already chosen, so the stored
        // choice is dropped rather than silently rolling against a settlement that cannot host it.
        if (eligible.none { it.id == settlementId }) settlementId = eligible.firstOrNull()?.id
        CleanseItemContext(
            partId = parent.partId,
            isFormValid = itemUuid != null && eligible.isNotEmpty(),
            formRows = arrayOf(
                Select(
                    name = "settlementId",
                    label = t("activities.cleanse-item.settlement"),
                    value = settlementId,
                    options = eligible.map { SelectOption(label = it.name, value = it.id) },
                    required = false,
                ).toContext(),
            ),
            itemName = itemName,
            itemLevel = itemLevel,
            hasItem = itemUuid != null,
            dc = plan.dc,
            luxuryCost = plan.luxuryCost,
            counteractLevel = plan.counteractLevel,
            requiredStructure = t(plan.requiredStructure.i18nKey),
            hasEligibleSettlement = eligible.isNotEmpty(),
            canRoll = itemUuid != null && eligible.isNotEmpty(),
        )
    }

    // Fires on every form change, so it only keeps the choice in sync; the roll is committed by
    // the explicit button below, the same split ClimateConfiguration uses.
    override fun onParsedSubmit(value: CleanseItemData) = buildPromise {
        settlementId = value.settlementId
        null
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        if (target.dataset["action"] != "km-cleanse-roll") return
        val uuid = itemUuid
        val settlement = eligible().find { it.id == settlementId }
        if (uuid == null) {
            ui.notifications.error(t("activities.cleanse-item.error.noItem"))
            return
        }
        if (settlement == null) {
            ui.notifications.error(t("activities.cleanse-item.error.noSettlements"))
            return
        }
        val plan = plan()
        onPrepared(
            CleanseItemPreparation(
                itemUuid = uuid,
                itemName = itemName,
                itemLevel = itemLevel,
                settlement = settlement,
                dc = plan.dc,
                luxuryCost = plan.luxuryCost,
                counteractLevel = plan.counteractLevel,
            ),
        )
        close()
    }
}

/**
 * Opens the preparation dialog. [onPrepared] runs only when the GM submits a choice: a dialog
 * dismissed without one simply performs no activity, which is what dismissing it means.
 *
 * [preselected] fills the item in advance for callers that already have one; null leaves the drop
 * zone empty, which is the activity-menu path.
 */
fun openCleanseItemDialog(
    kingdomLevel: Int,
    settlements: List<CleanseItemSettlement>,
    preselected: PF2EItem? = null,
    onPrepared: (CleanseItemPreparation) -> Unit,
) {
    if (settlements.isEmpty()) {
        ui.notifications.error(t("activities.cleanse-item.error.noSettlements"))
        return
    }
    CleanseItemDialog(kingdomLevel, settlements, preselected, onPrepared).render(true)
}
