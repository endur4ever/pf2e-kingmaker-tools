package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.app.forms.FormElementContext
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.data.kingdom.structures.CommodityStorage
import at.posselt.pfrpg2e.kingdom.data.RawCommodities
import at.posselt.pfrpg2e.kingdom.data.RawCurrentCommodities
import at.posselt.pfrpg2e.kingdom.sheet.ProjectedResources
import at.posselt.pfrpg2e.kingdom.sheet.Turn
import at.posselt.pfrpg2e.utils.t
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface CommodityContext {
    val food: FormElementContext
    val lumber: FormElementContext
    val luxuries: FormElementContext
    val ore: FormElementContext
    val stone: FormElementContext
}

@JsPlainObject
external interface CapacityContext {
    val ore: Int
    val food: Int
    val lumber: Int
    val stone: Int
    val luxuries: Int
}

@JsPlainObject
external interface CommodityStorageCapContext {
    val food: Boolean
    val lumber: Boolean
    val luxuries: Boolean
    val ore: Boolean
    val stone: Boolean
}

@Suppress("unused")
@JsPlainObject
external interface CommoditiesContext {
    val now: CommodityContext
    val next: CommodityContext
    val capacity: CapacityContext
    val cappedByStorage: CommodityStorageCapContext
}

fun RawCommodities.toContext(
    round: Turn,
    capacity: CommodityStorage,
    automate: Boolean = false,
    projected: ProjectedResources? = null,
) = if (round == Turn.NEXT && automate && projected != null) {
    CommodityContext(
        food = NumberInput(
            name = "commodities.${round.value}.food",
            label = t(round.i18nKeyShort),
            value = 0,
            stacked = false,
            elementClasses = listOf("km-width-small", "km-slim-inputs"),
            hideLabel = true,
            readonly = true,
        ).toContext(),
        lumber = NumberInput(
            name = "commodities.${round.value}.lumber",
            label = t(round.i18nKeyShort),
            value = projected.income.lumber,
            stacked = false,
            elementClasses = listOf("km-width-small", "km-slim-inputs"),
            hideLabel = true,
            readonly = true,
        ).toContext(),
        luxuries = NumberInput(
            name = "commodities.${round.value}.luxuries",
            label = t(round.i18nKeyShort),
            value = projected.income.luxuries,
            stacked = false,
            elementClasses = listOf("km-width-small", "km-slim-inputs"),
            hideLabel = true,
            readonly = true,
        ).toContext(),
        ore = NumberInput(
            name = "commodities.${round.value}.ore",
            label = t(round.i18nKeyShort),
            value = projected.income.ore,
            stacked = false,
            elementClasses = listOf("km-width-small", "km-slim-inputs"),
            hideLabel = true,
            readonly = true,
        ).toContext(),
        stone = NumberInput(
            name = "commodities.${round.value}.stone",
            label = t(round.i18nKeyShort),
            value = projected.income.stone,
            stacked = false,
            elementClasses = listOf("km-width-small", "km-slim-inputs"),
            hideLabel = true,
            readonly = true,
        ).toContext(),
    )
} else {
    CommodityContext(
        food = Select.range(
            from = 0,
            to = capacity.food,
            value = food,
            name = "commodities.${round.value}.food",
            label = if (round == Turn.NOW) t("kingdom.food") else t(round.i18nKeyShort),
            elementClasses = if(round == Turn.NOW) listOf("km-width-small") else listOf("km-width-small", "km-slim-inputs"),
            labelClasses = listOf("km-slim-inputs"),
            stacked = false,
            hideLabel = round == Turn.NEXT,
        ).toContext(),
        lumber = Select.range(
            from = 0,
            to = capacity.lumber,
            value = lumber,
            name = "commodities.${round.value}.lumber",
            label = if (round == Turn.NOW) t("kingdom.lumber") else t(round.i18nKeyShort),
            elementClasses = if(round == Turn.NOW) listOf("km-width-small") else listOf("km-width-small", "km-slim-inputs"),
            labelClasses = listOf("km-slim-inputs"),
            stacked = false,
            hideLabel = round == Turn.NEXT,
        ).toContext(),
        luxuries = Select.range(
            from = 0,
            to = capacity.luxuries,
            value = luxuries,
            name = "commodities.${round.value}.luxuries",
            label = if (round == Turn.NOW) t("kingdom.luxuries") else t(round.i18nKeyShort),
            elementClasses = if(round == Turn.NOW) listOf("km-width-small") else listOf("km-width-small", "km-slim-inputs"),
            labelClasses = listOf("km-slim-inputs"),
            stacked = false,
            hideLabel = round == Turn.NEXT,
        ).toContext(),
        ore = Select.range(
            from = 0,
            to = capacity.ore,
            value = ore,
            name = "commodities.${round.value}.ore",
            label = if (round == Turn.NOW) t("kingdom.ore") else t(round.i18nKeyShort),
            elementClasses = if(round == Turn.NOW) listOf("km-width-small") else listOf("km-width-small", "km-slim-inputs"),
            labelClasses = listOf("km-slim-inputs"),
            stacked = false,
            hideLabel = round == Turn.NEXT,
        ).toContext(),
        stone = Select.range(
            from = 0,
            to = capacity.stone,
            value = stone,
            name = "commodities.${round.value}.stone",
            label = if (round == Turn.NOW) t("kingdom.stone") else t(round.i18nKeyShort),
            elementClasses = if(round == Turn.NOW) listOf("km-width-small") else listOf("km-width-small", "km-slim-inputs"),
            labelClasses = listOf("km-slim-inputs"),
            stacked = false,
            hideLabel = round == Turn.NEXT,
        ).toContext(),
    )
}

fun RawCurrentCommodities.toContext(
    capacity: CommodityStorage,
    automate: Boolean = false,
    projected: ProjectedResources? = null,
) =
    CommoditiesContext(
        now = now.toContext(Turn.NOW, capacity, automate = false, projected = null),
        next = next.toContext(Turn.NEXT, capacity, automate, projected),
        capacity = CapacityContext(
            ore = capacity.ore,
            food = capacity.food,
            lumber = capacity.lumber,
            stone = capacity.stone,
            luxuries = capacity.luxuries,
        ),
        cappedByStorage = CommodityStorageCapContext(
            food = false,
            lumber = projected?.lumberCappedByStorage == true,
            luxuries = projected?.luxuriesCappedByStorage == true,
            ore = projected?.oreCappedByStorage == true,
            stone = projected?.stoneCappedByStorage == true,
        ),
    )
