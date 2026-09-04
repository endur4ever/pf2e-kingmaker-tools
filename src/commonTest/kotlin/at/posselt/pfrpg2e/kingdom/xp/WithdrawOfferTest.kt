package at.posselt.pfrpg2e.kingdom.xp

import kotlin.test.Test
import kotlin.test.assertEquals

class WithdrawOfferTest {
    private fun entry(id: String, ref: String, status: XpOfferStatus, kind: XpSourceKind = XpSourceKind.QUEST_COMPLETED) =
        XpLedgerEntry(id = id, turn = 1, timestamp = "", sourceKind = kind, sourceRef = ref, proposedAmount = 30, status = status)

    @Test
    fun withdrawingRemovesOnlyTheUnansweredOfferForThatBeat() {
        val entries = listOf(
            entry("a", "quest-1", XpOfferStatus.OFFERED),
            entry("b", "quest-2", XpOfferStatus.OFFERED),
            entry("c", "quest-1", XpOfferStatus.CONFIRMED),
            entry("d", "quest-1", XpOfferStatus.DISMISSED),
            entry("e", "quest-1", XpOfferStatus.OFFERED, kind = XpSourceKind.SITE_CLEARED),
        )
        val after = withdrawOffer(entries, XpSourceKind.QUEST_COMPLETED, "quest-1")
        assertEquals(listOf("b", "c", "d", "e"), after.map { it.id })
    }

    @Test
    fun aWithdrawnBeatCanBeOfferedAgain() {
        // removing the row rather than dismissing it is the point: proposeEntry's (kind, ref) guard
        // matches ANY status, so a dismissed row would block the offer when the quest really finishes
        val offered = listOf(entry("a", "quest-1", XpOfferStatus.OFFERED))
        val withdrawn = withdrawOffer(offered, XpSourceKind.QUEST_COMPLETED, "quest-1")
        assertEquals(emptyList(), withdrawn)
        // proposeEntry's guard matches ANY status, so the withdrawal is what makes this possible
        assertEquals(emptyList(), withdrawn.filter { it.sourceRef == "quest-1" })
    }

    @Test
    fun withdrawingSomethingThatWasNeverOfferedChangesNothing() {
        val entries = listOf(entry("a", "quest-9", XpOfferStatus.CONFIRMED))
        assertEquals(entries, withdrawOffer(entries, XpSourceKind.QUEST_COMPLETED, "quest-9"))
    }
}
