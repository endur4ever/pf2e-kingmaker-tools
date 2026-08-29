package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.armies.BattleStatus
import at.posselt.pfrpg2e.data.kingdom.Relations
import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementSizeType
import at.posselt.pfrpg2e.data.kingdom.settlements.settlementSizeTypeForLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Guards for the ADAPTER's assumptions, not the detectors'. Every bug this file pins shipped as a
 * deed that could never fire while the pure detector tests stayed green, because the detectors
 * were correct and the values fed to them were not.
 */
class DeedWiringTest {
    @Test
    fun tradeAgreementComparesAgainstTheSerializedEnumValue() {
        // the adapter once compared against "trade-agreement" -- the kebab spelling in RawGroup's
        // comment -- while the persisted value is the enum's camelCase form
        assertEquals("tradeAgreement", Relations.TRADE_AGREEMENT.value)
        assertNotEquals("trade-agreement", Relations.TRADE_AGREEMENT.value)
    }

    @Test
    fun victoryIsTheStatusTheAdapterMustCountBeforeArchiving() {
        assertEquals("victory", BattleStatus.VICTORY.value)
        // ...and the tick's archive value is NOT it, which is why the count must be taken pre-tick
        assertNotEquals(BattleStatus.VICTORY.value, "archived")
    }

    @Test
    fun sizeBandsAreBlockBandsNotSettlementLevels() {
        // settlementSizeTypeForLevel reads settlementSizeData's levelFrom/levelTo, which mirror
        // maximumBlocks (1 / 4 / 9 / 10+). Feeding it a real settlement LEVEL would call a
        // one-block village a metropolis, so the adapter must feed parsed size types instead.
        assertEquals(SettlementSizeType.VILLAGE, settlementSizeTypeForLevel(1))
        assertEquals(SettlementSizeType.METROPOLIS, settlementSizeTypeForLevel(10))
    }
}
