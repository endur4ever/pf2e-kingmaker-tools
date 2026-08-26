package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.data.kingdom.RivalGrowthProfile
import at.posselt.pfrpg2e.data.kingdom.RivalHeadlinePool
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawRivalRealm
import at.posselt.pfrpg2e.kingdom.data.headlinePoolOverride
import at.posselt.pfrpg2e.kingdom.data.isAgendaDriven
import at.posselt.pfrpg2e.kingdom.data.resolveGrowthProfile
import at.posselt.pfrpg2e.kingdom.data.toModel
import at.posselt.pfrpg2e.migrations.migrations.Migration67
import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Migration67 seeds the rival-realms array without ever backfilling or overwriting rows, and the
 * Raw boundary drops a row with no identity while letting every unrecognised string fall back --
 * one bad realm must never take down an End Turn tick.
 */
class Migration67Test {
    private val game = unsafeJso<Game>()

    private val presets = mapOf(
        "pitax-wartime" to RivalGrowthProfile(sizePerTurn = 0.33, famePerTurn = 0.5, armyPerTurn = 0.5),
    )

    @Test
    fun seedsAnAbsentRivalRealmsArrayToEmpty() = runTest {
        val kingdom = unsafeJso<dynamic> {}
        Migration67().migrateKingdom(game, kingdom.unsafeCast<KingdomData>())
        assertEquals(0, kingdom.rivalRealms.length as Int)
    }

    @Test
    fun aSecondRunNeverErasesRivalsAGmAuthored() = runTest {
        val kingdom = unsafeJso<dynamic> {
            rivalRealms = arrayOf(
                unsafeJso<dynamic> {
                    id = "rival-pitax"
                    factionRef = "Pitax"
                    size = 14
                    sizeAccrual = 0.66
                }
            )
        }
        Migration67().migrateKingdom(game, kingdom.unsafeCast<KingdomData>())
        assertEquals(1, kingdom.rivalRealms.length as Int)
        assertEquals("Pitax", kingdom.rivalRealms[0].factionRef as String)
        assertEquals(14, kingdom.rivalRealms[0].size as Int)
        assertEquals(0.66, kingdom.rivalRealms[0].sizeAccrual as Double)
    }

    @Test
    fun aTrackedRowMapsToItsStandingRow() {
        val row = RawRivalRealm(id = "r1", factionRef = "Pitax", size = 14, fame = 6, armyCount = 3).toModel()
        assertNotNull(row)
        assertEquals("Pitax", row.label)
        assertEquals(14, row.size)
        assertEquals(6, row.fame)
        assertEquals(3, row.armyCount)
        assertEquals(167, row.score)
        assertFalse(row.isPlayer)
    }

    @Test
    fun missingStatsReadAsZeroRatherThanDroppingTheRow() {
        val row = RawRivalRealm(id = "r1", factionRef = "Pitax").toModel(label = "Pitax, City of Rivers")
        assertNotNull(row)
        assertEquals("Pitax, City of Rivers", row.label)
        assertEquals(0, row.size)
        assertEquals(0, row.score)
    }

    @Test
    fun aRowWithNoIdentityIsDropped() {
        assertNull(RawRivalRealm(id = "r1").toModel())
        assertNull(RawRivalRealm(factionRef = "Pitax").toModel())
        assertNull(RawRivalRealm(id = " ", factionRef = "Pitax").toModel())
    }

    @Test
    fun anUnrecognisedHeadlinePoolFallsBackInsteadOfThrowing() {
        assertNull(RawRivalRealm(id = "r1", factionRef = "Pitax").headlinePoolOverride())
        assertNull(RawRivalRealm(id = "r1", factionRef = "Pitax", headlinePool = "siege").headlinePoolOverride())
        assertEquals(
            RivalHeadlinePool.ARMY_WAR,
            RawRivalRealm(id = "r1", factionRef = "Pitax", headlinePool = "armyWar").headlinePoolOverride(),
        )
    }

    @Test
    fun perStatDialsBeatTheNamedProfileStatByStat() {
        val realm = RawRivalRealm(
            id = "r1",
            factionRef = "Pitax",
            growthProfile = "pitax-wartime",
            armyGrowthPerTurn = 2.0,
        )
        val profile = realm.resolveGrowthProfile(presets)
        assertEquals(0.33, profile.sizePerTurn)
        assertEquals(0.5, profile.famePerTurn)
        assertEquals(2.0, profile.armyPerTurn)
    }

    @Test
    fun anUnknownProfileIdLeavesTheRealmDormant() {
        val realm = RawRivalRealm(id = "r1", factionRef = "Pitax", growthProfile = "no-such-preset")
        val profile = realm.resolveGrowthProfile(presets)
        assertEquals(0.0, profile.sizePerTurn)
        assertEquals(0.0, profile.famePerTurn)
        assertEquals(0.0, profile.armyPerTurn)
    }

    @Test
    fun anUnknownGrowthModeReadsAsFlat() {
        assertFalse(RawRivalRealm(id = "r1", factionRef = "Pitax").isAgendaDriven())
        assertFalse(RawRivalRealm(id = "r1", factionRef = "Pitax", growthMode = "agendaDriven").isAgendaDriven())
        assertTrue(RawRivalRealm(id = "r1", factionRef = "Pitax", growthMode = "agenda-driven").isAgendaDriven())
    }
}
