package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.sheet.contexts.buildXpLedgerContext
import at.posselt.pfrpg2e.kingdom.xp.RawXpLedgerEntry
import at.posselt.pfrpg2e.kingdom.xp.XpBeatTier
import at.posselt.pfrpg2e.kingdom.xp.answerEntry
import at.posselt.pfrpg2e.kingdom.xp.defaultXpAward
import at.posselt.pfrpg2e.kingdom.xp.pendingOffers
import at.posselt.pfrpg2e.kingdom.xp.proposeEntry
import at.posselt.pfrpg2e.kingdom.xp.appendEntry
import at.posselt.pfrpg2e.kingdom.xp.XpLedgerEntry
import at.posselt.pfrpg2e.kingdom.xp.XpOfferStatus
import at.posselt.pfrpg2e.kingdom.xp.XpSourceKind
import at.posselt.pfrpg2e.kingdom.xp.toModel
import at.posselt.pfrpg2e.kingdom.xp.toRaw
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    // ── automatic offers (award table signed off 2026-09-01) ──────────────────────────────────

    @Test
    fun theSignedOffAwardTable() {
        assertEquals(10, defaultXpAward(XpSourceKind.HEX_RECONNOITERED))
        assertEquals(10, defaultXpAward(XpSourceKind.SITE_CLEARED, XpBeatTier.MINOR))
        assertEquals(30, defaultXpAward(XpSourceKind.SITE_CLEARED, XpBeatTier.MODERATE))
        assertEquals(80, defaultXpAward(XpSourceKind.SITE_CLEARED, XpBeatTier.MAJOR))
        assertEquals(30, defaultXpAward(XpSourceKind.QUEST_COMPLETED, XpBeatTier.MODERATE))
        assertEquals(80, defaultXpAward(XpSourceKind.QUEST_COMPLETED, XpBeatTier.MAJOR))
        assertEquals(30, defaultXpAward(XpSourceKind.EXPEDITION_RESOLVED))
        assertEquals(30, defaultXpAward(XpSourceKind.RP_ENCOUNTER))
        // a hand-entered row carries the GM's own number, so there is nothing to propose
        assertEquals(0, defaultXpAward(XpSourceKind.MANUAL))
    }

    @Test
    fun theSameBeatIsNeverOfferedTwice() {
        val first = entry(id = "a", status = XpOfferStatus.OFFERED, granted = null)
            .copy(sourceKind = XpSourceKind.SITE_CLEARED, sourceRef = "hex-5.5")
        val ledger = listOf(first)
        val again = first.copy(id = "b")
        assertNull(proposeEntry(ledger, again))
        // a DIFFERENT site still offers
        assertNotNull(proposeEntry(ledger, first.copy(id = "c", sourceRef = "hex-6.6")))
    }

    @Test
    fun answeringOnlyEverTouchesAnUnansweredRow() {
        val offered = entry(id = "a", status = XpOfferStatus.OFFERED, granted = null)
        val confirmed = answerEntry(listOf(offered), "a", granted = 45).single()
        assertEquals(XpOfferStatus.CONFIRMED, confirmed.status)
        assertEquals(45, confirmed.grantedAmount)
        // a second click on the same card grants nothing more
        val twice = answerEntry(listOf(confirmed), "a", granted = 45).single()
        assertEquals(45, twice.grantedAmount)
        assertEquals(XpOfferStatus.CONFIRMED, twice.status)
        // dismissing records no amount
        val dismissed = answerEntry(listOf(offered), "a", granted = null).single()
        assertEquals(XpOfferStatus.DISMISSED, dismissed.status)
        assertNull(dismissed.grantedAmount)
    }

    @Test
    fun theDigestListsOnlyUnansweredOffers() {
        val rows = listOf(
            entry(id = "a", status = XpOfferStatus.OFFERED, granted = null),
            entry(id = "b", status = XpOfferStatus.CONFIRMED),
            entry(id = "c", status = XpOfferStatus.DISMISSED, granted = null),
        )
        assertEquals(listOf("a"), pendingOffers(rows).map { it.id })
    }

    @Test
    fun theCapNeverPrunesAnUnansweredOffer() {
        // an offer trimmed away is XP the party earned and will never be asked about
        val answered = (1..600).map {
            entry(id = "c$it", status = XpOfferStatus.CONFIRMED).copy(sourceRef = "ref$it")
        }
        val offered = entry(id = "pending", status = XpOfferStatus.OFFERED, granted = null)
        val ledger = answered.fold(emptyList<XpLedgerEntry>()) { acc, e -> appendEntry(acc, e) }
        val withOffer = appendEntry(ledger, offered)
        assertTrue(withOffer.any { it.id == "pending" })
        assertTrue(withOffer.size <= 501, "answered history was not capped: ${withOffer.size}")
    }
}
