package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.actor.isKingmakerInstalled
import at.posselt.pfrpg2e.camping.getCampingActors
import at.posselt.pfrpg2e.settings.Pfrpg2eKingdomCampingWeatherSettings
import at.posselt.pfrpg2e.utils.postChatTemplate
import com.foundryvtt.core.helpers.simpleCalendarOrNull
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import js.objects.recordOf

/**
 * Runnable health check for a world's module setup.
 *
 * The pure [evaluateSetupChecks] shipped with tests and no caller, so nothing ever ran it. This
 * probes the live world into a [SetupState], evaluates it, and whispers the GM a pass/warn/fail
 * report with a one-line fix for anything wrong.
 *
 * Deliberately a re-runnable health check rather than a one-shot first-run wizard: the two
 * conditions it exists to catch — libWrapper missing, and Seasons & Stars without the Simple
 * Calendar bridge — are the known causes of a rest silently failing to advance the world clock, and
 * a GM hits those long after first run, when a module is disabled or updated.
 *
 * See card t_1691834f.
 */

private const val LIB_WRAPPER_MODULE_ID = "lib-wrapper"
private const val SEASONS_AND_STARS_MODULE_ID = "seasons-and-stars"

private fun Game.moduleActive(id: String): Boolean = modules.get(id)?.active == true

/** Probe the live world. Read-only. */
fun Game.probeSetupState(): SetupState = SetupState(
    kingmakerModuleActive = isKingmakerInstalled,
    libWrapperActive = moduleActive(LIB_WRAPPER_MODULE_ID),
    seasonsAndStarsPresent = moduleActive(SEASONS_AND_STARS_MODULE_ID),
    // A present SimpleCalendar global is what the bridge provides; checking the global rather than
    // the module id means a real Simple Calendar install also counts, which it should.
    simpleCalendarBridgeActive = simpleCalendarOrNull() != null,
    hasPartyActor = getCampingActors().isNotEmpty(),
    kingdomConfigured = getKingdomActors().isNotEmpty(),
    campaignMapScenesConfigured = Pfrpg2eKingdomCampingWeatherSettings
        .getCampaignMapSceneIds()
        .split(',')
        .any { it.isNotBlank() },
)

/** GM-only: run the checks against the live world and whisper the report. */
suspend fun Game.postSetupHealthCheck() {
    if (!user.isGM) return
    val gmUserIds = users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    // An empty whisper array posts publicly rather than to nobody.
    if (gmUserIds.isEmpty()) return
    val checks = evaluateSetupChecks(probeSetupState())
    val rows = checks.map { check ->
        recordOf(
            "id" to check.id,
            "status" to check.status.name.lowercase(),
            "label" to t("setupWizard.check.${check.id}"),
            "fix" to (check.fixHintId?.let { t(it) } ?: ""),
        )
    }.toTypedArray()
    postChatTemplate(
        templatePath = "chatmessages/setup-health-check.hbs",
        templateContext = recordOf(
            "checks" to rows,
            "ready" to setupIsReady(checks),
        ),
        whisper = gmUserIds,
    )
}
