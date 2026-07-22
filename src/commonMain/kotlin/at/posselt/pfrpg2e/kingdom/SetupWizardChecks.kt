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

/** One evaluated setup check with a stable id (for i18n) and an optional fix hint id. */
data class SetupCheck(
    val id: String,
    val status: CheckStatus,
    val fixHintId: String? = null,
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
)

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
    ),
    SetupCheck(
        id = "map-scenes",
        status = if (state.campaignMapScenesConfigured) CheckStatus.PASS else CheckStatus.WARN,
        fixHintId = if (state.campaignMapScenesConfigured) null else "setupWizard.fix.mapScenes",
    ),
)

/** The world is ready when no check FAILs (warnings are acceptable, the module still runs). */
fun setupIsReady(checks: List<SetupCheck>): Boolean =
    checks.none { it.status == CheckStatus.FAIL }
