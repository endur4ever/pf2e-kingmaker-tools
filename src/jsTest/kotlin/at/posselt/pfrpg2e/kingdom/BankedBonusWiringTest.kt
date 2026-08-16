package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawBankedBonus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BankedBonusWiringTest {
    private fun kingdom(vararg bonuses: RawBankedBonus, turn: Int = 5): KingdomData =
        js("{}").unsafeCast<KingdomData>().also {
            it.bankedBonuses = arrayOf(*bonuses)
            it.currentTurn = turn
        }

    private fun bonus(id: String, value: Int, expires: Int? = null) = RawBankedBonus(
        id = id,
        value = value,
        source = "Request Foreign Aid",
        gainedTurn = 1,
        expiresTurn = expires,
    )

    @Test
    fun bonusesRoundTripThroughPersistence() {
        val k = kingdom(bonus("a", 2), bonus("b", 4, expires = 9))
        assertEquals(listOf(2, 4), k.bankedBonusList().map { it.value })
        assertEquals(listOf("a", "b"), k.bankedBonusIds())
        assertEquals(9, k.bankedBonusList()[1].expiresTurn)
    }

    @Test
    fun theBestOfferedBonusIsTheLargest() {
        // A GM offered a choice would always take the +4, so a card that silently spent the +2
        // would be a trap rather than a shortcut.
        val k = kingdom(bonus("a", 2), bonus("b", 4))
        assertEquals("b", k.bestSpendableBonus(currentTurn = 5)?.first)
        assertEquals(4, k.bestSpendableBonus(currentTurn = 5)?.second?.value)
    }

    @Test
    fun anExpiredBonusIsNeverOffered() {
        val k = kingdom(bonus("old", 4, expires = 3), bonus("live", 2))
        assertEquals("live", k.bestSpendableBonus(currentTurn = 5)?.first)
    }

    @Test
    fun anEmptyOrFullyExpiredBankOffersNothing() {
        assertNull(kingdom().bestSpendableBonus(currentTurn = 5))
        assertNull(kingdom(bonus("old", 4, expires = 3)).bestSpendableBonus(currentTurn = 5))
        assertNull(js("{}").unsafeCast<KingdomData>().bestSpendableBonus(currentTurn = 5))
    }

    @Test
    fun spendingRemovesExactlyOneEntry() {
        val k = kingdom(bonus("a", 2), bonus("b", 4))
        k.bankedBonuses = k.withoutBankedBonus("a")
        assertEquals(listOf("b"), k.bankedBonusIds())
    }

    @Test
    fun spendingAnAlreadySpentBonusChangesNothing() {
        // Offer cards sit in chat and the bank is shared, so a stale card can name a gone bonus.
        val k = kingdom(bonus("b", 4))
        k.bankedBonuses = k.withoutBankedBonus("a")
        assertEquals(listOf("b"), k.bankedBonusIds())
    }

    @Test
    fun bothForeignAidVariantsBank() {
        assertEquals(true, "request-foreign-aid" in FOREIGN_AID_ACTIVITIES)
        assertEquals(true, "request-foreign-aid-vk" in FOREIGN_AID_ACTIVITIES)
        assertEquals(false, "claim-hex" in FOREIGN_AID_ACTIVITIES)
    }
}
