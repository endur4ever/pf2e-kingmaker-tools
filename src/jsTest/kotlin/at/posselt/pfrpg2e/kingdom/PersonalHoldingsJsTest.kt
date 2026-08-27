package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.HoldingIncome
import at.posselt.pfrpg2e.kingdom.data.RawPersonalHolding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonalHoldingsJsTest {
    private fun holding(
        id: String = "h1",
        tier: Int = 2,
        condition: String = "sound",
        actorUuid: String? = "Actor.pc1",
        lastIncomeTurn: Int? = null,
    ): RawPersonalHolding {
        val obj = js("{}").unsafeCast<RawPersonalHolding>()
        obj.id = id
        obj.name = "Silverstead Manor"
        obj.kind = "manor"
        obj.incomeTier = tier
        obj.condition = condition
        obj.actorUuid = actorUuid
        obj.ownerLabel = "Valeria"
        obj.lastIncomeTurn = lastIncomeTurn
        return obj
    }

    @Test
    fun accrualStampsTheTurnAndEmitsALinePerProducingHolding() {
        val result = accrueHoldingIncome(
            holdings = arrayOf(holding()),
            ownerLevels = mapOf("Actor.pc1" to 10),
            kingdomLevel = 4,
            currentTurn = 7,
        )
        assertEquals(7, result.holdings.single().lastIncomeTurn)
        val line = result.offers.single()
        assertEquals(50, line.gold, "Comfortable 5gp/level at OWNER level 10, not kingdom 4")
        assertEquals(1, line.favors)
    }

    @Test
    fun aMissingOwnerFallsBackToKingdomLevelSoPreviewAndCommitAgree() {
        val result = accrueHoldingIncome(
            holdings = arrayOf(holding(actorUuid = "Actor.unresolvable")),
            ownerLevels = emptyMap(),
            kingdomLevel = 4,
            currentTurn = 7,
        )
        assertEquals(20, result.offers.single().gold, "5gp/level at kingdom level 4")
    }

    @Test
    fun theTickIsIdempotentPerTurn() {
        val first = accrueHoldingIncome(arrayOf(holding()), mapOf("Actor.pc1" to 10), 4, currentTurn = 7)
        val second = accrueHoldingIncome(first.holdings, mapOf("Actor.pc1" to 10), 4, currentTurn = 7)
        assertTrue(second.offers.isEmpty(), "a re-tick over the same turn accrues nothing")
        val nextTurn = accrueHoldingIncome(first.holdings, mapOf("Actor.pc1" to 10), 4, currentTurn = 8)
        assertEquals(1, nextTurn.offers.size, "the following turn accrues again")
    }

    @Test
    fun destroyedHoldingsEmitNoLineAndKeepTheirStamp() {
        val result = accrueHoldingIncome(
            holdings = arrayOf(holding(condition = "destroyed")),
            ownerLevels = mapOf("Actor.pc1" to 10),
            kingdomLevel = 4,
            currentTurn = 7,
        )
        assertTrue(result.offers.isEmpty())
        assertEquals(null, result.holdings.single().lastIncomeTurn,
            "a zero-income holding is not stamped -- nothing was accrued to make idempotent")
    }

    @Test
    fun unknownTierAndConditionDegradeConservativelyNotThrow() {
        val weird = holding(tier = 9, condition = "scorched")
        val result = accrueHoldingIncome(arrayOf(weird), mapOf("Actor.pc1" to 10), 4, 7)
        assertEquals(20, result.offers.single().gold, "unknown tier reads MODEST (2gp/level), unknown condition SOUND")
    }

    @Test
    fun damageAdvancesTheLadderOnACopyAndPinsTheLabel() {
        val damaged = applyHoldingDamage(
            holding(), at.posselt.pfrpg2e.data.kingdom.DamageSeverity.MINOR, "Raided by the Tiger Lords",
        )
        assertEquals("damaged", damaged.condition)
        assertEquals("Raided by the Tiger Lords", damaged.lastEventLabel)
        assertEquals("sound", holding().condition, "the input is never mutated")
    }
}
