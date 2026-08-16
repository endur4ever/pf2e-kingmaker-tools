package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawAccessGrant
import at.posselt.pfrpg2e.kingdom.data.RawQuestRewards

/** The kingdom's quest-granted settlement benefits, in the pure model the union logic understands. */
fun KingdomData.accessGrantList(): List<AccessGrant> =
    (accessGrants ?: emptyArray()).map {
        AccessGrant(
            benefitType = it.benefitType,
            value = it.value,
            amount = it.amount,
            sourceQuestId = it.sourceQuestId,
            settlementId = it.settlementId,
        )
    }

fun List<AccessGrant>.toRawAccessGrants(): Array<RawAccessGrant> =
    map {
        RawAccessGrant(
            benefitType = it.benefitType,
            value = it.value,
            amount = it.amount,
            sourceQuestId = it.sourceQuestId,
            settlementId = it.settlementId,
        )
    }.toTypedArray()

/**
 * The grant a quest's rewards describe, or null when it grants no access.
 *
 * A trainer/crafting grant needs a value and an itemLevel grant needs an amount; a half-filled
 * reward yields nothing rather than a grant that silently does nothing once applied.
 */
fun RawQuestRewards.toAccessGrant(questId: String): AccessGrant? {
    val type = accessBenefitType?.takeIf { it.isNotBlank() } ?: return null
    val value = accessValue?.takeIf { it.isNotBlank() }
    val amount = accessAmount
    val valid = when (type) {
        ACCESS_BENEFIT_TRAINER, ACCESS_BENEFIT_CRAFTING -> value != null
        ACCESS_BENEFIT_ITEM_LEVEL -> amount != null && amount > 0
        else -> false
    }
    if (!valid) return null
    return AccessGrant(
        benefitType = type,
        value = value,
        amount = amount,
        sourceQuestId = questId,
        settlementId = accessSettlementId?.takeIf { it.isNotBlank() },
    )
}
