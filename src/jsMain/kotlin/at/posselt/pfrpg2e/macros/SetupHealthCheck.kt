package at.posselt.pfrpg2e.macros

import at.posselt.pfrpg2e.kingdom.postSetupHealthCheck
import com.foundryvtt.core.Game

/**
 * Run the module setup health check and whisper the GM the result.
 *
 * Re-runnable on purpose: the dependency problems it reports (libWrapper missing, Seasons & Stars
 * without the Simple Calendar bridge) are the known causes of a rest silently failing to advance
 * the world clock, and they can appear at any point when a module is disabled or updated.
 */
suspend fun setupHealthCheckMacro(game: Game) {
    game.postSetupHealthCheck()
}
