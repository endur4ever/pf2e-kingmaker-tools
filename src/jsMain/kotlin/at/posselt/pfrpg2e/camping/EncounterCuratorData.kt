package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.camping.dialogs.RegionSetting
import kotlinx.js.JsPlainObject

/**
 * JS-interop (persistable) representations of the encounter-curator value models
 * (roadmap #11), plus converters to/from the clean commonMain models and
 * defensive accessors on [CampingData].
 *
 * New camping fields are nullable for backwards compatibility (same approach as
 * [CampingData.downtimeHoursSpent]); reads go through the *OrDefault helpers so
 * camping data saved before the curator existed keeps working without a
 * data-touching migration.
 */

@JsPlainObject
external interface RawCategoryWeights {
    var combat: Int
    var rp: Int
    var rumor: Int
    var merchant: Int
    var disease: Int
    var faction: Int
    var weather: Int
    var lore: Int
}

@JsPlainObject
external interface RawStockItem {
    var name: String
    var price: Int
}

@JsPlainObject
external interface RawMerchantStock {
    var name: String
    var stockItems: Array<RawStockItem>
    var stockRemaining: Int
    var stockMax: Int
    var greeting: String?
    var region: String?
    var uuid: String
}

@JsPlainObject
external interface RawRumor {
    var text: String
    var isQuestHook: Boolean
    var questTemplateId: String?
    var questTemplateName: String?
    var location: String?
    var sourceRegion: String?
    var isConverted: Boolean
    var convertedQuestId: String?
}

// ── converters ──────────────────────────────────────────────────────────────

fun RawCategoryWeights.toModel(): CategoryWeights =
    CategoryWeights(combat, rp, rumor, merchant, disease, faction, weather, lore)

fun CategoryWeights.toRaw(): RawCategoryWeights =
    RawCategoryWeights(
        combat = combat, rp = rp, rumor = rumor, merchant = merchant,
        disease = disease, faction = faction, weather = weather, lore = lore,
    )

fun RawStockItem.toModel(): StockItem = StockItem(name = name, price = price)

fun StockItem.toRaw(): RawStockItem = RawStockItem(name = name, price = price)

fun RawMerchantStock.toModel(): MerchantStock = MerchantStock(
    name = name,
    stockItems = stockItems.map { it.toModel() },
    stockRemaining = stockRemaining,
    stockMax = stockMax,
    greeting = greeting,
    region = region,
    uuid = uuid,
)

fun MerchantStock.toRaw(): RawMerchantStock = RawMerchantStock(
    name = name,
    stockItems = stockItems.map { it.toRaw() }.toTypedArray(),
    stockRemaining = stockRemaining,
    stockMax = stockMax,
    greeting = greeting,
    region = region,
    uuid = uuid,
)

fun RawRumor.toModel(): Rumor = Rumor(
    text = text,
    isQuestHook = isQuestHook,
    questTemplateId = questTemplateId,
    questTemplateName = questTemplateName,
    location = location,
    sourceRegion = sourceRegion,
    isConverted = isConverted,
    convertedQuestId = convertedQuestId,
)

fun Rumor.toRaw(): RawRumor = RawRumor(
    text = text,
    isQuestHook = isQuestHook,
    questTemplateId = questTemplateId,
    questTemplateName = questTemplateName,
    location = location,
    sourceRegion = sourceRegion,
    isConverted = isConverted,
    convertedQuestId = convertedQuestId,
)

// ── defensive accessors on CampingData ───────────────────────────────────────

fun CampingData.categoryWeightsOrDefault(): CategoryWeights =
    categoryWeights?.toModel() ?: CategoryWeights()

fun CampingData.rumorList(): List<Rumor> = rumors?.map { it.toModel() } ?: emptyList()

fun CampingData.merchantStockList(): List<MerchantStock> =
    merchantStock?.map { it.toModel() } ?: emptyList()

fun CampingData.isFilterByHexState(): Boolean = filterByHexState ?: false

/**
 * Per-category roll table UUIDs configured for this region, keyed by
 * [EncounterCategory.value]. Empty when unconfigured.
 */
fun RegionSetting.categoryRollTableUuidMap(): Map<String, String?> {
    val raw = categoryRollTableUuids ?: return emptyMap()
    return EncounterCategory.allCategories()
        .mapNotNull { c -> raw[c.value]?.let { c.value to it } }
        .toMap()
}

fun RegionSetting.suppressesEncountersOnClearedHex(): Boolean =
    suppressEncountersOnClearedHex ?: false
