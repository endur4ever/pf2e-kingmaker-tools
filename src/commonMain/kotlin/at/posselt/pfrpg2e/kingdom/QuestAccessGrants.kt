package at.posselt.pfrpg2e.kingdom

/**
 * A benefit a completed quest grants at kingdom level, unioned with structure-derived settlement
 * access in InspectSettlement (so a quest can grant a trainer or a higher item-purchase level in a
 * named settlement without the building actually existing). The GM-confirmed quest-reward flow
 * appends these; the settlement view unions them and shows their source ("granted by: <quest>").
 */
data class AccessGrant(
    /** "trainer" (class-trainer access) | "crafting" (crafting material access) | "itemLevel" (purchase level). */
    val benefitType: String,
    /** Trainer class id or crafting material id — the granted value for trainer/crafting benefits. */
    val value: String? = null,
    /** Item purchase level granted, for the "itemLevel" benefit. */
    val amount: Int? = null,
    val sourceQuestId: String,
    /** Settlement scene id the grant is scoped to; null = kingdom-wide (applies to every settlement). */
    val settlementId: String? = null,
)

/** The unioned access shown for a settlement. */
data class SettlementAccess(
    val trainers: List<String>,
    val craftingAccess: List<String>,
    val itemLevel: Int,
)

/** A grant applies to a settlement when it is kingdom-wide (null) or scoped to that settlement. */
fun AccessGrant.appliesTo(settlementId: String): Boolean =
    this.settlementId == null || this.settlementId == settlementId

/**
 * Unions the quest-granted [grants] into a settlement's structure-derived access. Trainer and
 * crafting access are set-unioned (deduped, structure access first); the item purchase level takes
 * the max of the structure-derived level and any applicable itemLevel grant. Only grants applicable
 * to [settlementId] (its own or kingdom-wide) contribute.
 */
fun unionSettlementAccess(
    settlementId: String,
    baseTrainers: List<String>,
    baseCrafting: List<String>,
    baseItemLevel: Int,
    grants: List<AccessGrant>,
): SettlementAccess {
    val applicable = grants.filter { it.appliesTo(settlementId) }
    val trainers = (baseTrainers + applicable.filter { it.benefitType == "trainer" }.mapNotNull { it.value }).distinct()
    val crafting = (baseCrafting + applicable.filter { it.benefitType == "crafting" }.mapNotNull { it.value }).distinct()
    val itemLevel = (listOf(baseItemLevel) + applicable.filter { it.benefitType == "itemLevel" }.mapNotNull { it.amount }).max()
    return SettlementAccess(trainers, craftingAccess = crafting, itemLevel = itemLevel)
}
