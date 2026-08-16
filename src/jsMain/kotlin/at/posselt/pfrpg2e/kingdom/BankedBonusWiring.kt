package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawBankedBonus

/** Activities whose success banks aid instead of handing out a pre-roll modifier. */
val FOREIGN_AID_ACTIVITIES = setOf("request-foreign-aid", "request-foreign-aid-vk")

/** The banked bonuses, in the pure model the spend math understands. */
fun KingdomData.bankedBonusList(): List<BankedBonus> =
    (bankedBonuses ?: emptyArray()).map {
        BankedBonus(
            value = it.value,
            source = it.source,
            gainedTurn = it.gainedTurn,
            expiresTurn = it.expiresTurn,
        )
    }

/** Ids run in lockstep with [bankedBonusList] so a spend can name exactly one entry. */
fun KingdomData.bankedBonusIds(): List<String> = (bankedBonuses ?: emptyArray()).map { it.id }

/** Remove the banked bonus with [id]; returns the array unchanged when it is already gone. */
fun KingdomData.withoutBankedBonus(id: String): Array<RawBankedBonus> =
    (bankedBonuses ?: emptyArray()).filterNot { it.id == id }.toTypedArray()

/**
 * The best banked bonus still spendable on [currentTurn], paired with its id, or null.
 *
 * Highest value first: a GM offered a choice between a +2 and a +4 would always take the +4, and a
 * card that silently spent the weaker one would be a trap.
 */
fun KingdomData.bestSpendableBonus(currentTurn: Int): Pair<String, BankedBonus>? =
    bankedBonusIds().zip(bankedBonusList())
        .filter { (_, bonus) -> !bonus.isExpired(currentTurn) }
        .maxByOrNull { (_, bonus) -> bonus.value }
