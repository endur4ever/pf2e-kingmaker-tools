package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawQuestRewards
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccessGrantWiringTest {
    private fun rewards(
        type: String? = null,
        value: String? = null,
        amount: Int? = null,
        settlementId: String? = null,
    ) = RawQuestRewards(
        accessBenefitType = type,
        accessValue = value,
        accessAmount = amount,
        accessSettlementId = settlementId,
    )

    @Test
    fun aQuestWithNoAccessRewardGrantsNothing() {
        assertNull(rewards().toAccessGrant("q1"))
        assertNull(rewards(type = "").toAccessGrant("q1"))
    }

    @Test
    fun aTrainerGrantCarriesItsValueAndSourceQuest() {
        val grant = rewards(type = ACCESS_BENEFIT_TRAINER, value = "cleric").toAccessGrant("q1")
        assertEquals(ACCESS_BENEFIT_TRAINER, grant?.benefitType)
        assertEquals("cleric", grant?.value)
        assertEquals("q1", grant?.sourceQuestId)
        assertNull(grant?.settlementId)
    }

    @Test
    fun aHalfFilledRewardGrantsNothingRatherThanADeadGrant() {
        // A trainer grant with no value, or an item-level grant with no level, would be applied and
        // then do nothing at all -- worse than not offering it, because the quest looks paid out.
        assertNull(rewards(type = ACCESS_BENEFIT_TRAINER).toAccessGrant("q1"))
        assertNull(rewards(type = ACCESS_BENEFIT_CRAFTING, value = "  ").toAccessGrant("q1"))
        assertNull(rewards(type = ACCESS_BENEFIT_ITEM_LEVEL).toAccessGrant("q1"))
        assertNull(rewards(type = ACCESS_BENEFIT_ITEM_LEVEL, amount = 0).toAccessGrant("q1"))
        assertNull(rewards(type = "nonsense", value = "x").toAccessGrant("q1"))
    }

    @Test
    fun anItemLevelGrantCarriesItsAmount() {
        val grant = rewards(type = ACCESS_BENEFIT_ITEM_LEVEL, amount = 12).toAccessGrant("q1")
        assertEquals(12, grant?.amount)
    }

    @Test
    fun aBlankSettlementMeansKingdomWide() {
        val scoped = rewards(type = ACCESS_BENEFIT_TRAINER, value = "cleric", settlementId = "Scene.a")
            .toAccessGrant("q1")
        assertEquals("Scene.a", scoped?.settlementId)

        val wide = rewards(type = ACCESS_BENEFIT_TRAINER, value = "cleric", settlementId = "")
            .toAccessGrant("q1")
        assertNull(wide?.settlementId)
    }

    @Test
    fun grantsRoundTripThroughPersistence() {
        val grants = listOf(
            AccessGrant(ACCESS_BENEFIT_TRAINER, value = "cleric", sourceQuestId = "q1"),
            AccessGrant(ACCESS_BENEFIT_ITEM_LEVEL, amount = 9, sourceQuestId = "q2", settlementId = "Scene.a"),
        )
        val kingdom = js("{}").unsafeCast<KingdomData>()
        kingdom.accessGrants = grants.toRawAccessGrants()

        assertEquals(grants, kingdom.accessGrantList())
    }

    @Test
    fun reopeningAQuestRevokesOnlyItsOwnGrants() {
        val grants = listOf(
            AccessGrant(ACCESS_BENEFIT_TRAINER, value = "cleric", sourceQuestId = "q1"),
            AccessGrant(ACCESS_BENEFIT_CRAFTING, value = "runes", sourceQuestId = "q2"),
            AccessGrant(ACCESS_BENEFIT_ITEM_LEVEL, amount = 9, sourceQuestId = "q1"),
        )
        val remaining = grants.withoutQuest("q1")
        assertEquals(1, remaining.size)
        assertEquals("q2", remaining.single().sourceQuestId)
    }

    @Test
    fun anEmptyKingdomReadsAsNoGrants() {
        val kingdom = js("{}").unsafeCast<KingdomData>()
        assertEquals(emptyList(), kingdom.accessGrantList())
    }
}
