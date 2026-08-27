package at.posselt.pfrpg2e

import at.posselt.pfrpg2e.actions.ActionDispatcher
import at.posselt.pfrpg2e.actions.handlers.AddHuntAndGatherResultHandler
import at.posselt.pfrpg2e.actions.handlers.ApplyMealEffectsHandler
import at.posselt.pfrpg2e.actions.handlers.ClearMealEffectsHandler
import at.posselt.pfrpg2e.actions.handlers.ApplyStarvationHandler
import at.posselt.pfrpg2e.actions.handlers.GainProvisionsHandler
import at.posselt.pfrpg2e.actions.handlers.LearnSpecialRecipeHandler
import at.posselt.pfrpg2e.actions.handlers.OpenCampingSheetHandler
import at.posselt.pfrpg2e.actions.handlers.OpenKingdomSheetHandler
import at.posselt.pfrpg2e.actions.handlers.CastCouncilVoteHandler
import at.posselt.pfrpg2e.actions.handlers.CloseCouncilVoteHandler
import at.posselt.pfrpg2e.actions.handlers.DeleteCouncilVoteHandler
import at.posselt.pfrpg2e.actions.handlers.OpenCouncilVoteHandler
import at.posselt.pfrpg2e.actions.handlers.ReopenCouncilVoteHandler
import at.posselt.pfrpg2e.actions.handlers.SetCouncilVoteLinksHandler
import at.posselt.pfrpg2e.actions.handlers.SetCouncilVoteNoteHandler
import at.posselt.pfrpg2e.actions.handlers.SyncActivitiesHandler
import at.posselt.pfrpg2e.actions.handlers.SyncBattleOutcomeHandler
import at.posselt.pfrpg2e.actor.partyMembers
import at.posselt.pfrpg2e.camping.CampingActor
import at.posselt.pfrpg2e.camping.beginRest
import at.posselt.pfrpg2e.camping.bindCampingChatEventListeners
import at.posselt.pfrpg2e.camping.getCampingActors
import at.posselt.pfrpg2e.camping.openOrCreateCampingSheet
import at.posselt.pfrpg2e.camping.registerActivityDiffingHooks
import at.posselt.pfrpg2e.camping.registerCampingTokenMove
import at.posselt.pfrpg2e.camping.registerFatiguedHooks
import at.posselt.pfrpg2e.kingdom.registerDailyTickHooks
import at.posselt.pfrpg2e.camping.registerMealDiffingHooks
import at.posselt.pfrpg2e.camping.updateCampingRegion
import at.posselt.pfrpg2e.combat.registerCombatTrackHooks
import at.posselt.pfrpg2e.combat.registerCombatXpHooks
import at.posselt.pfrpg2e.firstrun.showFirstRunMessage
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.getKingdomActors
import at.posselt.pfrpg2e.kingdom.armies.createArmyCompendiumEntries
import at.posselt.pfrpg2e.kingdom.armies.registerArmyConsumptionHooks
import at.posselt.pfrpg2e.kingdom.bindChatButtons
import at.posselt.pfrpg2e.kingdom.bindSetupHealthCheckButtons
import at.posselt.pfrpg2e.kingdom.warnIfCalendarNotesUnsupported
import at.posselt.pfrpg2e.kingdom.registerContextMenus
import at.posselt.pfrpg2e.kingdom.map.registerHexGridSync
import at.posselt.pfrpg2e.kingdom.map.registerHexContentSync
import at.posselt.pfrpg2e.kingdom.map.syncHexContentMarkers
import at.posselt.pfrpg2e.kingdom.map.syncHexDrawingsToNativeState
import at.posselt.pfrpg2e.kingdom.map.syncSettlementMarkers
import at.posselt.pfrpg2e.kingdom.map.syncZoneLabels
import at.posselt.pfrpg2e.kingdom.sheet.openOrCreateKingdomSheet
import at.posselt.pfrpg2e.kingdom.structures.validateStructures
import at.posselt.pfrpg2e.macros.awardHeroPointsMacro
import at.posselt.pfrpg2e.macros.awardXPMacro
import at.posselt.pfrpg2e.macros.chooseParty
import at.posselt.pfrpg2e.macros.combatTrackMacro
import at.posselt.pfrpg2e.macros.createFoodMacro
import at.posselt.pfrpg2e.macros.editRealmTileMacro
import at.posselt.pfrpg2e.macros.editStructureMacro
import at.posselt.pfrpg2e.macros.exportActiveGearSettingsProfileMacro
import at.posselt.pfrpg2e.macros.importGearSettingsProfileMacro
import at.posselt.pfrpg2e.macros.manageGearSettingsProfilesMacro
import at.posselt.pfrpg2e.macros.manageHomebrewProfilesMacro
import at.posselt.pfrpg2e.macros.restoreMigrationBackupMacro
import at.posselt.pfrpg2e.macros.setupHealthCheckMacro
import at.posselt.pfrpg2e.macros.resetHeroPointsMacro
import at.posselt.pfrpg2e.macros.rollExplorationSkillCheckMacro
import at.posselt.pfrpg2e.macros.rollPartyCheckMacro
import at.posselt.pfrpg2e.macros.sceneWeatherSettingsMacro
import at.posselt.pfrpg2e.macros.setTimeOfDayMacro
import at.posselt.pfrpg2e.macros.setWeatherMacro
import at.posselt.pfrpg2e.macros.showAllNpcHpBars
import at.posselt.pfrpg2e.macros.subsistMacro
import at.posselt.pfrpg2e.macros.toggleCombatTracksMacro
import at.posselt.pfrpg2e.macros.toggleShelteredMacro
import at.posselt.pfrpg2e.macros.toggleWeatherMacro
import at.posselt.pfrpg2e.migrations.migratePfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.utils.Pfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.utils.ToolsMacros
import at.posselt.pfrpg2e.utils.ToolsScripts
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.fixVisibility
import at.posselt.pfrpg2e.utils.initLocalization
import at.posselt.pfrpg2e.utils.loadTemplatePartials
import at.posselt.pfrpg2e.utils.pf2eKingmakerTools
import at.posselt.pfrpg2e.utils.registerIcons
import at.posselt.pfrpg2e.utils.registerMacroDropHooks
import at.posselt.pfrpg2e.utils.registerTouchDragGuard
import at.posselt.pfrpg2e.utils.registerTokenMappings
import at.posselt.pfrpg2e.weather.registerWeatherHooks
import at.posselt.pfrpg2e.weather.rollWeather
import com.foundryvtt.core.game
import com.foundryvtt.core.helpers.TypedHooks
import com.foundryvtt.core.helpers.onI18NInit
import com.foundryvtt.core.helpers.onInit
import com.foundryvtt.core.helpers.onReady
import com.foundryvtt.core.helpers.onRenderChatLog
import com.foundryvtt.core.helpers.onRenderChatMessage

fun main() {
    TypedHooks.onInit {
        val actionDispatcher = ActionDispatcher(
            game = game,
            handlers = listOf(
                AddHuntAndGatherResultHandler(),
                OpenCampingSheetHandler(game = game),
                SyncActivitiesHandler(game = game),
                SyncBattleOutcomeHandler(game = game),
                ClearMealEffectsHandler(),
                LearnSpecialRecipeHandler(),
                ApplyMealEffectsHandler(game = game),
                GainProvisionsHandler(),
            ApplyStarvationHandler(),
                OpenKingdomSheetHandler(game = game),
                CastCouncilVoteHandler(),
                CloseCouncilVoteHandler(),
                ReopenCouncilVoteHandler(),
                OpenCouncilVoteHandler(game = game),
                DeleteCouncilVoteHandler(),
                SetCouncilVoteNoteHandler(),
                SetCouncilVoteLinksHandler(),
            )
        ).apply {
            listen()
        }

        TypedHooks.onI18NInit {
            initLocalization()
            game.settings.pfrpg2eKingdomCampingWeather.register()
            registerContextMenus()
            registerTokenMappings(game)
            registerWeatherHooks(game)
            registerCombatTrackHooks(game)
            registerMealDiffingHooks()
            registerArmyConsumptionHooks(game)
            registerIcons(actionDispatcher)
            registerCombatXpHooks(game)
            registerFatiguedHooks(game)
            registerDailyTickHooks(game)
        }

        bindChatButtons(game, actionDispatcher)
        bindSetupHealthCheckButtons(game, actionDispatcher)
        registerMacroDropHooks(game)

        buildPromise {
            // register partials
            loadTemplatePartials(
                arrayOf(
                    "kingdom-activities" to "applications/kingdom/activities.hbs",
                    "kingdom-events" to "applications/kingdom/events.hbs",
                    "kingdom-quest-generator" to "applications/kingdom/quest-generator.hbs",
                    "kingdom-trade-agreements" to "applications/kingdom/sections/trade-agreements/page.hbs",
                    "kingdom-settlements" to "applications/kingdom/sections/settlements/page.hbs",
                    "kingdom-turn" to "applications/kingdom/sections/turn/page.hbs",
                    "kingdom-modifiers" to "applications/kingdom/sections/modifiers/page.hbs",
                    "kingdom-notes" to "applications/kingdom/sections/notes/page.hbs",
                    "kingdom-quests" to "applications/kingdom/sections/quests/page.hbs",
                    "kingdom-roster" to "applications/kingdom/sections/roster/page.hbs",
                    "kingdom-party" to "applications/kingdom/sections/party/page.hbs",
                    "kingdom-army-pressure" to "applications/kingdom/sections/army-pressure/page.hbs",
                    "kingdom-resolve-battle" to "applications/kingdom/dialogs/resolve-battle.hbs",
                    "kingdom-pacing-alerts" to "applications/kingdom/sections/pacing-alerts/page.hbs",
                    "kingdom-session-prep" to "applications/kingdom/sections/session-prep/page.hbs",
                    "kingdom-council-votes" to "applications/kingdom/sections/council-votes/page.hbs",
                    "kingdom-analytics" to "applications/kingdom/sections/analytics/page.hbs",
                    "kingdom-expeditions" to "applications/kingdom/sections/expeditions/page.hbs",
                    "kingdom-campaign" to "applications/kingdom/sections/clocks/page.hbs",
                    "kingdom-metric-chart" to "applications/kingdom/sections/analytics/metric-chart.hbs",
                    "kingdom-character-sheet" to "applications/kingdom/sections/character-sheet/page.hbs",
                    "kingdom-character-sheet-creation" to "applications/kingdom/sections/character-sheet/creation.hbs",
                    "kingdom-character-sheet-bonus" to "applications/kingdom/sections/character-sheet/bonus.hbs",
                    "kingdom-character-sheet-levels" to "applications/kingdom/sections/character-sheet/levels.hbs",
                    "campingTile" to "applications/camping/camping-tile.hbs",
                    "recipeTile" to "applications/camping/recipe-tile.hbs",
                    "downtimeProjects" to "applications/camping/downtime-projects.hbs",
                    "formElement" to "components/forms/form-element.hbs",
                    "tabs" to "components/tabs/tabs.hbs",
                    "foodCost" to "components/food-cost/food-cost.hbs",
                    "skillPickerInput" to "components/skill-picker/skill-picker-input.hbs",
                    "activityEffectsInput" to "components/activity-effects/activity-effects-input.hbs",
                    "companionQuestCard" to "applications/kingdom/companion-quest-card.hbs",
                    "companionQuestRow" to "applications/kingdom/companion-quest-row.hbs",
                )
            )
        }

        game.pf2eKingmakerTools = Pfrpg2eKingdomCampingWeather(
            scripts = ToolsScripts(
                updateCampingRegion = ::updateCampingRegion
            ),
            macros = ToolsMacros(
                toggleWeatherMacro = { buildPromise { toggleWeatherMacro(game) } },
                toggleShelteredMacro = { buildPromise { toggleShelteredMacro(game) } },
                setCurrentWeatherMacro = { buildPromise { setWeatherMacro(game) } },
                sceneWeatherSettingsMacro = {
                    buildPromise<Unit> {
                        game.scenes.active?.let {
                            sceneWeatherSettingsMacro(it)
                        }
                    }
                },
                openSheet = { type, id ->
                    buildPromise {
                        when (type) {
                            "camping" -> {
                                game.actors.get(id)
                                    ?.takeIfInstance<CampingActor>()
                                    ?.let { actor -> openOrCreateCampingSheet(game, actionDispatcher, actor) }
                            }

                            "kingdom" -> {
                                game.actors.get(id)
                                    ?.takeIfInstance<KingdomActor>()
                                    ?.let { actor -> openOrCreateKingdomSheet(game, actionDispatcher, actor) }
                            }
                        }
                    }
                },
                rollKingmakerWeatherMacro = { buildPromise { rollWeather(game) } },
                awardXpMacro = { buildPromise { awardXPMacro(game) } },
                resetHeroPointsMacro = {
                    buildPromise {
                        val players = chooseParty(game).partyMembers()
                        resetHeroPointsMacro(players)
                    }
                },
                awardHeroPointsMacro = {
                    buildPromise {
                        val players = chooseParty(game).partyMembers()
                        awardHeroPointsMacro(players)
                    }
                },
                rollExplorationSkillCheck = { skill, effect ->
                    buildPromise {
                        rollExplorationSkillCheckMacro(
                            game,
                            attributeName = skill,
                            explorationEffectName = effect,
                        )
                    }
                },
                rollSkillDialog = {
                    buildPromise {
                        rollPartyCheckMacro(chooseParty(game).partyMembers())
                    }
                },
                setSceneCombatPlaylistDialogMacro = { actor -> buildPromise { combatTrackMacro(game, actor) } },
                toTimeOfDayMacro = { buildPromise { setTimeOfDayMacro(game) } },
                toggleCombatTracksMacro = { buildPromise { toggleCombatTracksMacro(game) } },
                realmTileDialogMacro = { buildPromise { editRealmTileMacro(game) } },
                editStructureMacro = { actor -> buildPromise { editStructureMacro(actor) } },
                subsistMacro = { actor -> buildPromise { subsistMacro(game, actor) } },
                createFoodMacro = { buildPromise { createFoodMacro(game, actionDispatcher) } },
                showAllNpcHpBarsMacro = { buildPromise { game.showAllNpcHpBars() }},
                manageGearSettingsProfilesMacro = { buildPromise { manageGearSettingsProfilesMacro() } },
                manageHomebrewProfilesMacro = { buildPromise { manageHomebrewProfilesMacro(game) } },
                setupHealthCheckMacro = { buildPromise { setupHealthCheckMacro(game) } },
                restoreMigrationBackupMacro = { buildPromise { restoreMigrationBackupMacro(game) } },
                importGearSettingsProfileMacro = { buildPromise { importGearSettingsProfileMacro(game) } },
                exportActiveGearSettingsProfileMacro = { buildPromise { exportActiveGearSettingsProfileMacro(game) } },
                restMacro = { actorUuid ->
                    game.getCampingActors()
                        .find { it.uuid == actorUuid }
                        ?.let { beginRest(it, actionDispatcher) }
                }
            ),
        )

        // Insulated registration: the big onReady block below can abort partway on some worlds
        // (a sibling throws), which previously swallowed the last few registrations. Keep the
        // native hex-editor link panel in its own onReady so it always wires up.
        TypedHooks.onReady {
            at.posselt.pfrpg2e.kingdom.map.registerHexEditorLinks(game)
        }

        // Insulated: silence the upstream Foundry/PF2e touch drag-cancel crash
        // (TokenPF2e._finalizeDragLeft → Object.values(undefined)) on mobile/touch.
        TypedHooks.onReady {
            registerTouchDragGuard()
        }

        // Insulated: re-apply the saved Seasons & Stars calendar when S&S 0.26 fell back to
        // Gregorian because a pack calendar (e.g. PF2e Golarion) finished its async load after
        // S&S's setup-time restore — otherwise dates, weather seasons and calendar logging are wrong.
        TypedHooks.onReady {
            at.posselt.pfrpg2e.utils.fixSeasonsStarsActiveCalendar()
        }

        // Insulated: Seasons & Stars without the Simple Calendar Compatibility Bridge silently
        // drops all our calendar notes. Warn the GM once (own onReady so it always runs).
        TypedHooks.onReady {
            buildPromise { game.warnIfCalendarNotesUnsupported() }
        }

        TypedHooks.onReady {
            buildPromise {
                game.migratePfrpg2eKingdomCampingWeather()
                registerActivityDiffingHooks(game, actionDispatcher)
                showFirstRunMessage(game)
                createArmyCompendiumEntries(game)
                validateStructures(game)
                registerCampingTokenMove(game)
                registerHexGridSync(game)
                registerHexContentSync(game)
                at.posselt.pfrpg2e.kingdom.map.registerSelectedHexTracker()
                // Initial overlay draw on load. These create/delete Scene Drawing documents, which
                // only the GM may do (Foundry replicates them to players), so gate the same way the
                // sync hooks are gated — otherwise a non-GM client throws "User X lacks permission
                // to create Drawing in parent Scene Y" on every load.
                if (game.user.isGM) {
                    syncHexDrawingsToNativeState(game)
                    syncSettlementMarkers(game)
                    syncZoneLabels(game)
                    at.posselt.pfrpg2e.kingdom.map.syncExpeditionMarkers(game)
                    game.getKingdomActors().forEach { actor ->
                        at.posselt.pfrpg2e.kingdom.map.syncHexContentMarkers(game, actor)
                    }
                }
            }
        }

        TypedHooks.onRenderChatMessage { message, html, _ ->
            fixVisibility(game, html, message)
        }

        TypedHooks.onRenderChatLog { _, _, _ ->
            bindCampingChatEventListeners(game, actionDispatcher)
        }
    }
}