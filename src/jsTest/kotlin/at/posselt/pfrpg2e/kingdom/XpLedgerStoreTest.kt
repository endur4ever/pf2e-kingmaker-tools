package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.sheet.contexts.buildXpLedgerContext
import at.posselt.pfrpg2e.kingdom.xp.RawXpLedgerEntry
import at.posselt.pfrpg2e.kingdom.xp.XpLedgerEntry
import at.posselt.pfrpg2e.kingdom.xp.XpOfferStatus
import at.posselt.pfrpg2e.kingdom.xp.XpSourceKind
import at.posselt.pfrpg2e.kingdom.xp.toModel
import at.posselt.pfrpg2e.kingdom.xp.toRaw
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XpLedgerStoreTest {
    private fun entry(
        id: String = "e1",
        kind: XpSourceKind = XpSourceKind.MANUAL,
        granted: Int? = 30,
        status: XpOfferStatus = XpOfferStatus.CONFIRMED,
    ) = XpLedgerEntry(
        id = id, turn = 4, timestamp = "2026-08-29T10:00:00.000Z",
        sourceKind = kind, sourceRef = "ref-$id", proposedAmount = 30,
        grantedAmount = granted, status = status, note = null,
    )

    @Test
    fun rawRoundTripPreservesEveryField() {
        val original = entry(granted = 25).copy(note = "session 12")
        val back = original.toRaw().toModel()
        assertEquals(original, back)
    }

    @Test
    fun anUnknownKindOrStatusDropsTheRowRatherThanThrowing() {
        val raw = entry().toRaw()
        raw.sourceKind = "somethingNewerBuildsKnow"
        assertNull(raw.toModel())
        val raw2 = entry().toRaw()
        raw2.status = "escalated"
        assertNull(raw2.toModel())
    }

    @Test
    fun contextShowsGrantedForAnsweredAndProposedWhileWaiting() {
        val entries = listOf(
            entry(id = "a", granted = 25),
            entry(id = "b", granted = null, status = XpOfferStatus.OFFERED),
        )
        val ctx = buildXpLedgerContext(entries, actualLifetimeXp = 100, isGM = true)
        val byId = ctx.rows.associateBy { it.id }
        assertEquals(25, byId.getValue("a").amount)
        // an unanswered row shows what was PROPOSED, and says so
        assertEquals(30, byId.getValue("b").amount)
        assertTrue(byId.getValue("b").isOffered)
        assertEquals(25, ctx.confirmedTotal)
    }

    @Test
    fun driftIsReportedNeverCorrected() {
        val ctx = buildXpLedgerContext(listOf(entry(granted = 40)), actualLifetimeXp = 100, isGM = false)
        assertEquals(100, ctx.actualLifetimeXp)
        assertEquals(60, ctx.drift)
        assertTrue(ctx.hasDrift)
        // combat XP never passes through the ledger, so a gap is expected, not an error
        val even = buildXpLedgerContext(listOf(entry(granted = 100)), actualLifetimeXp = 100, isGM = false)
        assertEquals(0, even.drift)
        assertTrue(!even.hasDrift)
    }

    @Test
    fun rowsAreNewestFirst() {
        val older = entry(id = "old").copy(turn = 2)
        val newer = entry(id = "new").copy(turn = 9)
        val ctx = buildXpLedgerContext(listOf(older, newer), actualLifetimeXp = 0, isGM = true)
        assertEquals(listOf("new", "old"), ctx.rows.map { it.id })
    }

    @Test
    fun unparseableStoredRowsAreCarriedThroughAWrite() {
        // simulating the funnel's contract: a row a NEWER build wrote must survive our save
        val stored = arrayOf(entry(id = "known").toRaw(), entry(id = "future").toRaw().also { it.status = "escalated" })
        val known = stored.mapNotNull { it.toModel() }
        val unknown = stored.filter { it.toModel() == null }
        assertEquals(1, known.size)
        assertEquals(1, unknown.size)
        val next: Array<RawXpLedgerEntry> = (unknown + known.map { it.toRaw() }).toTypedArray()
        assertEquals(2, next.size)
    }
}
