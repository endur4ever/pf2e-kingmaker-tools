package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawPcDowntimeProject
import at.posselt.pfrpg2e.kingdom.data.toModel
import at.posselt.pfrpg2e.kingdom.downtime.DowntimeKind
import at.posselt.pfrpg2e.kingdom.downtime.DowntimeStatus
import at.posselt.pfrpg2e.migrations.migrations.Migration62
import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Migration62 seeds the downtime project ledger, and the Raw boundary drops rows this build cannot
 * interpret rather than throwing — one bad project must not take down the daily tick.
 */
class Migration62Test {
    private val game = unsafeJso<Game>()

    @Test
    fun seedsAnAbsentLedgerToEmpty() = runTest {
        val kingdom = unsafeJso<dynamic> {}
        Migration62().migrateKingdom(game, kingdom.unsafeCast<KingdomData>())
        assertEquals(0, kingdom.downtimeProjects.length as Int)
    }

    @Test
    fun aSecondRunNeverErasesProjectsAlreadyWritten() = runTest {
        val kingdom = unsafeJso<dynamic> {
            downtimeProjects = arrayOf(unsafeJso<dynamic> { id = "p1" })
        }
        Migration62().migrateKingdom(game, kingdom.unsafeCast<KingdomData>())
        assertEquals(1, kingdom.downtimeProjects.length as Int)
        assertEquals("p1", kingdom.downtimeProjects[0].id as String)
    }

    private fun raw(kind: String, status: String): RawPcDowntimeProject =
        RawPcDowntimeProject(
            id = "p1",
            pcActorUuid = "Actor.pc",
            kind = kind,
            targetRef = "Item.sword",
            title = "a sword",
            settlementId = null,
            daysTotal = 30,
            daysRemaining = 12,
            dailyCostGp = null,
            status = status,
            pauseReason = null,
        )

    @Test
    fun aKnownRowRoundTripsWithItsNullsIntact() {
        val model = raw("craft", "inProgress").toModel()
        assertEquals(DowntimeKind.CRAFT, model?.kind)
        assertEquals(DowntimeStatus.IN_PROGRESS, model?.status)
        assertEquals(12, model?.daysRemaining)
        assertNull(model?.settlementId)
        assertNull(model?.pauseReason)
    }

    @Test
    fun anUnknownKindOrStatusDropsTheRowRatherThanThrowing() {
        // Written by a newer build: skipped by the tick, preserved in storage.
        assertNull(raw("bricklaying", "inProgress").toModel())
        assertNull(raw("craft", "abandoned").toModel())
    }
}
