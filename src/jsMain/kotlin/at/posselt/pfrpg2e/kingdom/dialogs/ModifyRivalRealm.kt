package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.app.forms.Section
import at.posselt.pfrpg2e.app.forms.SectionsContext
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.app.forms.TextInput
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.kingdom.data.RawRivalRealm
import at.posselt.pfrpg2e.kingdom.rivalGrowthProfilesById
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.ui
import io.github.uuidjs.uuid.v4
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import js.core.Void
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

@JsPlainObject
external interface RivalRealmFormData {
    var factionRef: String
    var size: Int
    var fame: Int
    var armyCount: Int
    var growthProfile: String
    var sizeGrowth: String
    var fameGrowth: String
    var armyGrowth: String
    var growthMode: String
    var borderRegion: String
    var warArmyThreshold: String
    var pauseGrowth: Boolean
}

@JsExport
class RivalRealmDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("factionRef")
            int("size")
            int("fame")
            int("armyCount")
            string("growthProfile")
            // the optional numerics are strings on purpose: "blank = unset" has no representation
            // in a required number field, and a forced 0 is a real value here (a zero override
            // beats the preset, a blank one defers to it)
            string("sizeGrowth")
            string("fameGrowth")
            string("armyGrowth")
            string("growthMode")
            string("borderRegion")
            string("warArmyThreshold")
            boolean("pauseGrowth")
        }
    }
}

@JsPlainObject
external interface RivalRealmFormContext : ValidatedHandlebarsContext, SectionsContext

/**
 * Add/edit one rival realm (GM only; the sheet handlers gate on isGM before opening this).
 *
 * [onSave] receives the assembled row. The engine-owned bookkeeping the form never shows --
 * accruals, lastWarOfferArmyCount, headlinePool -- is carried over from [initial] explicitly:
 * rebuilding the row from form fields alone would silently zero a rival's banked growth every
 * time the GM touched the dial.
 */
@JsExport
class ModifyRivalRealm(
    private val initial: RawRivalRealm?,
    factionNames: List<String>,
    private val onSave: (realm: RawRivalRealm) -> Unit,
) : FormApp<RivalRealmFormContext, RivalRealmFormData>(
    title = if (initial == null) t("kingdom.rivalRealms.dialog.add") else t("kingdom.rivalRealms.dialog.edit"),
    template = "components/forms/application-form.hbs",
    debug = false,
    dataModel = RivalRealmDataModel::class.js,
    width = 480,
    id = "kmRivalRealm",
) {
    // a rival whose group was renamed keeps its stale ref selectable, otherwise opening the
    // dialog would silently relink it to whatever option happens to be first
    private val factionOptions: List<String> =
        (factionNames + listOfNotNull(initial?.factionRef?.takeIf { it !in factionNames })).distinct()

    private var data: RivalRealmFormData = RivalRealmFormData(
        factionRef = initial?.factionRef ?: factionOptions.firstOrNull() ?: "",
        size = initial?.size ?: 0,
        fame = initial?.fame ?: 0,
        armyCount = initial?.armyCount ?: 0,
        growthProfile = initial?.growthProfile ?: "",
        sizeGrowth = initial?.sizeGrowthPerTurn?.toString() ?: "",
        fameGrowth = initial?.fameGrowthPerTurn?.toString() ?: "",
        armyGrowth = initial?.armyGrowthPerTurn?.toString() ?: "",
        growthMode = initial?.growthMode ?: "flat",
        borderRegion = initial?.borderRegion ?: "",
        warArmyThreshold = initial?.warArmyThreshold?.toString() ?: "",
        pauseGrowth = initial?.pauseGrowth ?: false,
    )

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<RivalRealmFormContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        RivalRealmFormContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            sections = formContext(
                Section(
                    legend = t("kingdom.rivalRealms.title"),
                    formRows = listOf(
                        Select(
                            name = "factionRef",
                            label = t("kingdom.rivalRealms.dialog.faction"),
                            value = data.factionRef,
                            options = factionOptions.map { SelectOption(it, it) },
                            stacked = false,
                        ),
                        NumberInput(
                            name = "size",
                            label = t("kingdom.rivalRealms.size"),
                            value = data.size,
                            stacked = false,
                        ),
                        NumberInput(
                            name = "fame",
                            label = t("kingdom.rivalRealms.fame"),
                            value = data.fame,
                            stacked = false,
                        ),
                        NumberInput(
                            name = "armyCount",
                            label = t("kingdom.rivalRealms.armies"),
                            value = data.armyCount,
                            stacked = false,
                        ),
                        Select(
                            name = "growthProfile",
                            label = t("kingdom.rivalRealms.dialog.growthProfile"),
                            value = data.growthProfile,
                            options = listOf(SelectOption("—", "")) +
                                    rivalGrowthProfilesById().keys.sorted().map {
                                        SelectOption(t("kingdom.rivalRealms.profile.$it"), it)
                                    },
                            required = false,
                            stacked = false,
                        ),
                        TextInput(
                            name = "sizeGrowth",
                            label = t("kingdom.rivalRealms.dialog.sizeGrowth"),
                            value = data.sizeGrowth,
                            required = false,
                            stacked = false,
                            help = t("kingdom.rivalRealms.dialog.overrideHelp"),
                        ),
                        TextInput(
                            name = "fameGrowth",
                            label = t("kingdom.rivalRealms.dialog.fameGrowth"),
                            value = data.fameGrowth,
                            required = false,
                            stacked = false,
                        ),
                        TextInput(
                            name = "armyGrowth",
                            label = t("kingdom.rivalRealms.dialog.armyGrowth"),
                            value = data.armyGrowth,
                            required = false,
                            stacked = false,
                        ),
                        Select(
                            name = "growthMode",
                            label = t("kingdom.rivalRealms.dialog.growthMode"),
                            value = data.growthMode,
                            options = listOf(
                                SelectOption(t("kingdom.rivalRealms.dialog.modeFlat"), "flat"),
                                SelectOption(t("kingdom.rivalRealms.dialog.modeAgenda"), "agenda-driven"),
                            ),
                            stacked = false,
                        ),
                        TextInput(
                            name = "borderRegion",
                            label = t("kingdom.rivalRealms.dialog.borderRegion"),
                            value = data.borderRegion,
                            required = false,
                            stacked = false,
                        ),
                        TextInput(
                            name = "warArmyThreshold",
                            label = t("kingdom.rivalRealms.dialog.warThreshold"),
                            value = data.warArmyThreshold,
                            required = false,
                            stacked = false,
                            help = t("kingdom.rivalRealms.dialog.warThresholdHelp"),
                        ),
                        CheckboxInput(
                            name = "pauseGrowth",
                            label = t("kingdom.rivalRealms.dialog.pauseGrowth"),
                            value = data.pauseGrowth,
                        ),
                    )
                )
            )
        )
    }

    override fun onParsedSubmit(value: RivalRealmFormData): Promise<Void> = buildPromise {
        data = value
        undefined
    }

    /** "0,5" is how six of the eight shipped locales write one half; rejecting it silently would
     *  save "no override" while the GM believes they set a rate. */
    private fun parseRate(raw: String): Double? = raw.trim().replace(',', '.').toDoubleOrNull()

    /** Non-blank input that parses to nothing must block the save with feedback, never silently
     *  become the meaningful state "unset" (override deferred / war offer disabled). */
    private fun unparseable(raw: String): Boolean = raw.isNotBlank() && parseRate(raw) == null

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                if (unparseable(data.sizeGrowth) || unparseable(data.fameGrowth) ||
                    unparseable(data.armyGrowth) || unparseable(data.warArmyThreshold)
                ) {
                    ui.notifications.error(t("kingdom.rivalRealms.dialog.badNumber"))
                    return
                }
                close()
                onSave(RawRivalRealm(
                    id = initial?.id ?: v4(),
                    factionRef = data.factionRef.ifBlank { null },
                    size = data.size,
                    fame = data.fame,
                    armyCount = data.armyCount,
                    growthProfile = data.growthProfile.ifBlank { null },
                    sizeGrowthPerTurn = parseRate(data.sizeGrowth),
                    fameGrowthPerTurn = parseRate(data.fameGrowth),
                    armyGrowthPerTurn = parseRate(data.armyGrowth),
                    growthMode = data.growthMode.ifBlank { null },
                    borderRegion = data.borderRegion.ifBlank { null },
                    warArmyThreshold = parseRate(data.warArmyThreshold)?.toInt(),
                    pauseGrowth = data.pauseGrowth,
                    // engine-owned bookkeeping the form never shows: dropping these would zero a
                    // rival's banked fractional growth (and re-fire a dismissed war offer) on
                    // every edit
                    sizeAccrual = initial?.sizeAccrual,
                    fameAccrual = initial?.fameAccrual,
                    armyAccrual = initial?.armyAccrual,
                    lastWarOfferArmyCount = initial?.lastWarOfferArmyCount,
                    headlinePool = initial?.headlinePool,
                ))
            }
        }
    }
}
