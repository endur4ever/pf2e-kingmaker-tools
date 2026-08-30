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

    /** Stable identity. Mandatory before any chat card can name a specific rumor; null only on
     *  rows written by a build older than the lifecycle feature. */
    var id: String?

    /** World day number the rumor entered play. Null: adopt the day it is first seen ticking. */
    var bornDay: Int?

    /** fresh | stale | expired | converted | pinned. A STRING at this boundary; an unrecognised
     *  value drops the ROW from evaluation rather than throwing, so one bad row cannot take down
     *  the day's tick. */
    var state: String?

    /** true | distorted | false -- the GM's private assessment. Null = unassessed. */
    var veracity: String?

    /** Day an expiry beat was offered; a declined beat never re-offers. */
    var beatOfferedDay: Int?

    /** Machine-readable sibling of the prose [location]; enables the hex-hook conversion. */
    var sourceHexKey: String?
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

/**
 * Null when [RawRumor.state] holds a string this build does not know: the row is dropped from
 * evaluation INDIVIDUALLY rather than defaulted (a wrong state would mis-age it) or thrown on
 * (one bad row must not take down the day's tick). A null state is a legacy row and reads FRESH.
 */
fun RawRumor.toModel(): Rumor? {
    val parsedState = if (state == null) RumorState.FRESH else RumorState.fromValue(state) ?: return null
    return Rumor(
        text = text,
        isQuestHook = isQuestHook,
        questTemplateId = questTemplateId,
        questTemplateName = questTemplateName,
        location = location,
        sourceRegion = sourceRegion,
        isConverted = isConverted,
        convertedQuestId = convertedQuestId,
        id = id ?: "",
        bornDay = bornDay,
        state = parsedState,
        veracity = RumorVeracity.fromValue(veracity),
        beatOfferedDay = beatOfferedDay,
        sourceHexKey = sourceHexKey,
    )
}

fun Rumor.toRaw(): RawRumor = RawRumor(
    text = text,
    isQuestHook = isQuestHook,
    questTemplateId = questTemplateId,
    questTemplateName = questTemplateName,
    location = location,
    sourceRegion = sourceRegion,
    isConverted = isConverted,
    convertedQuestId = convertedQuestId,
    id = id.takeIf { it.isNotBlank() },
    bornDay = bornDay,
    state = state.value,
    veracity = veracity?.value,
    beatOfferedDay = beatOfferedDay,
    sourceHexKey = sourceHexKey,
)

// ── defensive accessors on CampingData ───────────────────────────────────────

fun CampingData.categoryWeightsOrDefault(): CategoryWeights =
    categoryWeights?.toModel() ?: CategoryWeights()

fun CampingData.rumorList(): List<Rumor> = rumors?.mapNotNull { it.toModel() } ?: emptyList()

/**
 * UNREACHABLE TODAY, deliberately kept: nothing writes [CampingData.merchantStock] and this
 * helper has no caller, so the MERCHANT encounter category can draw a result but never stock or
 * show a merchant. Left in place as the curator plan's scaffolding rather than deleted -- but
 * marked, so the next reader does not assume merchants work.
 */
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
