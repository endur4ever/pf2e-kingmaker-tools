package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawScheduledPressure
import at.posselt.pfrpg2e.kingdom.data.toModel
import at.posselt.pfrpg2e.kingdom.pressure.PayloadKind
import at.posselt.pfrpg2e.kingdom.pressure.Recurrence
import at.posselt.pfrpg2e.migrations.migrations.Migration63
import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Migration63 seeds the pressure-schedule ledger, and the Raw boundary drops a schedule whose
 * recurrence or payload this build cannot interpret — one bad row must not take down the tick.
 */
class Migration63Test {
    private val game = unsafeJso<Game>()

    @Test
    fun seedsAnAbsentLedgerToEmpty() = runTest {
        val kingdom = unsafeJso<dynamic> {}
        Migration63().migrateKingdom(game, kingdom.unsafeCast<KingdomData>())
        assertEquals(0, kingdom.scheduledPressures.length as Int)
    }

    @Test
    fun aSecondRunNeverErasesSchedulesAGmAuthored() = runTest {
        val kingdom = unsafeJso<dynamic> {
            scheduledPressures = arrayOf(unsafeJso<dynamic> { id = "s1" })
        }
        Migration63().migrateKingdom(game, kingdom.unsafeCast<KingdomData>())
        assertEquals(1, kingdom.scheduledPressures.length as Int)
        assertEquals("s1", kingdom.scheduledPressures[0].id as String)
    }

    private fun raw(recurrence: String, payloadKind: String): RawScheduledPressure =
        RawScheduledPressure(
            id = "s1",
            name = "Troll Sightings",
            description = null,
            startDay = 100,
            recurrence = recurrence,
            endDay = null,
            lastFiredDay = null,
            payloadKind = payloadKind,
            payloadEventId = "troll-sighting",
            payloadEncounterId = null,
            payloadClockId = null,
            payloadBeatText = null,
            escalationCount = 0,
            resolveConditionKind = "threatResolved",
            resolveConditionRef = "hargulka",
            active = true,
        )

    @Test
    fun aKnownRowMapsToItsModel() {
        val model = raw("weekly", "spawnEvent").toModel()
        assertEquals(Recurrence.WEEKLY, model?.recurrence)
        assertEquals(PayloadKind.SPAWN_EVENT, model?.payloadKind)
        assertEquals(100, model?.startDay)
        assertNull(model?.lastFiredDay)
    }

    @Test
    fun anUnknownRecurrenceOrPayloadDropsTheRowRatherThanThrowing() {
        assertNull(raw("fortnightly", "spawnEvent").toModel())
        assertNull(raw("weekly", "teleport").toModel())
    }
}
