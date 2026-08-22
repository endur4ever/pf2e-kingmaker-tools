package at.posselt.pfrpg2e.kingdom

/**
 * Pure health-check logic for the mid-campaign adoption/setup wizard. A GM adopting the module gets
 * no guided setup today; this evaluates the live world state into pass/warn/fail rows with one-line
 * fixes, and decides whether the world is ready. The wizard reads live state each open (stateless
 * health check), so this is a pure function of a state snapshot; the ApplicationV2 dialog, the live
 * state probing, and the per-step action buttons are the deferred jsMain wiring.
 *
 * See card t_1691834f.
 */

/** A single setup check's severity. FAIL blocks a healthy setup; WARN is advisory. */
enum class CheckStatus { PASS, WARN, FAIL }

/**
 * One evaluated setup check with a stable id (for i18n) and an optional fix hint id.
 *
 * [actionId] names a one-click fix the report can offer as a button. It is only ever set on a check
 * that is not passing: offering to "fix" something already correct invites a GM to overwrite work
 * they meant to keep. Checks whose fix is installing or enabling another module have no action,
 * because nothing this module can do from a button resolves them.
 */
data class SetupCheck(
    val id: String,
    val status: CheckStatus,
    val fixHintId: String? = null,
    val actionId: String? = null,
)

/** A snapshot of the live world state the wizard probes (assembled in jsMain). */
data class SetupState(
    val kingmakerModuleActive: Boolean,
    val libWrapperActive: Boolean,
    val seasonsAndStarsPresent: Boolean,
    /** Only meaningful when [seasonsAndStarsPresent]; the Simple Calendar compat bridge. */
    val simpleCalendarBridgeActive: Boolean,
    val hasPartyActor: Boolean,
    val kingdomConfigured: Boolean,
    val campaignMapScenesConfigured: Boolean,
    /**
     * How many months the climate table holds. Weather rolls read one row per month of the
     * calendar year, so any count other than [MONTHS_IN_A_YEAR] leaves months unrollable.
     */
    val climateMonthCount: Int,
)

/** A Golarion year, and therefore the number of rows a complete climate table needs. */
const val MONTHS_IN_A_YEAR = 12

/**
 * Evaluate the world into ordered setup checks. Hard preconditions fail; nice-to-haves warn:
 * - `kingmaker-module` / `libwrapper` are FAIL when missing (the known silent-rest-failure causes).
 * - `calendar-bridge` WARNs only when Seasons & Stars is present but its Simple Calendar bridge is
 *   off (the silent calendar-notes precondition); it PASSes when S&S is absent (nothing to bridge).
 * - `party-actor` is FAIL (nothing works without one); `kingdom` and `map-scenes` WARN.
 */
fun evaluateSetupChecks(state: SetupState): List<SetupCheck> = listOf(
    SetupCheck(
        id = "kingmaker-module",
        status = if (state.kingmakerModuleActive) CheckStatus.PASS else CheckStatus.FAIL,
        fixHintId = if (state.kingmakerModuleActive) null else "setupWizard.fix.kingmakerModule",
    ),
    SetupCheck(
        id = "libwrapper",
        status = if (state.libWrapperActive) CheckStatus.PASS else CheckStatus.FAIL,
        fixHintId = if (state.libWrapperActive) null else "setupWizard.fix.libWrapper",
    ),
    SetupCheck(
        id = "calendar-bridge",
        status = when {
            !state.seasonsAndStarsPresent -> CheckStatus.PASS  // nothing to bridge
            state.simpleCalendarBridgeActive -> CheckStatus.PASS
            else -> CheckStatus.WARN
        },
        fixHintId = if (state.seasonsAndStarsPresent && !state.simpleCalendarBridgeActive) {
            "setupWizard.fix.calendarBridge"
        } else {
            null
        },
    ),
    SetupCheck(
        id = "party-actor",
        status = if (state.hasPartyActor) CheckStatus.PASS else CheckStatus.FAIL,
        fixHintId = if (state.hasPartyActor) null else "setupWizard.fix.partyActor",
    ),
    SetupCheck(
        id = "kingdom",
        status = if (state.kingdomConfigured) CheckStatus.PASS else CheckStatus.WARN,
        fixHintId = if (state.kingdomConfigured) null else "setupWizard.fix.kingdom",
        actionId = if (state.kingdomConfigured) null else "create-kingdom",
    ),
    SetupCheck(
        id = "map-scenes",
        status = if (state.campaignMapScenesConfigured) CheckStatus.PASS else CheckStatus.WARN,
        fixHintId = if (state.campaignMapScenesConfigured) null else "setupWizard.fix.mapScenes",
        actionId = if (state.campaignMapScenesConfigured) null else "pick-map-scenes",
    ),
    // A climate table is seeded with the Stolen Lands months, so a wrong count means it was edited
    // into an unusable shape rather than simply never set up. Any count is left alone otherwise: a
    // GM who retuned the DCs for their own region has a valid table, not a broken one.
    SetupCheck(
        id = "climate",
        status = if (state.climateMonthCount == MONTHS_IN_A_YEAR) CheckStatus.PASS else CheckStatus.WARN,
        fixHintId = if (state.climateMonthCount == MONTHS_IN_A_YEAR) null else "setupWizard.fix.climate",
        actionId = if (state.climateMonthCount == MONTHS_IN_A_YEAR) null else "restore-climate",
    ),
)

/** The world is ready when no check FAILs (warnings are acceptable, the module still runs). */
fun setupIsReady(checks: List<SetupCheck>): Boolean =
    checks.none { it.status == CheckStatus.FAIL }
