package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.migrations.migrations.Migration19
import at.posselt.pfrpg2e.camping.RestSettings
import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Migration19Test {

    @Test
    fun testMigration19BackfillsAllCampingFields() = runTest {
        val gameMock = unsafeJso<Game>()
        val camping = unsafeJso<dynamic> {}

        Migration19().migrateCamping(gameMock, camping)

        assertEquals(true, camping.autoApplyFatigued.unsafeCast<Boolean>())
        assertNotNull(camping.restSettings)
        val rs = camping.restSettings.unsafeCast<dynamic>()
        assertEquals(false, rs.skipWatch.unsafeCast<Boolean>())
        assertEquals(false, rs.skipDailyPreparations.unsafeCast<Boolean>())
        assertEquals(false, rs.disableRandomEncounter.unsafeCast<Boolean>())
        assertEquals(false, rs.skipWeather.unsafeCast<Boolean>())
        assertEquals(0, camping.secondsSpentTraveling.unsafeCast<Int>())
        assertEquals(0, camping.secondsSpentHexploring.unsafeCast<Int>())
        assertEquals(true, camping.resetTimeTrackingAfterOneDay.unsafeCast<Boolean>())
        assertEquals(false, camping.travelModeActive.unsafeCast<Boolean>())
    }

    @Test
    fun testMigration19PreservesExistingValues() = runTest {
        val gameMock = unsafeJso<Game>()
        val camping = unsafeJso<dynamic> {
            autoApplyFatigued = false
            restSettings = unsafeJso<dynamic> { skipWatch = true }
            secondsSpentTraveling = 3600
            secondsSpentHexploring = 7200
            resetTimeTrackingAfterOneDay = false
            travelModeActive = true
        }

        Migration19().migrateCamping(gameMock, camping)

        assertEquals(false, camping.autoApplyFatigued.unsafeCast<Boolean>())
        assertEquals(true, camping.restSettings.unsafeCast<dynamic>().skipWatch.unsafeCast<Boolean>())
        assertEquals(3600, camping.secondsSpentTraveling.unsafeCast<Int>())
        assertEquals(7200, camping.secondsSpentHexploring.unsafeCast<Int>())
        assertEquals(false, camping.resetTimeTrackingAfterOneDay.unsafeCast<Boolean>())
        assertEquals(true, camping.travelModeActive.unsafeCast<Boolean>())
    }

    @Test
    fun testMigration19HandlesNullRestSettings() = runTest {
        val gameMock = unsafeJso<Game>()
        val camping = unsafeJso<dynamic> {
            restSettings = null
        }

        Migration19().migrateCamping(gameMock, camping)

        val rs = camping.restSettings.unsafeCast<dynamic>()
        assertEquals(false, rs.skipWatch.unsafeCast<Boolean>())
        assertEquals(false, rs.skipDailyPreparations.unsafeCast<Boolean>())
        assertEquals(false, rs.disableRandomEncounter.unsafeCast<Boolean>())
        assertEquals(false, rs.skipWeather.unsafeCast<Boolean>())
    }
}