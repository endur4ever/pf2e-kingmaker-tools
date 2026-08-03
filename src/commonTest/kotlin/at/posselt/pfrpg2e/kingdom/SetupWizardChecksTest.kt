package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetupWizardChecksTest {
    private val fullyConfigured = SetupState(
        kingmakerModuleActive = true,
        libWrapperActive = true,
        seasonsAndStarsPresent = true,
        simpleCalendarBridgeActive = true,
        hasPartyActor = true,
        kingdomConfigured = true,
        campaignMapScenesConfigured = true,
    )

    private fun status(checks: List<SetupCheck>, id: String) = checks.first { it.id == id }.status

    @Test
    fun aFullyConfiguredWorldIsAllPassAndReady() {
        val checks = evaluateSetupChecks(fullyConfigured)
        assertTrue(checks.all { it.status == CheckStatus.PASS })
        assertTrue(setupIsReady(checks))
    }

    @Test
    fun missingHardDependenciesFailAndBlockReadiness() {
        val checks = evaluateSetupChecks(fullyConfigured.copy(kingmakerModuleActive = false, libWrapperActive = false))
        assertEquals(CheckStatus.FAIL, status(checks, "kingmaker-module"))
        assertEquals(CheckStatus.FAIL, status(checks, "libwrapper"))
        assertFalse(setupIsReady(checks))
        assertEquals("setupWizard.fix.kingmakerModule", checks.first { it.id == "kingmaker-module" }.fixHintId)
    }

    @Test
    fun calendarBridgeOnlyWarnsWhenSeasonsAndStarsIsPresentWithoutTheBridge() {
        val withSs = evaluateSetupChecks(fullyConfigured.copy(simpleCalendarBridgeActive = false))
        assertEquals(CheckStatus.WARN, status(withSs, "calendar-bridge"))
        // no S&S at all -> nothing to bridge -> PASS
        val noSs = evaluateSetupChecks(fullyConfigured.copy(seasonsAndStarsPresent = false, simpleCalendarBridgeActive = false))
        assertEquals(CheckStatus.PASS, status(noSs, "calendar-bridge"))
    }

    @Test
    fun missingPartyActorFailsButMissingKingdomOnlyWarns() {
        val checks = evaluateSetupChecks(fullyConfigured.copy(hasPartyActor = false, kingdomConfigured = false))
        assertEquals(CheckStatus.FAIL, status(checks, "party-actor"))
        assertEquals(CheckStatus.WARN, status(checks, "kingdom"))
        assertFalse(setupIsReady(checks))  // the party-actor FAIL blocks
    }

    @Test
    fun warningsAloneStillCountAsReady() {
        val checks = evaluateSetupChecks(fullyConfigured.copy(kingdomConfigured = false, campaignMapScenesConfigured = false))
        assertTrue(checks.any { it.status == CheckStatus.WARN })
        assertTrue(setupIsReady(checks))  // no FAIL -> ready
    }
}
