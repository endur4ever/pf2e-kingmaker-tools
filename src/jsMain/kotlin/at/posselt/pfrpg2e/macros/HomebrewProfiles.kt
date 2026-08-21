package at.posselt.pfrpg2e.macros

import at.posselt.pfrpg2e.kingdom.dialogs.HomebrewProfileManagerApplication
import at.posselt.pfrpg2e.utils.launch
import com.foundryvtt.core.Game

/**
 * Open the CRUD dialog for importing, exporting, activating and deleting homebrew rule profiles.
 *
 * The dialog and its whole import/export pipeline shipped without a single caller: nothing in the
 * module referenced HomebrewProfileManagerApplication, so there was no way for a GM to open it. Its
 * gear-settings counterpart has had a macro entry point all along; this is the matching one.
 *
 * GM-only: importing a profile writes the world-level registry and activates the imported rules for
 * the whole table.
 */
suspend fun manageHomebrewProfilesMacro(game: Game) {
    if (!game.user.isGM) return
    HomebrewProfileManagerApplication(game).launch()
}
