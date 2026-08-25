package at.posselt.pfrpg2e.kingdom.sheet

import at.posselt.pfrpg2e.actions.ActionDispatcher
import at.posselt.pfrpg2e.actions.ActionMessage
import at.posselt.pfrpg2e.actions.handlers.OpenKingdomSheetAction
import at.posselt.pfrpg2e.actions.handlers.SyncBattleOutcomeAction
import at.posselt.pfrpg2e.actor.openActor
import at.posselt.pfrpg2e.actor.ownershipOwnersOnly
import at.posselt.pfrpg2e.actor.partyMembers
import at.posselt.pfrpg2e.app.ActorRef
import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.MenuControl
import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.forms.HiddenInput
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.app.forms.OverrideType
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.app.forms.TextInput
import at.posselt.pfrpg2e.data.checks.RollMode
import at.posselt.pfrpg2e.data.events.KingdomEventTrait
import at.posselt.pfrpg2e.data.kingdom.KingdomSkill
import at.posselt.pfrpg2e.data.kingdom.Relations
import at.posselt.pfrpg2e.data.kingdom.applyStandingDelta
import at.posselt.pfrpg2e.data.kingdom.calculateControlDC
import at.posselt.pfrpg2e.data.kingdom.calculateHexXP
import at.posselt.pfrpg2e.data.kingdom.calculateRpXP
import at.posselt.pfrpg2e.data.kingdom.findKingdomSize
import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementLayoutType
import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementLevelUpType
import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementType
import at.posselt.pfrpg2e.kingdom.AutomateResources
import at.posselt.pfrpg2e.kingdom.COMPLETED_QUESTS_DEFAULT_LIMIT
import at.posselt.pfrpg2e.kingdom.CompletedQuestEntry
import at.posselt.pfrpg2e.kingdom.CompletedQuestFilter
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.filterCompletedQuests
import at.posselt.pfrpg2e.kingdom.pageCompletedQuests
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.offerDefeatConsequences
import at.posselt.pfrpg2e.kingdom.accessGrantList
import at.posselt.pfrpg2e.kingdom.toRawAccessGrants
import at.posselt.pfrpg2e.kingdom.toAccessGrant
import at.posselt.pfrpg2e.kingdom.withoutQuest
import at.posselt.pfrpg2e.kingdom.armies.getSelectedArmies
import at.posselt.pfrpg2e.kingdom.offerDeployArmyOutcome
import at.posselt.pfrpg2e.kingdom.offerWarVictory
import at.posselt.pfrpg2e.kingdom.RawCouncilCooldowns
import at.posselt.pfrpg2e.kingdom.RawEq
import at.posselt.pfrpg2e.kingdom.RawModifier
import at.posselt.pfrpg2e.kingdom.RawOngoingKingdomEvent
import at.posselt.pfrpg2e.kingdom.RawSome
import at.posselt.pfrpg2e.kingdom.SettlementTerrain
import at.posselt.pfrpg2e.kingdom.armies.setupArmies
import at.posselt.pfrpg2e.kingdom.armies.updateArmyConsumption
import at.posselt.pfrpg2e.kingdom.TurnTickingEngine
import at.posselt.pfrpg2e.kingdom.BASE_ABILITY_BOOSTS
import at.posselt.pfrpg2e.kingdom.vkExtraAbilityBoosts
import at.posselt.pfrpg2e.kingdom.vkInitialSkillSlots
import at.posselt.pfrpg2e.campaign.CampaignClockManager
import at.posselt.pfrpg2e.kingdom.dialogs.importTurnHistoryDialog
import at.posselt.pfrpg2e.kingdom.dialogs.CampaignClockDialog
import at.posselt.pfrpg2e.kingdom.sheet.contexts.CampaignClockContext
import at.posselt.pfrpg2e.kingdom.extractSeries
import at.posselt.pfrpg2e.kingdom.summarizeSeries
import at.posselt.pfrpg2e.kingdom.mapSeriesToCoordinates
import at.posselt.pfrpg2e.kingdom.pacingChapterTargetLevel
import at.posselt.pfrpg2e.kingdom.pacingLevelMismatchRange
import at.posselt.pfrpg2e.kingdom.sheet.contexts.AnalyticsContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.MetricPointContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.filterAnalyticsMetricsForUser
import at.posselt.pfrpg2e.kingdom.sheet.contexts.MetricSeriesContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.ThresholdLineContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.toDashboardContext
import at.posselt.pfrpg2e.kingdom.createModifiers
import at.posselt.pfrpg2e.kingdom.createSimpleContext
import at.posselt.pfrpg2e.kingdom.data.RawBonusFeat
import at.posselt.pfrpg2e.kingdom.data.RawQuest
import at.posselt.pfrpg2e.kingdom.data.RawQuestRewards
import at.posselt.pfrpg2e.kingdom.data.RawCommodities
import at.posselt.pfrpg2e.kingdom.data.RawQuestCompletionSnapshot
import at.posselt.pfrpg2e.kingdom.data.limitBy
import at.posselt.pfrpg2e.kingdom.data.RawConsumption
import at.posselt.pfrpg2e.utils.typeSafeUpdate
import com.foundryvtt.core.grid.GridOffset2D
import at.posselt.pfrpg2e.kingdom.data.RawFactionStandingEntry
import at.posselt.pfrpg2e.kingdom.data.RawGroup
import at.posselt.pfrpg2e.kingdom.mergeSubmittedGroups
import at.posselt.pfrpg2e.kingdom.data.endTurn
import at.posselt.pfrpg2e.kingdom.data.getChosenCharter
import at.posselt.pfrpg2e.kingdom.data.getChosenFeats
import at.posselt.pfrpg2e.kingdom.data.getChosenFeatures
import at.posselt.pfrpg2e.kingdom.data.getChosenGovernment
import at.posselt.pfrpg2e.kingdom.data.getChosenHeartland
import at.posselt.pfrpg2e.kingdom.dialogs.ActivityManagement
import at.posselt.pfrpg2e.kingdom.dialogs.AddEvent
import at.posselt.pfrpg2e.kingdom.dialogs.AddModifier
import at.posselt.pfrpg2e.kingdom.dialogs.AddQuest
import at.posselt.pfrpg2e.questevent.GenerateQuestDialog
import at.posselt.pfrpg2e.questevent.QuestGeneratorSettings
import at.posselt.pfrpg2e.kingdom.dialogs.CharterManagement
import at.posselt.pfrpg2e.kingdom.dialogs.CheckType
import at.posselt.pfrpg2e.kingdom.dialogs.FeatManagement
import at.posselt.pfrpg2e.kingdom.dialogs.GovernmentManagement
import at.posselt.pfrpg2e.kingdom.dialogs.HeartlandManagement
import at.posselt.pfrpg2e.kingdom.dialogs.InspectSettlement
import at.posselt.pfrpg2e.kingdom.dialogs.KingdomEventManagement
import at.posselt.pfrpg2e.kingdom.dialogs.KingdomSettingsApplication
import at.posselt.pfrpg2e.kingdom.dialogs.MilestoneManagement
import at.posselt.pfrpg2e.kingdom.dialogs.StructureBrowser
import at.posselt.pfrpg2e.kingdom.dialogs.DeployArmy
import at.posselt.pfrpg2e.kingdom.dialogs.DeploySettlementOption
import at.posselt.pfrpg2e.kingdom.dialogs.ResolveBattle
import at.posselt.pfrpg2e.kingdom.data.RawArmyBattle
import at.posselt.pfrpg2e.kingdom.dialogs.addSettlementBlockDialog
import at.posselt.pfrpg2e.kingdom.dialogs.armyBrowser
import at.posselt.pfrpg2e.kingdom.dialogs.armyTacticsBrowser
import at.posselt.pfrpg2e.kingdom.dialogs.configureLeaderKingdomSkills
import at.posselt.pfrpg2e.kingdom.dialogs.configureLeaderSkills
import at.posselt.pfrpg2e.kingdom.dialogs.consumptionBreakdown
import at.posselt.pfrpg2e.kingdom.dialogs.deleteSettlementDialog
import at.posselt.pfrpg2e.kingdom.dialogs.kingdomCheckDialog
import at.posselt.pfrpg2e.kingdom.dialogs.kingdomSizeHelp
import at.posselt.pfrpg2e.kingdom.dialogs.newSettlementChoices
import at.posselt.pfrpg2e.kingdom.dialogs.settlementSizeHelp
import at.posselt.pfrpg2e.kingdom.dialogs.structureXpDialog
import at.posselt.pfrpg2e.kingdom.dialogs.HexContentManager
import at.posselt.pfrpg2e.kingdom.dialogs.RosterAddDialog
import at.posselt.pfrpg2e.kingdom.dialogs.AddExpeditionDialog
import at.posselt.pfrpg2e.kingdom.dialogs.RosterEditDialog
import at.posselt.pfrpg2e.kingdom.dialogs.TurnWizardApplication
import at.posselt.pfrpg2e.kingdom.dialogs.postLastTurnRecap
import at.posselt.pfrpg2e.kingdom.dialogs.performEndTurn
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.getActiveLeader
import at.posselt.pfrpg2e.kingdom.getOwnedLeaderRoles
import at.posselt.pfrpg2e.kingdom.getActivity
import at.posselt.pfrpg2e.kingdom.recordActivityPerformed
import at.posselt.pfrpg2e.kingdom.toggleActivityPerformed
import at.posselt.pfrpg2e.kingdom.getAllActivities
import at.posselt.pfrpg2e.kingdom.vkActivityIds
import at.posselt.pfrpg2e.kingdom.vkToBaseActivityIds
import at.posselt.pfrpg2e.kingdom.structures.vkStructureIds
import at.posselt.pfrpg2e.kingdom.structures.vkToBaseStructureIds
import at.posselt.pfrpg2e.kingdom.getAllSettlements
import at.posselt.pfrpg2e.kingdom.getCharters
import at.posselt.pfrpg2e.kingdom.getEvent
import at.posselt.pfrpg2e.kingdom.getExplodedFeatures
import at.posselt.pfrpg2e.kingdom.getFeats
import at.posselt.pfrpg2e.kingdom.getGovernments
import at.posselt.pfrpg2e.kingdom.getHeartlands
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.getMilestones
import at.posselt.pfrpg2e.kingdom.getOngoingEvents
import at.posselt.pfrpg2e.kingdom.getRealmData
import at.posselt.pfrpg2e.kingdom.getUnclaimedWorksites
import at.posselt.pfrpg2e.kingdom.computeCaravanEtaTurns
import at.posselt.pfrpg2e.kingdom.caravanPurchaseCost
import at.posselt.pfrpg2e.kingdom.canDispatchCaravanTo
import at.posselt.pfrpg2e.kingdom.data.RawCaravan
import at.posselt.pfrpg2e.kingdom.map.KingmakerHexGridProvider
import at.posselt.pfrpg2e.kingdom.dialogs.CaravanDispatchDialog
import at.posselt.pfrpg2e.kingdom.dialogs.CaravanHexOption
import at.posselt.pfrpg2e.kingdom.dialogs.CaravanPartnerOption
import at.posselt.pfrpg2e.kingdom.ShipmentOutcome
import at.posselt.pfrpg2e.kingdom.shipmentHistoryList
import at.posselt.pfrpg2e.kingdom.shipmentOutcomeCounts
import at.posselt.pfrpg2e.kingdom.sheet.contexts.CaravanRowContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.ShipmentHistoryRowContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.toCaravanRowContexts
import at.posselt.pfrpg2e.kingdom.sheet.contexts.toShipmentRowContexts
import at.posselt.pfrpg2e.kingdom.data.RawCaravanShipment
import at.posselt.pfrpg2e.kingdom.dialogs.CaravanShipmentDialog
import at.posselt.pfrpg2e.kingdom.CleanseItemSettlement
import at.posselt.pfrpg2e.kingdom.dialogs.openCleanseItemDialog
import at.posselt.pfrpg2e.kingdom.rollCleanseItem
import at.posselt.pfrpg2e.kingdom.computeCaravanRoute
import at.posselt.pfrpg2e.kingdom.currentSeasonalModifiers
import at.posselt.pfrpg2e.kingdom.forecast.buildForecast
import at.posselt.pfrpg2e.kingdom.sheet.contexts.buildForecastPanelContext
import at.posselt.pfrpg2e.kingdom.map.routeHexSafety
import at.posselt.pfrpg2e.kingdom.shipmentRaidDc
import at.posselt.pfrpg2e.kingdom.caravanRouteSafety
import at.posselt.pfrpg2e.kingdom.caravanRaidDc
import at.posselt.pfrpg2e.kingdom.CARAVAN_BASE_RAID_DC
import at.posselt.pfrpg2e.kingdom.siegeTargetsFor
import at.posselt.pfrpg2e.kingdom.garrisonDefensiveBonus
import at.posselt.pfrpg2e.kingdom.GarrisonAssignment
import at.posselt.pfrpg2e.kingdom.withGarrisonDefenders
import at.posselt.pfrpg2e.kingdom.garrisonedArmyIdsFor
import at.posselt.pfrpg2e.kingdom.caravanEtaTurns
import at.posselt.pfrpg2e.kingdom.parseBulk
import at.posselt.pfrpg2e.kingdom.CARAVAN_PARTY_SURCHARGE
import at.posselt.pfrpg2e.kingdom.caravanBulkCapacity
import com.pixijs.Point
import com.foundryvtt.kingmaker.kingmaker
import at.posselt.pfrpg2e.utils.asSequence
import js.array.component1
import js.array.component2
import at.posselt.pfrpg2e.kingdom.getTrainedSkills
import at.posselt.pfrpg2e.kingdom.hasLeaderUuid
import at.posselt.pfrpg2e.kingdom.modifiers.ModifierType
import at.posselt.pfrpg2e.kingdom.modifiers.bonuses.calculateInvestedBonus
import at.posselt.pfrpg2e.kingdom.modifiers.bonuses.getHighestLeadershipModifiers
import at.posselt.pfrpg2e.kingdom.modifiers.evaluation.evaluateGlobalBonuses
import at.posselt.pfrpg2e.kingdom.modifiers.penalties.calculateUnrestPenalty
import at.posselt.pfrpg2e.kingdom.parse
import at.posselt.pfrpg2e.kingdom.parseAbilityScores
import at.posselt.pfrpg2e.kingdom.parseLeaderActors
import at.posselt.pfrpg2e.kingdom.parseRuins
import at.posselt.pfrpg2e.kingdom.parseSkillRanks
import at.posselt.pfrpg2e.kingdom.resources.calculateConsumption
import at.posselt.pfrpg2e.kingdom.resources.calculateStorage
import at.posselt.pfrpg2e.kingdom.resources.Income
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.kingdom.sheet.contexts.KingdomSheetContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.NavEntryContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.createBonusFeatContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.createTabs
import at.posselt.pfrpg2e.kingdom.sheet.contexts.skillChecks
import at.posselt.pfrpg2e.kingdom.sheet.contexts.UnclaimedWorksiteContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.toActivitiesContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.toContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.toRosterContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.toExpeditionsContext
import at.posselt.pfrpg2e.kingdom.launchExpedition
import at.posselt.pfrpg2e.kingdom.applyExpeditionRewardToKingdom
import at.posselt.pfrpg2e.kingdom.buildExpeditionDestinationOptions
import at.posselt.pfrpg2e.kingdom.logExpeditionLaunched
import at.posselt.pfrpg2e.kingdom.repostExpeditionOffer
import at.posselt.pfrpg2e.kingdom.resolveExpeditionCore
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import at.posselt.pfrpg2e.kingdom.sheet.contexts.MAX_CONCURRENT_EXPEDITIONS
import at.posselt.pfrpg2e.kingdom.sheet.contexts.activeExpeditionCount
import at.posselt.pfrpg2e.kingdom.sheet.contexts.companionHasActiveExpedition
import at.posselt.pfrpg2e.kingdom.SessionPrepNarrativeGenerator
import at.posselt.pfrpg2e.kingdom.sheet.SessionPrepNarrativeDialog
import at.posselt.pfrpg2e.kingdom.sheet.contexts.buildPartyInfluenceContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.buildArmyPressureContext
import at.posselt.pfrpg2e.kingdom.buildArmyPressureView
import at.posselt.pfrpg2e.kingdom.sheet.contexts.buildPacingAlertContext
import at.posselt.pfrpg2e.kingdom.buildPacingAlertView
import at.posselt.pfrpg2e.kingdom.sheet.contexts.buildSessionPrepContext
import at.posselt.pfrpg2e.kingdom.SessionPrepView
import at.posselt.pfrpg2e.kingdom.buildSessionPrepView
import at.posselt.pfrpg2e.kingdom.trackTurnGap
import at.posselt.pfrpg2e.kingdom.pacingMaxTurnGap
import at.posselt.pfrpg2e.kingdom.postPacingAlertChat
import at.posselt.pfrpg2e.kingdom.recalculateWarPressure
import at.posselt.pfrpg2e.kingdom.defaultWarPressure
import at.posselt.pfrpg2e.kingdom.dialogs.AddWarThreat
import at.posselt.pfrpg2e.kingdom.dialogs.ModifyFactionStanding
import at.posselt.pfrpg2e.kingdom.dialogs.DeployableArmyOption
import at.posselt.pfrpg2e.kingdom.dialogs.DeployThreatOption
import at.posselt.pfrpg2e.kingdom.data.WarThreatStatus
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import at.posselt.pfrpg2e.kingdom.BattleArmyInfo
import at.posselt.pfrpg2e.kingdom.createArmyBattle
import at.posselt.pfrpg2e.kingdom.resolveBattleTerrain
import at.posselt.pfrpg2e.data.armies.ArmyType
import at.posselt.pfrpg2e.data.armies.BattleStatus
import at.posselt.pfrpg2e.data.armies.ArmyCondition
import at.posselt.pfrpg2e.kingdom.transitionDeploymentToBattle
import at.posselt.pfrpg2e.kingdom.updateDeploymentStatusesAfterBattle
import com.foundryvtt.pf2e.actor.PF2EArmy
import at.posselt.pfrpg2e.kingdom.sheet.contexts.CompanionRef
import at.posselt.pfrpg2e.kingdom.sheet.contexts.PartyMemberRef
import at.posselt.pfrpg2e.kingdom.sheet.contexts.withInfluence
import at.posselt.pfrpg2e.kingdom.sheet.contexts.buildCompanionQuestRows
import at.posselt.pfrpg2e.kingdom.sheet.contexts.companionHasActivePersonalQuests
import at.posselt.pfrpg2e.companion.CompanionProfileDialog
import at.posselt.pfrpg2e.kingdom.sheet.contexts.toSettlementDetailsMatrixRows
import at.posselt.pfrpg2e.kingdom.sheet.navigation.MainNavEntry
import at.posselt.pfrpg2e.kingdom.sheet.navigation.TurnNavEntry
import at.posselt.pfrpg2e.kingdom.structures.BlockTile
import at.posselt.pfrpg2e.kingdom.structures.RawSettlement
import at.posselt.pfrpg2e.kingdom.structures.createSettlementBlocks
import at.posselt.pfrpg2e.kingdom.structures.getImportedStructures
import at.posselt.pfrpg2e.kingdom.structures.importSettlementScene
import at.posselt.pfrpg2e.kingdom.structures.importStructures
import at.posselt.pfrpg2e.kingdom.structures.isStructure
import at.posselt.pfrpg2e.kingdom.structures.levelUpTo
import at.posselt.pfrpg2e.kingdom.vacancies
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.app.confirm
import at.posselt.pfrpg2e.app.confirmDelete
import at.posselt.pfrpg2e.takeIfInstance
import at.posselt.pfrpg2e.app.jsonFilePicker
import at.posselt.pfrpg2e.kingdom.sheet.KingdomJournalExporter
import at.posselt.pfrpg2e.kingdom.sheet.ObsidianImporter
import at.posselt.pfrpg2e.kingdom.sheet.WorldAnvilExporter
import at.posselt.pfrpg2e.kingdom.sheet.upkeepGainFame
import at.posselt.pfrpg2e.kingdom.sheet.upkeepAdjustUnrest
import at.posselt.pfrpg2e.kingdom.sheet.upkeepCollectResources
import at.posselt.pfrpg2e.kingdom.sheet.upkeepPayConsumption
import at.posselt.pfrpg2e.utils.TableAndDraw
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.d20Check
import at.posselt.pfrpg2e.utils.formatAsModifier
import at.posselt.pfrpg2e.utils.fromUuidOfTypes
import at.posselt.pfrpg2e.utils.launch
import at.posselt.pfrpg2e.utils.openJournal
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.roll
import at.posselt.pfrpg2e.utils.rollWithCompendiumFallback
import at.posselt.pfrpg2e.utils.stripHtml
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.getCurrentMonth
import at.posselt.pfrpg2e.weather.setWeather
import at.posselt.pfrpg2e.weather.getCurrentWeatherType
import at.posselt.pfrpg2e.data.regions.WeatherType
import at.posselt.pfrpg2e.data.regions.WeatherEffect
import at.posselt.pfrpg2e.data.regions.getMonth
import at.posselt.pfrpg2e.fromCamelCase
import com.foundryvtt.core.Game
import com.foundryvtt.core.applications.api.ApplicationRenderOptions
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.applications.ux.TextEditor.enrichHtml
import com.foundryvtt.core.documents.Actor
import com.foundryvtt.core.documents.onCreateDrawing
import com.foundryvtt.core.documents.onCreateTile
import com.foundryvtt.pf2e.actor.PF2ECharacter
import com.foundryvtt.pf2e.actor.PF2ENpc
import com.foundryvtt.core.documents.onCreateToken
import com.foundryvtt.core.documents.onDeleteDrawing
import com.foundryvtt.core.documents.onDeleteScene
import com.foundryvtt.core.documents.onDeleteTile
import com.foundryvtt.core.documents.onDeleteToken
import com.foundryvtt.core.documents.onUpdateActor
import com.foundryvtt.core.documents.onUpdateDrawing
import com.foundryvtt.core.documents.onUpdateItem
import com.foundryvtt.core.documents.onUpdateTile
import com.foundryvtt.core.documents.onUpdateToken
import com.foundryvtt.core.helpers.onApplyTokenStatusEffect
import com.foundryvtt.core.helpers.onCanvasReady
import com.foundryvtt.core.ui
import com.foundryvtt.core.utils.deepClone
import com.foundryvtt.kingmaker.onCloseKingmakerHexEdit
import io.github.uuidjs.uuid.v4
import js.array.toTypedArray
import js.array.tupleOf
import js.core.Void
import js.objects.recordOf
import kotlinx.browser.document
import kotlinx.coroutines.await
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise
import kotlin.math.max
import at.posselt.pfrpg2e.kingdom.caravansInTransitTo
import at.posselt.pfrpg2e.kingdom.recallCaravan
import at.posselt.pfrpg2e.kingdom.postCaravanWarOffers
import at.posselt.pfrpg2e.kingdom.partnersNewlyAtWar
import at.posselt.pfrpg2e.kingdom.isExpired
import at.posselt.pfrpg2e.kingdom.bankedBonusList
import at.posselt.pfrpg2e.kingdom.sheet.contexts.BankedBonusContext
import at.posselt.pfrpg2e.kingdom.addStandingEntry
import at.posselt.pfrpg2e.kingdom.IMPROVE_SETTLEMENT_ACTIVITY
import at.posselt.pfrpg2e.kingdom.sheet.contexts.analyticsLevelTarget

class KingdomSheet(
    private val game: Game,
    private val actor: KingdomActor,
    private val dispatcher: ActionDispatcher,
) : FormApp<KingdomSheetContext, KingdomSheetData>(
    title = "Manage Kingdom",
    template = "applications/kingdom/kingdom-sheet.hbs",
    debug = true,
    dataModel = KingdomSheetDataModel::class.js,
    classes = setOf("km-kingdom-sheet"),
    id = "kmKingdomSheet-${actor.uuid}",
    width = 1200,
    resizable = true,
    syncedDocument = actor,
    controls = arrayOf(
        MenuControl(label = t("kingdom.showPlayers"), action = "show-players", gmOnly = true),
        MenuControl(label = t("kingdom.activities"), action = "configure-activities", gmOnly = true),
        MenuControl(label = t("kingdom.charters"), action = "configure-charters", gmOnly = true),
        MenuControl(label = t("kingdom.events"), action = "configure-events", gmOnly = true),
        MenuControl(label = t("kingdom.feats"), action = "configure-feats", gmOnly = true),
        MenuControl(label = t("kingdom.governments"), action = "configure-governments", gmOnly = true),
        MenuControl(label = t("kingdom.heartlands"), action = "configure-heartlands", gmOnly = true),
        MenuControl(label = t("kingdom.milestones"), action = "configure-milestones", gmOnly = true),
        MenuControl(label = t("kingdom.hex-content"), action = "open-hex-content-manager", gmOnly = true),
        MenuControl(label = t("applications.settings"), action = "settings", gmOnly = true),
        MenuControl(label = t("kingdom.exportToJournal"), action = "export-to-journal", gmOnly = true),
        MenuControl(label = t("kingdom.exportToWorldAnvil"), action = "export-to-world-anvil", gmOnly = true),
        MenuControl(label = t("kingdom.importFromObsidian"), action = "import-from-obsidian", gmOnly = true),
        MenuControl(label = t("kingdom.openObsidian"), action = "open-obsidian", gmOnly = true),
        MenuControl(label = t("applications.quickstart"), action = "quickstart", gmOnly = true),
        MenuControl(label = t("applications.help"), action = "help"),
    ),
    scrollable = setOf(
        ".km-kingdom-sheet-sidebar-kingdom",
        ".km-kingdom-sheet-turn",
        ".km-kingdom-sheet-kingdom",
        ".km-kingdom-sheet-modifiers",
        ".km-kingdom-sheet-notes",
        ".km-kingdom-sheet-settlements",
        ".km-kingdom-sheet-trade-agreements",
        ".km-kingdom-sheet-sidebar-turn"
    ),
) {
    private var initialKingdomLevel = getKingdom().level
    private var noCharter = getKingdom().charter.type == null
    private var currentCharacterSheetNavEntry: String = if (noCharter) "Creation" else "$initialKingdomLevel"
    private var currentNavEntry: MainNavEntry = if (noCharter) MainNavEntry.KINGDOM else MainNavEntry.TURN

    /** Transient per-open horizon for the Session Prep forecast (plan phase 4); resets on reopen. */
    private var forecastHorizonDays: Int = 7
    private var bonusFeat: String? = null
    private var showDetailedMatrix: Boolean = false
    private val openedDetails = mutableSetOf<String>()
    private var analyticsWindowSize: Int = 25
    // Quick-filter state for the active quests grid. Kept on the instance so it
    // survives Foundry's full re-render (which rebuilds the DOM on every change).
    private var questFilterTitle: String = ""
    private var questFilterMin: String = ""
    private var questFilterMax: String = ""
    private var questFilterHidden: String = "all"
    private var questFilterCategory: String = "all"

    // Whether the completed-quest archive is expanded past its default cap. Lives here rather than
    // in the DOM because reopening a quest calls setKingdom(), which re-renders the whole sheet.
    private var completedQuestsExpanded: Boolean = false
    private var threatFilterStatus: String = "all"
    private var threatSortMode: String = "eta"

    init {
        appHook.onDeleteScene { _, _, _ -> render() }
        appHook.onCreateTile { _, _, _ -> render() }
        appHook.onUpdateTile { _, _, _, _ -> render() }
        appHook.onDeleteTile { _, _, _ -> render() }
        appHook.onCreateDrawing { _, _, _ -> render() }
        appHook.onUpdateDrawing { _, _, _, _ -> render() }
        appHook.onDeleteDrawing { _, _, _ -> render() }
        appHook.onDeleteToken { token, _, _ ->
            if (token.isStructure()) {
                render()
            }
        }
        appHook.onUpdateToken { token, _, _, _ ->
            if (token.isStructure()) {
                buildPromise {
                    token.movement.animation.ended.await()
                    render()
                }
            }
        }
        appHook.onCreateToken { token, _, _ ->
            if (token.isStructure()) {
                render()
            }
        }
        appHook.onCanvasReady { _ -> render() }
        appHook.onApplyTokenStatusEffect { _, _, _ -> render() }
        appHook.onCloseKingmakerHexEdit { _, _ -> render() }
        appHook.onUpdateActor { actor, _, _, _ -> checkUpdateActorReRenders(actor) }
        appHook.onUpdateItem { item, _, _, _ ->
            val actor = item.actor
            if (item.type == "lore" && actor != null) {
                checkUpdateActorReRenders(actor)
            }
        }
        onDocumentRefDrop(
            ".km-choose-leaders li",
            { it.type == "Actor" }
        ) { event, documentRef ->
            buildPromise {
                val target = event.currentTarget as HTMLElement
                val leader = target.dataset["leader"]?.let { Leader.fromString(it) }
                if (leader != null && documentRef is ActorRef) {
                    val kingdom = getKingdom()
                    when (leader) {
                        Leader.RULER -> kingdom.leaders.ruler.uuid = documentRef.uuid
                        Leader.COUNSELOR -> kingdom.leaders.counselor.uuid = documentRef.uuid
                        Leader.EMISSARY -> kingdom.leaders.emissary.uuid = documentRef.uuid
                        Leader.GENERAL -> kingdom.leaders.general.uuid = documentRef.uuid
                        Leader.MAGISTER -> kingdom.leaders.magister.uuid = documentRef.uuid
                        Leader.TREASURER -> kingdom.leaders.treasurer.uuid = documentRef.uuid
                        Leader.VICEROY -> kingdom.leaders.viceroy.uuid = documentRef.uuid
                        Leader.WARDEN -> kingdom.leaders.warden.uuid = documentRef.uuid
                    }
                    actor.setKingdom(kingdom)
                }
            }
        }
    }

    private fun checkUpdateActorReRenders(actor: Actor) {
        val kingdom = getKingdom()
        if (kingdom.hasLeaderUuid(actor.uuid)) {
            render()
        }
    }

    private fun getKingdom(): KingdomData {
        val kingdom = actor.getKingdom()
        checkNotNull(kingdom) {
            "Actor ${actor.name} is not a kingdom actor"
        }
        return kingdom
    }

    /**
     * Adjust one party member's influence toward one companion ([companionId], [uuid]) by [delta],
     * clamped to [0, 12], and persist. Companion roster and party membership are read live; only the
     * per-pair influence value is stored. GM only.
     */
    private suspend fun adjustPartyInfluence(companionId: String?, uuid: String?, delta: Int) {
        if (companionId.isNullOrBlank() || uuid.isNullOrBlank() || !game.user.isGM) return
        val kingdom = getKingdom()
        val current = kingdom.partyInfluence ?: emptyArray()
        val existing = current.firstOrNull { it.companionId == companionId && it.uuid == uuid }?.influence ?: 0
        kingdom.partyInfluence = current.withInfluence(companionId, uuid, existing + delta)
        actor.setKingdom(kingdom)
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "show-players" -> buildPromise {
                val action = ActionMessage(
                    action = "openKingdomSheet",
                    data = OpenKingdomSheetAction(actorUuid = actor.uuid)
                )
                dispatcher.dispatch(action)
            }

            "clear-leader" -> buildPromise {
                val target = event.target as HTMLElement
                val leader = target.dataset["leader"]?.let { Leader.fromString(it) }
                if (leader != null) {
                    val kingdom = getKingdom()
                    when (leader) {
                        Leader.RULER -> kingdom.leaders.ruler.uuid = null
                        Leader.COUNSELOR -> kingdom.leaders.counselor.uuid = null
                        Leader.EMISSARY -> kingdom.leaders.emissary.uuid = null
                        Leader.GENERAL -> kingdom.leaders.general.uuid = null
                        Leader.MAGISTER -> kingdom.leaders.magister.uuid = null
                        Leader.TREASURER -> kingdom.leaders.treasurer.uuid = null
                        Leader.VICEROY -> kingdom.leaders.viceroy.uuid = null
                        Leader.WARDEN -> kingdom.leaders.warden.uuid = null
                    }
                    actor.setKingdom(kingdom)
                }
            }

            "open-leader" -> buildPromise {
                val target = event.target as HTMLElement
                val leader = target.dataset["leader"]?.let { Leader.fromString(it) }
                if (leader != null) {
                    val kingdom = getKingdom()
                    when (leader) {
                        Leader.RULER -> kingdom.leaders.ruler.uuid?.let { openActor(it) }
                        Leader.COUNSELOR -> kingdom.leaders.counselor.uuid?.let { openActor(it) }
                        Leader.EMISSARY -> kingdom.leaders.emissary.uuid?.let { openActor(it) }
                        Leader.GENERAL -> kingdom.leaders.general.uuid?.let { openActor(it) }
                        Leader.MAGISTER -> kingdom.leaders.magister.uuid?.let { openActor(it) }
                        Leader.TREASURER -> kingdom.leaders.treasurer.uuid?.let { openActor(it) }
                        Leader.VICEROY -> kingdom.leaders.viceroy.uuid?.let { openActor(it) }
                        Leader.WARDEN -> kingdom.leaders.warden.uuid?.let { openActor(it) }
                    }
                    actor.setKingdom(kingdom)
                }
            }

            "change-kingdom-section-nav" -> {
                event.preventDefault()
                event.stopPropagation()
                currentCharacterSheetNavEntry = target.dataset["link"] ?: "Creation"
                render()
            }

            "set-forecast-horizon" -> {
                forecastHorizonDays = target.dataset["days"]?.toIntOrNull() ?: 7
                render()
            }

            "change-nav" -> {
                event.preventDefault()
                event.stopPropagation()
                currentNavEntry = target.dataset["link"]?.let { MainNavEntry.fromString(it) } ?: MainNavEntry.TURN
                render()
            }

            "import-turn-history" -> buildPromise {
                // Backfill pre-migration turns from the workbook. GM-guarded inside the dialog too:
                // players are OWNERs of the party actor, so a template gate is not authorization.
                importTurnHistoryDialog(game, actor)
                render()
            }

            "change-analytics-window" -> {
                event.preventDefault()
                event.stopPropagation()
                analyticsWindowSize = target.dataset["window"]?.toIntOrNull() ?: 25
                render()
            }

            "toggle-settlements-view" -> {
                event.preventDefault()
                event.stopPropagation()
                showDetailedMatrix = target.dataset["view"] == "matrix"
                render()
            }

            "add-bonus-feat" -> buildPromise {
                val featId = bonusFeat
                if (featId != null) {
                    val kingdom = getKingdom()
                    kingdom.bonusFeats = kingdom.bonusFeats + RawBonusFeat(
                        id = featId,
                        ruinThresholdIncreases = emptyArray(),
                    )
                    bonusFeat = null
                    actor.setKingdom(kingdom)
                }
            }

            "delete-bonus-feat" -> buildPromise {
                val featId = target.dataset["id"]
                if (featId != null) {
                    val kingdom = getKingdom()
                    val feat = kingdom.bonusFeats.find { it.id == featId }
                    val featName = feat?.id ?: featId
                    if (confirmDelete("kingdom.confirmDelete.bonusFeat", featName)) {
                        kingdom.bonusFeats = kingdom.bonusFeats.filter { it.id != featId }.toTypedArray()
                        actor.setKingdom(kingdom)
                    }
                }
            }

            "enable-army-board" -> buildPromise {
                val kingdom = getKingdom()
                kingdom.settings.enableArmyPressureBoard = true
                if (kingdom.warPressure == null) kingdom.warPressure = defaultWarPressure()
                actor.setKingdom(kingdom)
            }

            "add-war-threat" -> AddWarThreat(factions = getKingdom().groups.map { it.name }) { threat ->
                buildPromise {
                    val kingdom = getKingdom()
                    kingdom.warThreats = (kingdom.warThreats ?: emptyArray()) + threat
                    kingdom.warPressure = recalculateWarPressure(
                        kingdom.warThreats ?: emptyArray(),
                        kingdom.armyDeployments ?: emptyArray(),
                        kingdom.warPressure,
                    )
                    actor.setKingdom(kingdom)
                }
            }.launch()

            "edit-war-threat" -> {
                val threatId = target.dataset["id"]
                val existingThreat = (getKingdom().warThreats ?: emptyArray()).find { it.id == threatId }
                if (existingThreat != null) {
                    AddWarThreat(
                        existing = existingThreat,
                        factions = getKingdom().groups.map { it.name },
                    ) { updated ->
                        buildPromise {
                            val kingdom = getKingdom()
                            kingdom.warThreats = (kingdom.warThreats ?: emptyArray())
                                .map { if (it.id == updated.id) updated else it }.toTypedArray()
                            kingdom.warPressure = recalculateWarPressure(
                                kingdom.warThreats ?: emptyArray(),
                                kingdom.armyDeployments ?: emptyArray(),
                                kingdom.warPressure,
                            )
                            actor.setKingdom(kingdom)
                        }
                    }.launch()
                }
            }

            "delete-war-threat" -> buildPromise {
                val threatId = target.dataset["id"]
                val kingdom = getKingdom()
                val threat = (kingdom.warThreats ?: emptyArray()).find { it.id == threatId }
                val threatName = threat?.name ?: threatId ?: "Unknown"
                if (confirmDelete("kingdom.confirmDelete.warThreat", threatName)) {
                    kingdom.warThreats = (kingdom.warThreats ?: emptyArray()).filter { it.id != threatId }.toTypedArray()
                    kingdom.warPressure = recalculateWarPressure(
                        kingdom.warThreats ?: emptyArray(),
                        kingdom.armyDeployments ?: emptyArray(),
                        kingdom.warPressure,
                    )
                    actor.setKingdom(kingdom)
                }
            }

            "deploy-army" -> buildPromise {
                val kingdom = getKingdom()
                val armies = game.actors.contents.asSequence()
                    .filterIsInstance<PF2EArmy>()
                    .sortedBy { it.name }
                    .map {
                        val type = ArmyType.fromString(it.system.traits.type) ?: ArmyType.INFANTRY
                        DeployableArmyOption(
                            uuid = it.uuid,
                            name = it.name,
                            typeValue = type.value,
                            typeLabel = t(type),
                        )
                    }
                    .toList()
                if (armies.isEmpty()) {
                    ui.notifications.warn(t("armyPressure.noArmiesAvailable"))
                } else {
                    val threats = (kingdom.warThreats ?: emptyArray())
                        .filter { it.status == WarThreatStatus.ACTIVE.value }
                        .map { DeployThreatOption(it.id, it.name) }
                    val settlements = kingdom.getAllSettlements(game).allSettlements
                        .map { DeploySettlementOption(it.id, it.name) }
                    DeployArmy(armies, threats, settlements, deployedTurn = kingdom.currentTurn ?: 0) { deployment ->
                        buildPromise {
                            val current = getKingdom()
                            current.armyDeployments = (current.armyDeployments ?: emptyArray()) + deployment
                            current.warPressure = recalculateWarPressure(
                                current.warThreats ?: emptyArray(),
                                current.armyDeployments ?: emptyArray(),
                                current.warPressure,
                            )
                            actor.setKingdom(current)
                        }
                    }.launch()
                }
            }

            "recall-army" -> buildPromise {
                val deploymentId = target.dataset["id"]
                val kingdom = getKingdom()
                kingdom.armyDeployments = (kingdom.armyDeployments ?: emptyArray())
                    .filter { it.id != deploymentId }.toTypedArray()
                kingdom.warPressure = recalculateWarPressure(
                    kingdom.warThreats ?: emptyArray(),
                    kingdom.armyDeployments ?: emptyArray(),
                    kingdom.warPressure,
                )
                actor.setKingdom(kingdom)
            }

            "remove-deployment" -> buildPromise {
                // GM-only: remove a destroyed army deployment (cleanup)
                if (!game.user.isGM) return@buildPromise
                val deploymentId = target.dataset["id"]
                val kingdom = getKingdom()
                val deployment = (kingdom.armyDeployments ?: emptyArray()).find { it.id == deploymentId }
                if (deployment == null) return@buildPromise
                val confirmed = confirmDelete("armyPressure.removeDeploymentConfirm", deployment.armyName)
                if (!confirmed) return@buildPromise
                kingdom.armyDeployments = (kingdom.armyDeployments ?: emptyArray())
                    .filter { it.id != deploymentId }.toTypedArray()
                kingdom.warPressure = recalculateWarPressure(
                    kingdom.warThreats ?: emptyArray(),
                    kingdom.armyDeployments ?: emptyArray(),
                    kingdom.warPressure,
                )
                actor.setKingdom(kingdom)
            }

            "mark-pending-encounter-run" -> buildPromise {
                // GM-only: clear a queued war-threat encounter. setKingdom re-fires the actor-update
                // hook that re-syncs HexContentSync markers, so the map "!" marker clears too.
                if (!game.user.isGM) return@buildPromise
                val hexContentId = target.dataset["hexContentId"] ?: return@buildPromise
                val kingdom = getKingdom()
                val hexContent = kingdom.hexContents?.find { it.id == hexContentId } ?: return@buildPromise
                hexContent.pendingEncounter = false
                actor.setKingdom(kingdom)
                postChatMessage(t("chatMessages.warThreatArrival.markedAsRun", recordOf("hexKey" to hexContent.hexKey)))
            }

            "resolve-battle" -> buildPromise {
                // GM-only: resolve a war threat battle.
                if (!game.user.isGM) return@buildPromise
                val threatId = target.dataset["threatId"]
                val kingdom = getKingdom()
                val threat = (kingdom.warThreats ?: emptyArray()).find { it.id == threatId }
                if (threat == null) {
                    ui.notifications.warn(t("armyPressure.noThreats"))
                } else {
                    val existing = (kingdom.activeBattles ?: emptyArray())
                        .find { it.threatId == threatId && it.status == BattleStatus.ACTIVE.value }
                    val battle = existing ?: run {
                        val armiesByUuid = game.actors.contents
                            .filterIsInstance<PF2EArmy>()
                            .associateBy { it.uuid }
                        val deployments = kingdom.armyDeployments ?: emptyArray()
                        val assignedUuids = deployments
                            .filter { it.assignedThreatId == threatId }
                            .map { it.armyActorUuid }
                        // Armies garrisoned in the settlement under threat turn out to defend it —
                        // that is what the garrison assignment is for. They join the kingdom's own
                        // side: `attackers` is the PLAYER side here (determineBattleStatus reports
                        // DEFEAT when every attacker falls), which is the sides trap this card
                        // warned about.
                        val garrisonedUuids = threat.targetSettlementSceneId?.let { settlementId ->
                            garrisonedArmyIdsFor(
                                settlementId = settlementId,
                                assignments = deployments.map {
                                    GarrisonAssignment(
                                        armyId = it.armyActorUuid,
                                        garrisonedSettlementId = it.garrisonedSettlementId,
                                    )
                                },
                            )
                        } ?: emptyList()
                        val infos = withGarrisonDefenders(assignedUuids, garrisonedUuids)
                            .mapNotNull { uuid ->
                                armiesByUuid[uuid]?.let {
                                    BattleArmyInfo(
                                        uuid = it.uuid,
                                        name = it.name,
                                        level = it.system.details.level.value,
                                    )
                                }
                            }
                        if (infos.isEmpty()) {
                            ui.notifications.warn(t("armyPressure.noArmiesAvailable"))
                            return@buildPromise
                        }
                        val terrain = resolveBattleTerrain(
                            targetSettlementSceneId = threat.targetSettlementSceneId,
                            targetHexLocation = threat.targetHexLocation,
                            settlements = kingdom.settlements,
                        )
                        // A garrison only fights harder if the settlement actually has a
                        // Garrison structure to fight from; garrisoning in an undefended town
                        // grants nothing beyond turning up.
                        val settlementHasGarrison = threat.targetSettlementSceneId?.let { sid ->
                            siegeTargetsFor(game, kingdom, sid).any { it.structureId.removeSuffix("-vk") == "garrison" }
                        } == true
                        val defenseBonuses = if (settlementHasGarrison) {
                            garrisonedUuids.associateWith { garrisonDefensiveBonus(true) }
                        } else {
                            emptyMap()
                        }
                        val created = createArmyBattle(
                            id = "battle-${kotlin.js.Date().getTime().toLong()}",
                            threat = threat,
                            attackers = infos,
                            terrain = terrain,
                            defenseBonusByUuid = defenseBonuses,
                        )
                        // Transition assigned deployments to BATTLE status
                        val threatIdNonNull = threatId ?: return@buildPromise
                        kingdom.armyDeployments = transitionDeploymentToBattle(
                            kingdom.armyDeployments ?: emptyArray(),
                            threatIdNonNull,
                            targetSettlementSceneId = threat.targetSettlementSceneId,
                        )
                        kingdom.activeBattles = (kingdom.activeBattles ?: emptyArray()) + created
                        actor.setKingdom(kingdom)
                        created
                    }
                    ResolveBattle(battle) { updated ->
                        buildPromise {
                            val current = getKingdom()
                            current.activeBattles = (current.activeBattles ?: emptyArray())
                                .map { if (it.id == updated.id) updated else it }
                                .toTypedArray()
                            val isTerminal = updated.status == BattleStatus.VICTORY.value ||
                                updated.status == BattleStatus.DEFEAT.value
                            if (isTerminal) {
                                // Update deployment statuses based on battle outcome
                                current.armyDeployments = updateDeploymentStatusesAfterBattle(
                                    current.armyDeployments ?: emptyArray(),
                                    updated,
                                )
                                if (updated.status == BattleStatus.VICTORY.value) {
                                    current.warThreats = (current.warThreats ?: emptyArray())
                                        .map {
                                            if (it.id == updated.threatId) {
                                                RawWarThreat.copy(it, status = WarThreatStatus.DEFEATED.value)
                                            } else {
                                                it
                                            }
                                        }
                                        .toTypedArray()
                                }
                                current.warPressure = recalculateWarPressure(
                                    current.warThreats ?: emptyArray(),
                                    current.armyDeployments ?: emptyArray(),
                                    current.warPressure,
                                )
                            }
                            actor.setKingdom(current)

                            // A DEFEAT used to do nothing beyond archiving the battle: the threat
                            // stayed active and unchanged, so losing every army to an invasion was
                            // mechanically identical to never fighting. Offer the fallout instead —
                            // GM-confirmed, per-consequence, never auto-applied.
                            if (updated.status == BattleStatus.DEFEAT.value) {
                                offerDefeatConsequences(game, actor, current, updated)
                            }

                            // And a VICTORY over a faction's army used to move nothing diplomatic:
                            // standing never shifted and atWar stayed set with no way to clear it,
                            // so a won war never ended. Offer the standing gain, plus peace terms
                            // once the faction has no threats left standing.
                            if (updated.status == BattleStatus.VICTORY.value) {
                                offerWarVictory(game, actor, current, updated)
                            }

                            // Dispatch syncBattleOutcome to sync HP/conditions/XP to PF2EArmy actors.
                            // Typed construction — the original raw js() literal left `actor` as a
                            // free JS identifier (the compiler only resolved `updated`), so every
                            // resolve threw ReferenceError and the sync never ran.
                            dispatcher.dispatch(
                                ActionMessage(
                                    action = "syncBattleOutcome",
                                    data = SyncBattleOutcomeAction(
                                        battle = updated,
                                        kingdomActorUuid = actor.uuid,
                                    ),
                                )
                            )
                        }
                    }.launch()
                }
            }

            "dismiss-pacing-alert" -> buildPromise {
                val alertId = target.dataset["id"]
                val kingdom = getKingdom()
                kingdom.pacingAlerts = (kingdom.pacingAlerts ?: emptyArray()).filter { it.id != alertId }.toTypedArray()
                actor.setKingdom(kingdom)
            }

            "clear-pacing-alerts" -> buildPromise {
                val kingdom = getKingdom()
                kingdom.pacingAlerts = emptyArray()
                actor.setKingdom(kingdom)
            }

            "add-quest" -> buildPromise {
                AddQuest(settlements = questSettlementOptions()) { quest ->
                    val current = getKingdom()
                    val quests = current.quests ?: emptyArray()
                    current.quests = quests + quest
                    actor.setKingdom(current)
                }.launch()
            }

            "open-quest-generator" -> buildPromise {
                val kingdom = getKingdom()
                val settings = if (kingdom.questGeneratorSettings != null) {
                    val s = kingdom.questGeneratorSettings
                    QuestGeneratorSettings(
                        defaultVisibilityToPlayers = s.defaultVisibilityToPlayers as? Boolean ?: false,
                        maxActiveGeneratedQuests = s.maxActiveGeneratedQuests as? Int ?: 10,
                        autoAdvanceQuestTimersOnTurn = s.autoAdvanceQuestTimersOnTurn as? Boolean ?: true,
                    )
                } else {
                    QuestGeneratorSettings()
                }
                GenerateQuestDialog(
                    game = game,
                    kingdomActor = actor,
                    settings = settings,
                    onGenerate = { quest ->
                        val current = getKingdom()
                        val campaignQuests = current.campaignQuests ?: emptyArray<Any>()
                        current.campaignQuests = campaignQuests + quest
                        actor.setKingdom(current)
                    },
                ).launch()
            }

            "edit-quest" -> buildPromise {
                val questId = target.dataset["id"]
                if (questId != null) {
                    val existing = (getKingdom().quests ?: emptyArray()).find { it.id == questId }
                    if (existing != null) {
                        AddQuest(
                            settlements = questSettlementOptions(),
                            onSave = { updated ->
                                val current = getKingdom()
                                val quests = current.quests ?: emptyArray()
                                current.quests = quests.map { if (it.id == questId) updated else it }.toTypedArray()
                                actor.setKingdom(current)
                            },
                            existing = existing,
                        ).launch()
                    }
                }
            }

            "complete-quest" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                val questId = target.dataset["id"]
                if (questId != null) {
                    val kingdom = getKingdom()
                    val quests = kingdom.quests ?: emptyArray()
                    val quest = quests.find { it.id == questId }
                    if (quest != null && quest.status == "active") {
                        val priorStatus = quest.status
                        quest.status = "completed"
                        val realm = game.getRealmData(actor, kingdom)
                        val settlements = kingdom.getAllSettlements(game)
                        val storage = calculateStorage(realm, settlements.allSettlements)
                        val allFeatures = kingdom.getExplodedFeatures()
                        val chosenFeatures = kingdom.getChosenFeatures(allFeatures)
                        val chosenFeats = kingdom.getChosenFeats(chosenFeatures)

                        val rewards = quest.rewards
                        val appliedRp = rewards.rp ?: 0
                        kingdom.resourcePoints.now += appliedRp
                        // Apply XP on THIS kingdom object rather than actor.gainXp(): gainXp re-fetches
                        // a fresh clone and persists it, which the actor.setKingdom(kingdom) below would
                        // clobber — so the quest's XP reward was silently lost. Inline via the same
                        // calculateXpChange() gainXp uses, then reverse it exactly on reopen.
                        var appliedXp = 0
                        var appliedLevel = 0
                        rewards.xp?.let { xpVal ->
                            val change = kingdom.calculateXpChange(xpVal)
                            change.toChat()
                            kingdom.level += change.addLevel
                            kingdom.xp += change.addXp
                            appliedXp = change.addXp
                            appliedLevel = change.addLevel
                        }
                        val unrestBefore = kingdom.unrest
                        rewards.unrest?.let { unrestVal ->
                            kingdom.unrest = kingdom.addUnrest(unrestVal, chosenFeats)
                        }
                        val appliedUnrest = kingdom.unrest - unrestBefore
                        val currentCommodities = kingdom.commodities.now
                        val newCommodities = RawCommodities(
                            food = currentCommodities.food + (rewards.food ?: 0),
                            lumber = currentCommodities.lumber + (rewards.lumber ?: 0),
                            luxuries = currentCommodities.luxuries + (rewards.luxuries ?: 0),
                            ore = currentCommodities.ore + (rewards.ore ?: 0),
                            stone = currentCommodities.stone + (rewards.stone ?: 0)
                        ).limitBy(storage)
                        kingdom.commodities.now = newCommodities

                        // A quest can also grant lasting settlement access (a trainer, a crafting
                        // material, a higher item-purchase level). Appended here so it rides the
                        // same GM gate, the same status == "active" dedup, and the same single
                        // persist as every other reward; reopen-quest revokes it by sourceQuestId.
                        rewards.toAccessGrant(quest.id)?.let { grant ->
                            kingdom.accessGrants = (kingdom.accessGrantList() + grant).toRawAccessGrants()
                        }

                        // Record the ACTUAL applied deltas (post-clamp) so reopen reverses exactly.
                        quest.completionSnapshot = RawQuestCompletionSnapshot(
                            priorStatus = priorStatus,
                            turn = kingdom.currentTurn ?: 0,
                            rp = appliedRp,
                            xp = appliedXp,
                            level = appliedLevel,
                            unrest = appliedUnrest,
                            food = newCommodities.food - currentCommodities.food,
                            lumber = newCommodities.lumber - currentCommodities.lumber,
                            luxuries = newCommodities.luxuries - currentCommodities.luxuries,
                            ore = newCommodities.ore - currentCommodities.ore,
                            stone = newCommodities.stone - currentCommodities.stone,
                        )

                        postChatTemplate("chatmessages/quest-completed.hbs", quest, speaker = actor)
                        actor.setKingdom(kingdom)
                    }
                }
            }

            "reopen-quest" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                val questId = target.dataset["id"]
                if (questId != null) {
                    val kingdom = getKingdom()
                    val quest = (kingdom.quests ?: emptyArray()).find { it.id == questId }
                    if (quest != null && quest.status == "completed") {
                        // Revoke any access this quest granted, whether or not a completion snapshot
                        // exists: grants are keyed by sourceQuestId, so they need no numeric delta to
                        // reverse, and a legacy completion should not leave an unrevokable benefit.
                        kingdom.accessGrants = kingdom.accessGrantList()
                            .withoutQuest(quest.id)
                            .toRawAccessGrants()
                        val snap = quest.completionSnapshot
                        if (snap != null) {
                            // Reverse each applied delta (coerced to legal floors). Deltas were captured
                            // post-clamp, so this restores the pre-completion state exactly when nothing
                            // else changed these values in the meantime. If the turn has since advanced,
                            // a tick/level-up may have moved kingdom economy — warn that undo is approximate.
                            if ((kingdom.currentTurn ?: 0) != snap.turn) {
                                postChatMessage(t("kingdom.quests.reopenStale", recordOf("name" to quest.title)))
                            }
                            kingdom.resourcePoints.now = (kingdom.resourcePoints.now - snap.rp).coerceAtLeast(0)
                            kingdom.xp = (kingdom.xp - snap.xp).coerceAtLeast(0)
                            kingdom.level = (kingdom.level - snap.level).coerceAtLeast(1)
                            kingdom.unrest = (kingdom.unrest - snap.unrest).coerceAtLeast(0)
                            val c = kingdom.commodities.now
                            kingdom.commodities.now = RawCommodities(
                                food = (c.food - snap.food).coerceAtLeast(0),
                                lumber = (c.lumber - snap.lumber).coerceAtLeast(0),
                                luxuries = (c.luxuries - snap.luxuries).coerceAtLeast(0),
                                ore = (c.ore - snap.ore).coerceAtLeast(0),
                                stone = (c.stone - snap.stone).coerceAtLeast(0),
                            )
                            quest.status = snap.priorStatus
                            quest.completionSnapshot = null
                        } else {
                            // Legacy completion with no snapshot: best-effort — just reopen the status.
                            quest.status = "active"
                        }
                        postChatMessage(t("kingdom.quests.reopened", recordOf("name" to quest.title)))
                        actor.setKingdom(kingdom)
                    } else if (quest != null && quest.status == "failed") {
                        // Failed quests have no rewards/snapshot — pure status flip back to active.
                        val q = quest.asDynamic()
                        q.status = "active"
                        q.turnsRemaining = 1 // Reset to 1 turn remaining
                        postChatMessage(t("kingdom.quests.reopened", recordOf("name" to quest.title)))
                        actor.setKingdom(kingdom)
                    }
                }
            }

            "delete-quest" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                val questId = target.dataset["id"]
                if (questId != null) {
                    val kingdom = getKingdom()
                    val quest = (kingdom.quests ?: emptyArray()).find { it.id == questId }
                    val questName = quest?.title ?: questId
                    if (confirmDelete("kingdom.confirmDelete.quest", questName)) {
                        kingdom.quests = (kingdom.quests ?: emptyArray()).filter { it.id != questId }.toTypedArray()
                        actor.setKingdom(kingdom)
                    }
                }
            }

            "open-hex-content-manager" -> buildPromise {
                val game = this@KingdomSheet.game
                HexContentManager(
                    actor = actor,
                    onContentChanged = {
                        buildPromise {
                            at.posselt.pfrpg2e.kingdom.map.syncHexContentMarkers(
                                game,
                                actor
                            )
                        }
                    }
                ).launch()
            }

            "add-companion" -> buildPromise {
                RosterAddDialog { character ->
                    val current = getKingdom()
                    current.companions = (current.companions ?: emptyArray()) + character
                    actor.setKingdom(current)
                }.launch()
            }

            "open-companion-profile" -> buildPromise {
                val index = target.dataset["index"]?.toIntOrNull()
                if (index != null && index >= 0) {
                    CompanionProfileDialog(actor, index).launch()
                }
            }

            "party-influence-increase" -> buildPromise {
                adjustPartyInfluence(target.dataset["companionId"], target.dataset["uuid"], 1)
            }

            "party-influence-decrease" -> buildPromise {
                adjustPartyInfluence(target.dataset["companionId"], target.dataset["uuid"], -1)
            }

            "edit-companion" -> buildPromise {
                val index = target.dataset["index"]?.toIntOrNull()
                if (index != null) {
                    val kingdom = getKingdom()
                    val companions = kingdom.companions ?: emptyArray()
                    if (index in companions.indices) {
                        val existing = companions[index]
                        RosterEditDialog(
                            index = index,
                            existing = existing,
                            onSave = { idx, updated ->
                                val current = getKingdom()
                                val arr = (current.companions ?: emptyArray()).copyOf()
                                arr[idx] = updated
                                current.companions = arr
                                actor.setKingdom(current)
                            },
                            onDelete = { idx ->
                                val current = getKingdom()
                                if (current.companionHasActiveExpedition(idx)) {
                                    ui.notifications.warn(t("kingdom.companion.cannotDeleteOnExpedition"))
                                } else if (current.companionHasActivePersonalQuests(idx)) {
                                    ui.notifications.warn(t("kingdom.companion.cannotDeleteHasQuests"))
                                } else {
                                    current.companions = (current.companions ?: emptyArray()).filterIndexed { i, _ -> i != idx }.toTypedArray()
                                    actor.setKingdom(current)
                                }
                            },
                        ).launch()
                    }
                }
            }

            "delete-companion" -> buildPromise {
                val index = target.dataset["index"]?.toIntOrNull()
                if (index != null) {
                    val kingdom = getKingdom()
                    if (kingdom.companionHasActiveExpedition(index)) {
                        ui.notifications.warn(t("kingdom.companion.cannotDeleteOnExpedition"))
                    } else if (kingdom.companionHasActivePersonalQuests(index)) {
                        ui.notifications.warn(t("kingdom.companion.cannotDeleteHasQuests"))
                    } else {
                        val companion = kingdom.companions?.getOrNull(index)
                        if (companion != null) {
                            if (confirmDelete("kingdom.confirmDelete.companion", companion.name)) {
                                kingdom.companions = (kingdom.companions ?: emptyArray()).filterIndexed { i, _ -> i != index }.toTypedArray()
                                actor.setKingdom(kingdom)
                            }
                        }
                    }
                }
            }

            "link-actor" -> buildPromise {
                val index = target.dataset["index"]?.toIntOrNull()
                val actorUuid = target.dataset["actorUuid"]
                if (index != null && actorUuid != null) {
                    val kingdom = getKingdom()
                    val companions = (kingdom.companions ?: emptyArray()).copyOf()
                    if (index in companions.indices) {
                        companions[index].actorUuid = actorUuid
                        kingdom.companions = companions
                        actor.setKingdom(kingdom)
                    }
                }
            }

            "toggle-traveling" -> buildPromise {
                val index = target.dataset["index"]?.toIntOrNull()
                if (index != null) {
                    val kingdom = getKingdom()
                    val companions = (kingdom.companions ?: emptyArray()).copyOf()
                    if (index in companions.indices) {
                        companions[index].traveling = !companions[index].traveling
                        kingdom.companions = companions
                        actor.setKingdom(kingdom)
                    }
                }
            }

            "toggle-active" -> buildPromise {
                val index = target.dataset["index"]?.toIntOrNull()
                if (index != null) {
                    val kingdom = getKingdom()
                    val companions = (kingdom.companions ?: emptyArray()).copyOf()
                    if (index in companions.indices) {
                        companions[index].active = !companions[index].active
                        kingdom.companions = companions
                        actor.setKingdom(kingdom)
                    }
                }
            }

            "resolve-all-expeditions" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                val kingdom = getKingdom()
                val expeditions = kingdom.companionExpeditions ?: emptyArray()
                val awaitingResolution = expeditions.filter { it.status == "awaitingResolution" }
                val inProgress = expeditions.filter { it.status == "inProgress" }
                var resolved = 0

                // First, apply rewards for expeditions already awaiting resolution
                for (exp in awaitingResolution) {
                    if (applyExpeditionRewardToKingdom(kingdom, exp)) resolved++
                }

                // Then, force-resolve in-progress expeditions (early resolution)
                for (exp in inProgress) {
                    val leadCompanionId = exp.companionIds.firstOrNull() ?: continue
                    val leadCompanion = kingdom.companions?.find { (it.actorUuid ?: it.name) == leadCompanionId } ?: continue
                    resolveExpeditionCore(game, actor, kingdom, exp, leadCompanion)
                    resolved++
                }

                if (resolved > 0) {
                    actor.setKingdom(kingdom)
                    postChatMessage(t("kingdom.expeditionBatchResolved", recordOf("count" to resolved)))
                }
            }

            "add-expedition" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                val kingdom = getKingdom()
                val comps = kingdom.companions ?: emptyArray()
                AddExpeditionDialog(
                    companions = comps,
                    quests = kingdom.companionPersonalQuests ?: emptyArray(),
                    factions = kingdom.groups,
                    destinations = buildExpeditionDestinationOptions(kingdom),
                ) { expedition ->
                    val current = getKingdom()
                    val updatedComps = (current.companions ?: emptyArray()).copyOf()
                    if (launchExpedition(current, expedition, updatedComps)) {
                        current.companions = updatedComps
                        actor.setKingdom(current)
                        logExpeditionLaunched(expedition, updatedComps)
                        true
                    } else {
                        ui.notifications.warn(t("kingdom.expeditions.tooMany"))
                        false
                    }
                }.launch()
            }

            "cancel-expedition" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                val expeditionId = target.dataset["expeditionId"] ?: return@buildPromise
                val kingdom = getKingdom()
                if (confirm(t("kingdom.expeditions.cancelConfirm"))) {
                    val cancelledCompanionIds = (kingdom.companionExpeditions ?: emptyArray())
                        .find { it.id == expeditionId }
                        ?.companionIds ?: emptyArray()
                    kingdom.companionExpeditions = (kingdom.companionExpeditions ?: emptyArray()).map {
                        if (it.id == expeditionId) {
                            it.status = "cancelled"
                            it.daysRemaining = 0
                        }
                        it
                    }.toTypedArray()
                    // Release companions
                    kingdom.companions = (kingdom.companions ?: emptyArray()).map { c ->
                        val key = c.actorUuid ?: c.name
                        if (key in cancelledCompanionIds) {
                            c.expeditionStatus = "available"
                        }
                        c
                    }.toTypedArray()
                    actor.setKingdom(kingdom)
                }
            }

            "resolve-expedition" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                val expeditionId = target.dataset["expeditionId"] ?: return@buildPromise
                val kingdom = getKingdom()
                val expedition = kingdom.companionExpeditions?.find { it.id == expeditionId } ?: return@buildPromise
                // Only resolve if still in progress (early resolve) or awaiting resolution (re-show offer)
                if (expedition.status !in setOf("inProgress", "awaitingResolution")) return@buildPromise
                // Find the lead companion (first in companionIds) to make the check
                val leadCompanionId = expedition.companionIds.firstOrNull() ?: return@buildPromise
                val leadCompanion = kingdom.companions?.find { (it.actorUuid ?: it.name) == leadCompanionId } ?: return@buildPromise
                if (expedition.status == "awaitingResolution") {
                    // The outcome is already rolled — re-show the dismissed offer card WITHOUT
                    // re-rolling (routing this through resolveExpeditionCore rerolled the check
                    // and silently overwrote the accrued outcome on every click).
                    repostExpeditionOffer(game, actor, kingdom, expedition, leadCompanion)
                    return@buildPromise
                }
                // Roll once, accrue, post offer card, set status to awaitingResolution
                resolveExpeditionCore(game, actor, kingdom, expedition, leadCompanion)
            }

            "add-modifier" -> buildPromise {
                val kingdom = getKingdom()
                AddModifier(activities = kingdom.getAllActivities().toTypedArray()) {
                    val current = getKingdom()
                    current.modifiers = current.modifiers + it
                    actor.setKingdom(current)
                }.launch()
            }

            "delete-modifier" -> buildPromise {
                val index = target.dataset["index"]?.toInt() ?: 0
                val kingdom = getKingdom()
                val modifier = kingdom.modifiers.getOrNull(index)
                if (modifier != null) {
                    val modifierName = if (modifier.requiresTranslation == true) {
                        modifier.buttonLabel?.let { t(it) } ?: t(modifier.name)
                    } else {
                        modifier.name
                    }
                    if (confirmDelete("kingdom.confirmDelete.modifier", modifierName)) {
                        kingdom.modifiers = kingdom.modifiers.filterIndexed { idx, _ -> idx != index }.toTypedArray()
                        actor.setKingdom(kingdom)
                    }
                }
            }

            "add-group" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                val kingdom = getKingdom()
                val realm = game.getRealmData(actor, kingdom)
                kingdom.groups = kingdom.groups + RawGroup(
                    name = t("kingdom.groupName"),
                    negotiationDC = 10 + findKingdomSize(realm.size).controlDCModifier,
                    atWar = false,
                    relations = "none",
                    preventPledgeOfFealty = false,
                    standing = null,
                    standingLog = null,
                    allianceLevel = null,
                )
                actor.setKingdom(kingdom)
            }

            "delete-group" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                val index = target.dataset["index"]?.toInt() ?: 0
                val kingdom = getKingdom()
                val group = kingdom.groups.getOrNull(index)
                if (group != null) {
                    if (confirmDelete("kingdom.confirmDelete.group", group.name)) {
                        kingdom.groups = kingdom.groups.filterIndexed { idx, _ -> idx != index }.toTypedArray()
                        actor.setKingdom(kingdom)
                    }
                }
            }

            "annex-group" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                val index = target.dataset["index"]?.toInt() ?: 0
                val kingdom = getKingdom()
                val group = kingdom.groups.getOrNull(index)
                if (group != null) {
                    if (confirm(t("kingdom.annexConfirm", recordOf("name" to group.name)))) {
                        val hexKey = group.hexKey
                        if (!hexKey.isNullOrBlank() && game.modules.get("pf2e-kingmaker")?.active == true) {
                            // Claim the vassal's trade-hub hex on the realm map. The Kingmaker
                            // module's state is a DataModel persisted to a world setting, so we
                            // mutate it via updateSource(...) + save() (the same path its own hex
                            // editor uses) — there is no Document-style .update() on it. Wrapped so
                            // a failed/absent realm-map API never aborts the rest of the annexation.
                            runCatching {
                                val state = com.foundryvtt.kingmaker.kingmaker.state.asDynamic()
                                val hexFlags = js("{}")
                                hexFlags.claimed = true
                                hexFlags.explored = true
                                hexFlags.cleared = true
                                val hexes = js("{}")
                                hexes[hexKey] = hexFlags
                                val changes = js("{}")
                                changes.hexes = hexes
                                state.updateSource(changes)
                                (state.save() as? Promise<*>)?.await()
                            }.onFailure {
                                console.error("[pf2e-kmt] Failed to claim annexed hex $hexKey", it)
                            }
                        }
                        group.allianceLevel = null
                        kingdom.unrest = kingdom.unrest + 2
                        
                        group.addStandingEntry(RawFactionStandingEntry(
                            turn = kingdom.currentTurn ?: 0,
                            delta = 0,
                            reason = "kingdom.factionStanding.annexation",
                        ))
                        
                        actor.setKingdom(kingdom)
                        
                        val title = t("chatMessages.annexation.title")
                        val body = t("chatMessages.annexation.body", recordOf("name" to group.name))
                        val unrestLabel = t("kingdom.unrest")
                        val unrestReason = t("chatMessages.annexation.unrestReason")
                        val hexLabel = if (!hexKey.isNullOrBlank()) {
                            val label = t("chatMessages.annexation.hexClaimed")
                            "<li><b>$label</b>: $hexKey</li>"
                        } else {
                            ""
                        }
                        val content = """
                            <div class="pf2e-kingmaker-tools chat-card">
                                <h3>$title</h3>
                                <p>$body</p>
                                <ul>
                                    <li><b>$unrestLabel</b>: +2 ($unrestReason)</li>
                                    $hexLabel
                                </ul>
                            </div>
                        """.trimIndent()
                        postChatMessage(content, speaker = actor, isHtml = true)
                    }
                }
            }

            "adjust-standing" -> {
                if (!game.user.isGM) return
                val index = target.dataset["index"]?.toInt() ?: 0
                val group = getKingdom().groups.getOrNull(index)
                if (group != null) {
                    ModifyFactionStanding(factionName = group.name, initialAllianceLevel = group.allianceLevel) { delta, reason, allianceLevel ->
                        buildPromise {
                            val kingdom = getKingdom()
                            val g = kingdom.groups.getOrNull(index)
                            if (g != null) {
                                val standingChanged = delta != 0
                                val allianceChanged = allianceLevel != g.allianceLevel
                                if (standingChanged || allianceChanged) {
                                    if (standingChanged) {
                                        g.standing = applyStandingDelta(g.standing, delta)
                                    }
                                    val logReason = if (standingChanged && reason.isNotBlank()) {
                                        reason
                                    } else if (allianceChanged) {
                                        reason.ifBlank { "kingdom.factionStanding.allianceChange" }
                                    } else {
                                        reason
                                    }
                                    g.addStandingEntry(RawFactionStandingEntry(
                                        turn = kingdom.currentTurn ?: 0,
                                        delta = delta,
                                        reason = logReason,
                                    ))
                                    g.allianceLevel = allianceLevel
                                    actor.setKingdom(kingdom)
                                }
                            }
                        }
                    }.launch()
                }
            }

            "configure-activities" -> ActivityManagement(kingdomActor = actor).launch()
            "configure-events" -> KingdomEventManagement(kingdomActor = actor).launch()
            "configure-milestones" -> MilestoneManagement(kingdomActor = actor).launch()
            "configure-charters" -> CharterManagement(kingdomActor = actor).launch()
            "configure-governments" -> GovernmentManagement(kingdomActor = actor).launch()
            "configure-heartlands" -> HeartlandManagement(kingdomActor = actor).launch()
            "configure-feats" -> FeatManagement(kingdomActor = actor).launch()
            "open-clock-dialog" -> CampaignClockDialog(kingdomActor = actor).launch()
            "structures-import" -> buildPromise { importStructures() }

            "create-settlement" -> {
                buildPromise {
                    val result = newSettlementChoices()
                    importSettlement(
                        sceneName = result.name,
                        terrain = result.terrain,
                        waterBorders = result.waterBorders,
                        type = SettlementType.SETTLEMENT,
                        layoutType = result.layoutType,
                    )
                }
            }

            "create-capital" -> {
                buildPromise {
                    val heartland = getKingdom().getChosenHeartland()
                    val terrain = when (heartland?.id) {
                        "forest-or-swamp" -> SettlementTerrain.FOREST
                        "hill-or-plain" -> SettlementTerrain.PLAINS
                        "lake-or-river" -> SettlementTerrain.SWAMP
                        "mountain-or-ruins" -> SettlementTerrain.MOUNTAINS
                        else -> null
                    }
                    val result = newSettlementChoices(terrain)
                    importSettlement(
                        sceneName = result.name,
                        terrain = result.terrain,
                        waterBorders = result.waterBorders,
                        type = SettlementType.CAPITAL,
                        layoutType = result.layoutType,
                    )
                }
            }

            "add-settlement" -> buildPromise {
                game.scenes.current?.id?.let { id ->
                    val kingdom = getKingdom()
                    kingdom.settlements += RawSettlement(
                        sceneId = id,
                        lots = 1,
                        level = 1,
                        type = "settlement",
                        secondaryTerritory = false,
                        manualSettlementLevel = false,
                        waterBorders = 0,
                        layoutType = SettlementLayoutType.FREE_FORM.value,
                    )
                    actor.setKingdom(kingdom)
                }
            }

            "delete-settlement" -> buildPromise {
                target.dataset["id"]?.let { id ->
                    val scene = game.scenes.get(id)
                    val settlementName = scene?.name ?: "unknown"
                    deleteSettlementDialog(settlementName) { deleteSettlement ->
                        val kingdom = getKingdom()
                        kingdom.ongoingEvents =
                            kingdom.ongoingEvents.filter { it.settlementSceneId != id }.toTypedArray()
                        kingdom.settlements = kingdom.settlements.filter { it.sceneId != id }.toTypedArray()
                        actor.setKingdom(kingdom)
                        if (deleteSettlement) {
                            scene?.delete()?.await()
                        }
                    }
                }
            }

            "level-up-settlement" -> buildPromise {
                val scene = target.dataset["id"]?.let { game.scenes.get(it) }
                target.dataset["levelUpTo"]
                    ?.let { SettlementLevelUpType.fromString(it) }
                    ?.let { scene?.levelUpTo(it) }
            }

            "add-settlement-block" -> buildPromise {
                target.dataset["id"]
                    ?.let { game.scenes.get(it) }
                    ?.let { scene ->
                        addSettlementBlockDialog { options ->
                            scene.createSettlementBlocks(
                                listOf(
                                    BlockTile(
                                        options.shape,
                                        options.x,
                                        options.y,
                                        false,
                                    )
                                ),
                                false,
                            )
                            ui.notifications.info(t("addSettlementBlock.success", recordOf("sceneName" to scene.name)))
                        }
                    }
            }

            "view-settlement" -> buildPromise {
                target.dataset["id"]?.let { id ->
                    game.scenes.get(id)?.view()?.await()
                }
            }

            "activate-settlement" -> buildPromise {
                target.dataset["id"]?.let { id ->
                    game.scenes.get(id)?.activate()?.await()
                }
            }

            "inspect-settlement" -> buildPromise {
                target.dataset["id"]?.let { id ->
                    val kingdom = getKingdom()
                    val settlement = kingdom.settlements.find { it.sceneId == id }
                    checkNotNull(settlement) {
                        "Could not find raw settlement with id $id"
                    }
                    val autoCalculateSettlementLevel = kingdom.settings.autoCalculateSettlementLevel
                    val allStructuresStack = kingdom.settings.kingdomAllStructureItemBonusesStack
                    val title = game.scenes.get(id)?.name
                    checkNotNull(title) {
                        "Scene with id $id not found"
                    }
                    InspectSettlement(
                        game = game,
                        title = title,
                        autoCalculateSettlementLevel = autoCalculateSettlementLevel,
                        allStructuresStack = allStructuresStack,
                        allowCapitalInvestmentInCapitalWithoutBank = kingdom.settings.capitalInvestmentInCapital,
                        settlement = settlement,
                        feats = kingdom.getChosenFeats(kingdom.getChosenFeatures(kingdom.getExplodedFeatures())),
                        kingdom = kingdom,
                        capStructureBonusAtKingdomLevel = kingdom.settings.capStructureBonusAtKingdomLevel,
                        kingdomLevel = kingdom.level,
                        onRosterChange = { roster ->
                            val updatedKingdom = getKingdom()
                            updatedKingdom.settlements
                                .find { it.sceneId == id }
                                ?.populationRoster = roster
                            actor.setKingdom(updatedKingdom)
                        },
                    ) { data ->
                        val kingdom = getKingdom()
                        kingdom.settlements = kingdom.settlements
                            .filter { it.sceneId != data.sceneId }
                            .toTypedArray() + data
                        actor.setKingdom(kingdom)
                    }.launch()
                }
            }

            "settings" -> {
                console.log("hi")
                val kingdom = getKingdom()
                buildPromise {
                    KingdomSettingsApplication(
                        game = game,
                        onSave = {
                            val previous = deepClone(kingdom)
                            kingdom.settings = it
                            kingdom.fame.now = kingdom.fame.now.coerceIn(0, kingdom.settings.maximumFamePoints)
                            beforeKingdomUpdate(previous, kingdom)
                            actor.setKingdom(kingdom)
                            val armyFolderIdChanged =
                                previous.settings.recruitableArmiesFolderId != kingdom.settings.recruitableArmiesFolderId
                            if (kingdom.settings.autoCalculateArmyConsumption && (!previous.settings.autoCalculateArmyConsumption || armyFolderIdChanged)) {
                                updateArmyConsumption(game)
                            }
                        },
                        kingdomSettings = kingdom.settings
                    ).launch()
                }
            }

            "quickstart" -> buildPromise {
                openJournal("Compendium.pf2e-kingmaker-tools.kingmaker-tools-journals.JournalEntry.FwcyYZARAnOHlKkE")
            }

            "help" -> buildPromise {
                openJournal("Compendium.pf2e-kingmaker-tools.kingmaker-tools-journals.JournalEntry.iAQCUYEAq4Dy8uCY.JournalEntryPage.ty6BS5eSI7ScfVBk")
            }

            "export-to-journal" -> buildPromise {
                try {
                    val folder = KingdomJournalExporter.export(game, actor, getKingdom())
                    ui.notifications.info(t("kingdom.obsidianExportSuccess", recordOf("folder" to folder)))
                } catch (e: Throwable) {
                    ui.notifications.error("Export failed: ${e.message}")
                }
            }
            "export-to-world-anvil" -> buildPromise {
                try {
                    val result = WorldAnvilExporter.export(game, actor, getKingdom())
                    ui.notifications.info(result)
                } catch (e: Throwable) {
                    ui.notifications.error("World Anvil export failed: ${e.message}")
                }
            }

            "export-session-prep-to-journal" -> buildPromise {
                            if (!game.user.isGM) return@buildPromise
                            try {
                                val kingdom = getKingdom()
                                val view = buildSessionPrepView(
                                    quests = kingdom.quests,
                                    clocks = kingdom.campaignClocks,
                                    events = kingdom.campaignKingdomEvents,
                                    hexContents = kingdom.hexContents,
                                    companionQuests = kingdom.companionPersonalQuests,
                                    isGM = true,
                                    turnHistory = kingdom.turnHistory,
                                    companionExpeditions = kingdom.companionExpeditions,
                                    companions = kingdom.companions,
                                    warThreats = kingdom.warThreats,
                                )
                                val folder = SessionPrepJournalExporter.export(game, view)
                                ui.notifications.info(t("kingdom.sessionPrep.exportSuccess", recordOf("folder" to folder)))
                            } catch (e: Throwable) {
                                ui.notifications.error("Export failed: ${e.message}")
                            }
                        }

                        "generate-session-prep-narrative" -> buildPromise {
                            if (!game.user.isGM) return@buildPromise
                            try {
                                val kingdom = getKingdom()
                                val view = buildSessionPrepView(
                                    quests = kingdom.quests,
                                    clocks = kingdom.campaignClocks,
                                    events = kingdom.campaignKingdomEvents,
                                    hexContents = kingdom.hexContents,
                                    companionQuests = kingdom.companionPersonalQuests,
                                    isGM = true,
                                    turnHistory = kingdom.turnHistory,
                                    companionExpeditions = kingdom.companionExpeditions,
                                    companions = kingdom.companions,
                                    warThreats = kingdom.warThreats,
                                )
                    val html = SessionPrepNarrativeGenerator.generate(view)
                    if (html.isBlank()) {
                        ui.notifications.info(t("kingdom.sessionPrep.narrativeEmpty"))
                    } else {
                        SessionPrepNarrativeDialog.launch(
                            html = html,
                            game = game,
                        )
                    }
                } catch (e: Throwable) {
                    ui.notifications.error("Narrative generation failed: ${e.message}")
                }
            }

            "import-from-obsidian" -> buildPromise {
                try {
                    val markdown = jsonFilePicker(
                        title = t("kingdom.importFromObsidian"),
                        label = "Obsidian Note (.md)",
                        accept = listOf(".md"),
                        help = "Select a Markdown file exported from Obsidian to import notes, companions, or quests."
                    )
                    val result = ObsidianImporter.importMarkdown(actor, markdown, game)
                    if (result.startsWith("Missing") || result.startsWith("Unsupported") || result.startsWith("No kingdom")) {
                        ui.notifications.error(result)
                    } else {
                        ui.notifications.info(t("kingdom.obsidianImportSuccess", recordOf("details" to result)))
                        render()
                    }
                } catch (e: Throwable) {
                    ui.notifications.error("Import failed: ${e.message}")
                }
            }

            "open-obsidian" -> {
                val vaultName = game.settings.pfrpg2eKingdomCampingWeather.getObsidianVaultName()
                if (vaultName.isBlank()) {
                    ui.notifications.error("Please configure your Obsidian Vault Name in settings first!")
                } else {
                    js("window.open('obsidian://open?vault=' + encodeURIComponent(vaultName), '_blank')")
                }
            }

            "gain-xp" -> buildPromise {
                target.dataset["xp"]?.toInt()?.let {
                    actor.gainXp(it)
                }
            }

            "hex-xp" -> buildPromise {
                target.dataset["hexes"]?.toInt()?.let { hexes ->
                    actor.getKingdom()?.let { kingdom ->
                        val xp = calculateHexXP(
                            hexes = hexes,
                            xpPerClaimedHex = kingdom.settings.xpPerClaimedHex,
                            kingdomSize = game.getRealmData(actor, kingdom).size,
                            useVK = kingdom.settings.vanceAndKerensharaXP,
                        )
                        actor.gainXp(xp)
                    }
                }
            }

            "structure-xp" -> buildPromise {
                structureXpDialog(game) {
                    actor.gainXp(it)
                }
            }

            "rp-xp" -> buildPromise {
                actor.getKingdom()?.let { kingdom ->
                    val xp = calculateRpXP(
                        rp = kingdom.resourcePoints.now,
                        kingdomLevel = kingdom.level,
                        rpToXpConversionRate = kingdom.settings.rpToXpConversionRate,
                        rpToXpConversionLimit = kingdom.settings.rpToXpConversionLimit,
                        useVK = kingdom.settings.vanceAndKerensharaXP,
                    )
                    actor.gainXp(xp)
                }
            }

            "solution-xp" -> buildPromise {
                actor.getKingdom()?.let { kingdom ->
                    val xp = (kingdom.supernaturalSolutions + kingdom.creativeSolutions) * 10
                    actor.gainXp(xp)
                }
            }

            "level-up" -> buildPromise {
                actor.levelUp()
            }

            "scroll-to" -> {
                event.stopPropagation()
                event.preventDefault()
                target.dataset["id"]?.let {
                    document.getElementById(it)?.scrollIntoView()
                }
            }

            "toggle-continuous" -> buildPromise {
                val eventIndex = target.dataset["eventIndex"]?.toInt()
                checkNotNull(eventIndex) { "event index is null" }
                actor.getKingdom()?.let { kingdom ->
                    kingdom.ongoingEvents = kingdom.ongoingEvents
                        .mapIndexed { index, event ->
                            if (index == eventIndex) {
                                val isContinuous = event.becameContinuous == true
                                RawOngoingKingdomEvent.copy(event, becameContinuous = !isContinuous)
                            } else {
                                event
                            }
                        }
                        .toTypedArray()
                    actor.setKingdom(kingdom)
                }
            }

            "check-cult-event" -> buildPromise {
                actor.getKingdom()?.let { kingdom ->
                    val dc = getCultEventDC(kingdom)
                    val rollMode = RollMode.fromString(kingdom.settings.kingdomEventRollMode)
                    val succeeded = d20Check(
                        dc = dc,
                        flavor = t("kingdom.checkingForCultEvent", recordOf("dc" to dc)),
                        rollMode = rollMode,
                    ).degreeOfSuccess.succeeded()
                    if (succeeded) {
                        kingdom.turnsWithoutCultEvent = 0
                        postChatMessage(t("kingdom.cultEventOccurs"), rollMode = rollMode)
                    } else {
                        kingdom.turnsWithoutCultEvent += 1
                    }
                    actor.setKingdom(kingdom)
                }
            }

            "check-event" -> buildPromise {
                actor.getKingdom()?.let { kingdom ->
                    val dc = getEventDC(kingdom)
                    val rollMode = RollMode.fromString(kingdom.settings.kingdomEventRollMode)
                    val succeeded = d20Check(
                        dc = dc,
                        flavor = t("kingdom.checkingForKingdomEvent", recordOf("dc" to dc)),
                        rollMode = rollMode,
                    ).degreeOfSuccess.succeeded()
                    if (succeeded) {
                        kingdom.turnsWithoutEvent = 0
                        postChatMessage(t("kingdom.kingdomEventOccurs"), rollMode = rollMode)
                    } else {
                        kingdom.turnsWithoutEvent += 1
                        // Pacing advisory (#13): warn once when the event drought crosses the gap
                        val turnGapAlert = trackTurnGap(
                            turnsSinceLastEvent = kingdom.turnsWithoutEvent,
                            maxTurnGap = kingdom.settings.pacingMaxTurnGap(),
                            turn = kingdom.currentTurn ?: 0,
                        )
                        if (turnGapAlert != null) {
                            kingdom.pacingAlerts = (kingdom.pacingAlerts ?: emptyArray()) + turnGapAlert
                            postPacingAlertChat(turnGapAlert)
                        }
                    }
                    actor.setKingdom(kingdom)
                }
            }

            "roll-cult-event" -> buildPromise {
                val kingdom = getKingdom()
                val uuid = kingdom.settings.kingdomCultTable
                val rollMode = kingdom.settings.kingdomEventRollMode
                    .let { RollMode.fromString(it) } ?: RollMode.GMROLL
                val result = game.rollWithCompendiumFallback(
                    rollMode = rollMode,
                    uuid = uuid,
                    compendiumUuid = "Compendium.pf2e-kingmaker-tools.kingmaker-tools-rolltables.RollTable.aYQXu2GwIf5gdyQa",
                )
                postAddToOngoingEvents(result, rollMode, kingdom)
            }

            "roll-event" -> buildPromise {
                val kingdom = getKingdom()
                val uuid = kingdom.settings.kingdomEventsTable
                val rollMode = kingdom.settings.kingdomEventRollMode
                    .let { RollMode.fromString(it) } ?: RollMode.GMROLL
                val result = game.rollWithCompendiumFallback(
                    rollMode = rollMode,
                    uuid = uuid,
                    compendiumUuid = "Compendium.pf2e-kingmaker-tools.kingmaker-tools-rolltables.RollTable.ZXk2yVZH7JMswXbD",
                )
                postAddToOngoingEvents(result, rollMode, kingdom)
            }

            "delete-event" -> buildPromise {
                actor.getKingdom()?.let { kingdom ->
                    target.dataset["index"]?.toInt()?.let { eventIndex ->
                        kingdom.ongoingEvents = kingdom.ongoingEvents
                            .filterIndexed { index, _ -> index != eventIndex }
                            .toTypedArray()
                        actor.setKingdom(kingdom)
                    }
                }
            }

            "add-event" -> {
                val kingdom = getKingdom()
                buildPromise {
                    val settlements = kingdom.getAllSettlements(game)
                    AddEvent(
                        game = game,
                        kingdomActor = actor,
                        kingdom = kingdom,
                        settlements = settlements.allSettlements,
                        onSave = {
                            val k = getKingdom()
                            k.ongoingEvents = k.ongoingEvents + it
                            actor.setKingdom(k)
                        }
                    ).launch()
                }
            }

            "change-event-stage" -> buildPromise {
                val stage = target.dataset["stage"]?.toInt()
                val eventIndex = target.dataset["eventIndex"]?.toInt()
                checkNotNull(stage) { "index is null" }
                checkNotNull(eventIndex) { "event index is null" }
                actor.getKingdom()?.let { kingdom ->
                    kingdom.ongoingEvents = kingdom.ongoingEvents
                        .mapIndexed { index, event ->
                            if (index == eventIndex) {
                                RawOngoingKingdomEvent.copy(event, stage = stage)
                            } else {
                                event
                            }
                        }
                        .toTypedArray()
                    actor.setKingdom(kingdom)
                }
            }

            "handle-event" -> buildPromise {
                val index = target.dataset["index"]?.toInt()
                checkNotNull(index)
                val kingdom = getKingdom()
                val event = kingdom.getOngoingEvents().getOrNull(index)
                checkNotNull(event)
                actor.getKingdom()?.let { kingdom ->
                    kingdomCheckDialog(
                        game = game,
                        kingdom = kingdom,
                        kingdomActor = actor,
                        check = CheckType.HandleEvent(event),
                        selectedLeader = game.getActiveLeader(),
                        groups = emptyArray(),
                        events = emptyList(),
                    )
                }
            }

            "armies-import" -> buildPromise {
                actor.getKingdom()?.let { kingdom ->
                    game.setupArmies(kingdom, actor)
                }
            }

            "claimed-landmark" -> buildPromise {
                actor.getKingdom()?.let { kingdom ->
                    kingdom.modifiers = kingdom.modifiers + RawModifier(
                        id = v4(),
                        turns = 2,
                        name = "kingdom.claimedLandmark",
                        type = ModifierType.CIRCUMSTANCE.value,
                        value = 2,
                        enabled = true,
                        applyIf = arrayOf(
                            RawSome(
                                some = arrayOf(
                                    RawEq(eq = tupleOf("@ability", "culture")),
                                    RawEq(eq = tupleOf("@ability", "economy")),
                                )
                            )
                        )
                    )
                    val unrest = roll("1d4", flavor = "Losing Unrest")
                    val chosenFeatures = kingdom.getChosenFeatures(kingdom.getExplodedFeatures())
                    kingdom.unrest = kingdom.addUnrest(-unrest, kingdom.getChosenFeats(chosenFeatures))
                    actor.setKingdom(kingdom)
                }
            }

            "claimed-refuge" -> buildPromise {
                actor.getKingdom()?.let { kingdom ->
                    kingdom.modifiers = kingdom.modifiers + RawModifier(
                        id = v4(),
                        turns = 2,
                        name = "kingdom.claimedRefuge",
                        type = ModifierType.CIRCUMSTANCE.value,
                        value = 2,
                        enabled = true,
                        applyIf = arrayOf(
                            RawSome(
                                some = arrayOf(
                                    RawEq(eq = tupleOf("@ability", "loyalty")),
                                    RawEq(eq = tupleOf("@ability", "stability")),
                                )
                            )
                        )
                    )
                    val ruinButtons = sequenceOf(Resource.CRIME, Resource.DECAY, Resource.CORRUPTION, Resource.STRIFE)
                        .map {
                            ResourceButton(
                                value = "1",
                                resource = it,
                                mode = ResourceMode.LOSE
                            ).toHtml(emptyArray())
                        }
                        .toTypedArray()
                    postChatTemplate(
                        templatePath = "chatmessages/landmark.hbs",
                        templateContext = recordOf(
                            "buttons" to ruinButtons,
                            "actorUuid" to actor.uuid
                        )
                    )
                    actor.setKingdom(kingdom)
                }
            }

            "gain-fame" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                if (actor.isUpkeepStepDone("gain-fame")) {
                    ui.notifications.info(t("kingdom.turnWizard.upkeepStepAlreadyDone"))
                    return@buildPromise
                }
                actor.getKingdom()?.let { kingdom ->
                    actor.upkeepGainFame(kingdom)
                    actor.setKingdom(kingdom)
                    actor.markUpkeepStepDone("gain-fame")
                }
            }

            "adjust-unrest" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                if (actor.isUpkeepStepDone("adjust-unrest")) {
                    ui.notifications.info(t("kingdom.turnWizard.upkeepStepAlreadyDone"))
                    return@buildPromise
                }
                actor.getKingdom()?.let { kingdom ->
                    actor.upkeepAdjustUnrest(game, kingdom)
                    actor.setKingdom(kingdom)
                    actor.markUpkeepStepDone("adjust-unrest")
                }
            }

            "collect-resources" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                if (actor.isUpkeepStepDone("collect-resources")) {
                    ui.notifications.info(t("kingdom.turnWizard.upkeepStepAlreadyDone"))
                    return@buildPromise
                }
                actor.getKingdom()?.let { kingdom ->
                    actor.upkeepCollectResources(game, kingdom)
                    actor.setKingdom(kingdom)
                    actor.markUpkeepStepDone("collect-resources")
                }
            }

            "pay-consumption" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                if (actor.isUpkeepStepDone("pay-consumption")) {
                    ui.notifications.info(t("kingdom.turnWizard.upkeepStepAlreadyDone"))
                    return@buildPromise
                }
                actor.getKingdom()?.let { kingdom ->
                    actor.upkeepPayConsumption(game, kingdom)
                    actor.setKingdom(kingdom)
                    actor.markUpkeepStepDone("pay-consumption")
                }
            }

            "council-mission-audit" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                actor.getKingdom()?.let { kingdom ->
                    val chosenFeatures = kingdom.getChosenFeatures(kingdom.getExplodedFeatures())
                    val vacancies = kingdom.vacancies(
                        choices = chosenFeatures,
                        bonusFeats = kingdom.bonusFeats,
                        government = kingdom.government,
                    )
                    val leaderActors = kingdom.parseLeaderActors()
                    val status = deriveCouncilMissionsStatus(kingdom, leaderActors, vacancies)
                    if (!status.canAudit) return@buildPromise

                    val cooldowns = kingdom.councilCooldowns ?: RawCouncilCooldowns(0, 0, 0, 0)
                    cooldowns.audit = 4
                    kingdom.councilCooldowns = cooldowns
                    kingdom.resourcePoints.now += 10
                    actor.setKingdom(kingdom)
                    postChatMessage(t("kingdom.councilMissions.audit.chat"))
                }
            }

            "council-mission-scrying" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                actor.getKingdom()?.let { kingdom ->
                    val chosenFeatures = kingdom.getChosenFeatures(kingdom.getExplodedFeatures())
                    val vacancies = kingdom.vacancies(
                        choices = chosenFeatures,
                        bonusFeats = kingdom.bonusFeats,
                        government = kingdom.government,
                    )
                    val leaderActors = kingdom.parseLeaderActors()
                    val status = deriveCouncilMissionsStatus(kingdom, leaderActors, vacancies)
                    if (!status.canScrying) return@buildPromise

                    val cooldowns = kingdom.councilCooldowns ?: RawCouncilCooldowns(0, 0, 0, 0)
                    cooldowns.scrying = 3
                    kingdom.councilCooldowns = cooldowns
                    kingdom.resourcePoints.now -= 4
                    val modifier = RawModifier(
                        id = v4(),
                        type = "circumstance",
                        value = 2,
                        name = t("kingdom.councilMissions.scrying.btn"),
                        enabled = true,
                        turns = 1,
                        selector = "check",
                        applyIf = arrayOf(
                            RawEq(eq = tupleOf("@skill", "exploration"))
                        ),
                        requiresTranslation = false,
                    )
                    kingdom.modifiers = kingdom.modifiers + modifier
                    actor.setKingdom(kingdom)
                    postChatMessage(t("kingdom.councilMissions.scrying.chat"))
                }
            }

            "council-mission-lockdown" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                actor.getKingdom()?.let { kingdom ->
                    val chosenFeatures = kingdom.getChosenFeatures(kingdom.getExplodedFeatures())
                    val vacancies = kingdom.vacancies(
                        choices = chosenFeatures,
                        bonusFeats = kingdom.bonusFeats,
                        government = kingdom.government,
                    )
                    val leaderActors = kingdom.parseLeaderActors()
                    val status = deriveCouncilMissionsStatus(kingdom, leaderActors, vacancies)
                    if (!status.canLockdown) return@buildPromise

                    val cooldowns = kingdom.councilCooldowns ?: RawCouncilCooldowns(0, 0, 0, 0)
                    cooldowns.lockdown = 5
                    kingdom.councilCooldowns = cooldowns
                    kingdom.resourcePoints.now -= 2
                    val chosenFeats = kingdom.getChosenFeats(chosenFeatures)
                    kingdom.unrest = kingdom.addUnrest(-2, chosenFeats)
                    actor.setKingdom(kingdom)
                    postChatMessage(t("kingdom.councilMissions.lockdown.chat"))
                }
            }

            "council-mission-feast" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                actor.getKingdom()?.let { kingdom ->
                    val chosenFeatures = kingdom.getChosenFeatures(kingdom.getExplodedFeatures())
                    val vacancies = kingdom.vacancies(
                        choices = chosenFeatures,
                        bonusFeats = kingdom.bonusFeats,
                        government = kingdom.government,
                    )
                    val leaderActors = kingdom.parseLeaderActors()
                    val status = deriveCouncilMissionsStatus(kingdom, leaderActors, vacancies)
                    if (!status.canFeast) return@buildPromise

                    val cooldowns = kingdom.councilCooldowns ?: RawCouncilCooldowns(0, 0, 0, 0)
                    cooldowns.feast = 4
                    kingdom.councilCooldowns = cooldowns
                    kingdom.commodities.now.food -= 3
                    val modifier = RawModifier(
                        id = v4(),
                        type = "status",
                        value = 1,
                        name = t("kingdom.councilMissions.feast.btn"),
                        enabled = true,
                        turns = 1,
                        selector = "check",
                        applyIf = arrayOf(
                            RawEq(eq = tupleOf("@ability", "loyalty"))
                        ),
                        requiresTranslation = false,
                    )
                    kingdom.modifiers = kingdom.modifiers + modifier
                    val chosenFeats = kingdom.getChosenFeats(chosenFeatures)
                    kingdom.unrest = kingdom.addUnrest(-1, chosenFeats)
                    actor.setKingdom(kingdom)
                    postChatMessage(t("kingdom.councilMissions.feast.chat"))
                }
            }

            "end-turn" -> buildPromise {
                if (!game.user.isGM) {
                    ui.notifications.warn(t("kingdom.turn.endTurnGmOnly"))
                    return@buildPromise
                }
                actor.getKingdom()?.let { kingdom ->
                    val nextTurn = (kingdom.currentTurn ?: 0) + 1
                    if (!confirm(t("kingdom.turn.confirmEndTurn", recordOf("turn" to nextTurn)))) {
                        return@buildPromise
                    }
                    performEndTurn(game, actor, kingdom)
                }
            }

            "open-turn-wizard" -> buildPromise {
                TurnWizardApplication(actor).render(true)
                // GM-only, GM-whispered recap of the previous turn; idempotent per turn.
                postLastTurnRecap(game, actor)
            }

            "settlement-size-info" -> buildPromise {
                settlementSizeHelp()
            }

            "kingdom-size-info" -> buildPromise {
                kingdomSizeHelp()
            }

            "consumption-breakdown" -> buildPromise {
                actor.getKingdom()?.let { kingdom ->
                    val realm = game.getRealmData(actor, kingdom)
                    val settlements = kingdom.getAllSettlements(game)
                    val consumption = calculateConsumption(
                        seasonal = game.currentSeasonalModifiers(),
                        settlements = settlements.allSettlements,
                        realmData = realm,
                        armyConsumption = kingdom.consumption.armies,
                        now = kingdom.consumption.now,
                        expressionContext = kingdom.createSimpleContext(settlements),
                        modifiers = kingdom.createModifiers(settlements),
                    )
                    consumptionBreakdown(consumption.toContext())
                }
            }

            "skip-collect-taxes" -> buildPromise {
                actor.getKingdom()?.let { kingdom ->
                    val succeeded = d20Check(
                        dc = 11,
                        flavor = t("kingdom.skipCollectTaxesCheck"),
                    ).degreeOfSuccess.succeeded()
                    if (succeeded && kingdom.unrest > 0) {
                        postChatMessage(t("kingdom.reducing1Unrest"))
                        kingdom.unrest = (kingdom.unrest - 1).coerceIn(0, Int.MAX_VALUE)
                    }
                    actor.setKingdom(kingdom)
                }
            }

            "roll-skill-check" -> buildPromise {
                val skill = target.dataset["skill"]?.let { KingdomSkill.fromString(it) }
                checkNotNull(skill)
                val kingdom = actor.getKingdom()
                checkNotNull(kingdom)
                kingdomCheckDialog(
                    game = game,
                    kingdom = kingdom,
                    kingdomActor = actor,
                    check = CheckType.RollSkill(skill),
                    selectedLeader = game.getActiveLeader(),
                    groups = emptyArray(),
                    events = emptyList(),
                )
            }

            "inspect-leader-skills" -> {
                val kingdom = actor.getKingdom() ?: return
                configureLeaderSkills(kingdom.settings.leaderSkills, true) {}
            }

            "inspect-kingdom-skills" -> {
                val kingdom = actor.getKingdom() ?: return
                configureLeaderKingdomSkills(kingdom.settings.leaderKingdomSkills, true) {}
            }

            "toggle-activity-performed" -> buildPromise {
                val activityId = target.dataset["activity"]
                checkNotNull(activityId)
                actor.toggleActivityPerformed(activityId)
                render()
            }

            "dispatch-caravan" -> buildPromise {
                // Caravan dispatch and recall move real resources -- goods, gold and RP -- so they
                // are GM-only. The template gate is presentation: players are OWNERs of the party
                // actor, so an ungated handler is reachable regardless of what the sheet renders.
                if (!game.user.isGM) return@buildPromise
                val kingdom = getKingdom()
                val claimedHexes = runCatching {
                    kingmaker.state.hexes.asSequence()
                        .filter { (_, hex) -> hex.claimed == true }
                        .mapNotNull { (key, _) ->
                            key.toIntOrNull()?.let {
                                CaravanHexOption(hexKey = key, label = "${it / 1000}.${it % 1000}")
                            }
                        }
                        .toList()
                }.getOrDefault(emptyList())
                val partners = kingdom.groups
                    .filter { !it.hexKey.isNullOrBlank() }
                    .map { CaravanPartnerOption(name = it.name, hexKey = it.hexKey!!, label = it.name, atWar = it.atWar) }
                // HOLE A embargo: partners at war are not dispatchable (SelectOption has no disabled
                // state, so they're filtered out of the list rather than shown greyed); the callback
                // also guards, so war declared while the dialog is open still blocks the dispatch.
                val dispatchablePartners = partners.filter { canDispatchCaravanTo(it.atWar) }
                when {
                    claimedHexes.isEmpty() -> ui.notifications.warn(t("kingdom.caravans.noOriginHexes"))
                    partners.isEmpty() -> ui.notifications.warn(t("kingdom.caravans.noPartners"))
                    dispatchablePartners.isEmpty() -> ui.notifications.warn(t("kingdom.caravans.allPartnersEmbargoed"))
                    else -> CaravanDispatchDialog(
                        hexes = claimedHexes,
                        partners = dispatchablePartners,
                        commodities = listOf("food", "lumber", "stone", "ore", "luxuries"),
                    ) { req ->
                        buildPromise {
                            val current = getKingdom()
                            val eta = computeCaravanEtaTurns(
                                KingmakerHexGridProvider(),
                                req.originHexKey,
                                req.partnerHexKey,
                            )
                            if (eta == null) {
                                ui.notifications.warn(t("kingdom.caravans.unreachable"))
                            } else {
                                val etaTurns = eta
                                val partner = current.groups.find { it.name == req.partnerName }
                                if (!canDispatchCaravanTo(partner?.atWar == true)) {
                                    // War declared against this partner since the dialog opened — no
                                    // silent trade with a besieging faction.
                                    ui.notifications.warn(t("kingdom.caravans.allPartnersEmbargoed"))
                                    return@buildPromise
                                }
                                val originLabel = claimedHexes.find { it.hexKey == req.originHexKey }?.label
                                    ?: req.originHexKey
                                fun makeCaravan(kind: String, cargoRp: Int?) = RawCaravan(
                                    id = "caravan-${kotlin.js.Date().getTime().toLong()}",
                                    kind = kind,
                                    originHexKey = req.originHexKey,
                                    destHexKey = req.partnerHexKey,
                                    originLabel = originLabel,
                                    destLabel = req.partnerName,
                                    partnerName = req.partnerName,
                                    cargoCommodity = req.commodity,
                                    cargoAmount = req.amount,
                                    cargoRp = cargoRp,
                                    etaTurns = etaTurns,
                                    turnsRemaining = etaTurns,
                                    status = "inTransit",
                                )
                                if (req.kind == "buyFromPartner") {
                                    val cost = caravanPurchaseCost(
                                        req.commodity, req.amount, partner?.standing, partner?.allianceLevel,
                                    )
                                    if (current.resourcePoints.now < cost) {
                                        ui.notifications.warn(t("kingdom.caravans.notEnoughRp"))
                                    } else {
                                        current.resourcePoints.now = current.resourcePoints.now - cost
                                        current.caravans = (current.caravans ?: emptyArray()) +
                                            makeCaravan("buyFromPartner", cost)
                                        actor.setKingdom(current)
                                    }
                                } else {
                                    val now = current.commodities.now.asDynamic()
                                    val available = (now[req.commodity].unsafeCast<Int?>()) ?: 0
                                    if (available < req.amount) {
                                        ui.notifications.warn(t("kingdom.caravans.notEnough"))
                                    } else {
                                        now[req.commodity] = available - req.amount
                                        current.caravans = (current.caravans ?: emptyArray()) +
                                            makeCaravan("sellToPartner", null)
                                        actor.setKingdom(current)
                                    }
                                }
                            }
                        }
                    }.launch()
                }
            }

            "recall-caravan" -> buildPromise {
                // Caravan dispatch and recall move real resources -- goods, gold and RP -- so they
                // are GM-only. The template gate is presentation: players are OWNERs of the party
                // actor, so an ungated handler is reachable regardless of what the sheet renders.
                if (!game.user.isGM) return@buildPromise
                val id = target.dataset["caravanId"]
                checkNotNull(id)
                val current = getKingdom()
                val caravan = (current.caravans ?: emptyArray()).find { it.id == id }
                if (caravan != null && caravan.status == "inTransit") {
                    if (caravan.kind == "buyFromPartner") {
                        // RP was spent at dispatch — refund it.
                        current.resourcePoints.now = current.resourcePoints.now + (caravan.cargoRp ?: 0)
                    } else {
                        // Commodities were loaded at dispatch — return them.
                        val commodity = caravan.cargoCommodity
                        if (commodity != null) {
                            val now = current.commodities.now.asDynamic()
                            now[commodity] = ((now[commodity].unsafeCast<Int?>()) ?: 0) + caravan.cargoAmount
                        }
                    }
                    current.caravans = (current.caravans ?: emptyArray()).filter { it.id != id }.toTypedArray()
                    actor.setKingdom(current)
                }
            }

            "dispatch-shipment" -> buildPromise {
                // Caravan dispatch and recall move real resources -- goods, gold and RP -- so they
                // are GM-only. The template gate is presentation: players are OWNERs of the party
                // actor, so an ungated handler is reachable regardless of what the sheet renders.
                if (!game.user.isGM) return@buildPromise
                val kingdom = getKingdom()
                fun hexCoords(h: String) =
                    h.toIntOrNull()?.let { " (${it / 1000}.${it % 1000})" } ?: " ($h)"
                val settlementOptions = kingdom.settlements
                    .filter { !it.hexKey.isNullOrBlank() }
                    .map { raw ->
                        val sceneName = game.scenes.get(raw.sceneId)?.name ?: raw.sceneId
                        val hexKeyStr = raw.hexKey!!
                        CaravanHexOption(hexKey = hexKeyStr, label = sceneName + hexCoords(hexKeyStr))
                    }
                // Trade-partner factions (groups with a map-hex) are routable origins/destinations too —
                // this is how you ship from a non-kingdom location such as Restov.
                val factionOptions = kingdom.groups
                    .filter { !it.hexKey.isNullOrBlank() }
                    .map { CaravanHexOption(hexKey = it.hexKey!!, label = it.name + hexCoords(it.hexKey!!)) }
                // The party itself is a routable destination/origin: derive its current hex from its token
                // on the active hex-grid scene. Shipments to/from a moving party carry a surcharge.
                val partyHex: String? = run {
                    val scene = game.scenes.active
                    if (scene == null || !scene.grid.isHexagonal) return@run null
                    val token = scene.tokens.contents.find { it.actorId == actor.id } ?: return@run null
                    val grid = scene.grid
                    val center = Point(
                        x = token.x + grid.sizeX / 2.0,
                        y = token.y + grid.sizeY / 2.0,
                    )
                    val offset = grid.getOffset(center)
                    (offset.i * 1000 + offset.j).toString()
                }
                val partyOption = partyHex?.let {
                    CaravanHexOption(hexKey = it, label = t("kingdom.caravans.partyLocation") + hexCoords(it))
                }
                val locations = settlementOptions + factionOptions + listOfNotNull(partyOption)
                if (locations.isEmpty()) {
                    ui.notifications.warn(t("kingdom.caravans.noShipmentLocations"))
                } else {
                    CaravanShipmentDialog(locations) { req ->
                        buildPromise {
                            val current = getKingdom()
                            val route = computeCaravanRoute(
                                KingmakerHexGridProvider(),
                                req.originHexKey,
                                req.destHexKey,
                            )
                            if (route == null) {
                                ui.notifications.warn(t("kingdom.caravans.unreachable"))
                            } else {
                                val routeCost = route.totalCost
                                val speed = when (req.caravanType) {
                                    "light" -> 4.0
                                    "heavy" -> 2.0
                                    else -> 3.0
                                }
                                val baseCostPerType = when (req.caravanType) {
                                    "light" -> 5.0
                                    "heavy" -> 20.0
                                    else -> 10.0
                                }
                                val totalBulk = parseBulk(req.itemBulk) * req.itemQuantity
                                val capacity = caravanBulkCapacity(req.caravanType)
                                if (totalBulk > capacity) {
                                    val shownBulk = kotlin.math.round(totalBulk * 10.0) / 10.0
                                    ui.notifications.warn(
                                        t("kingdom.caravans.overCapacity", recordOf("bulk" to shownBulk.toString(), "capacity" to capacity.toString()))
                                    )
                                    return@buildPromise
                                }
                                val baseFee = (baseCostPerType * routeCost) + (totalBulk * 0.5 * routeCost)
                                // Surcharge when the party's current hex is either endpoint (mobile delivery).
                                val partyInvolved = partyHex != null && (req.originHexKey == partyHex || req.destHexKey == partyHex)
                                val shipmentCost = if (partyInvolved) baseFee * CARAVAN_PARTY_SURCHARGE else baseFee
                                val finalGoldCost = shipmentCost + (if (req.isPurchase) req.itemPriceGp * req.itemQuantity else 0.0)
                                val roundedGoldCost = kotlin.math.round(finalGoldCost * 100.0) / 100.0

                                // The item-level purchase gate only applies when buying FROM one of our own
                                // settlements; external markets (factions / free-form locations) are assumed
                                // to stock the requested item.
                                val originSettlement = current.settlements.find { it.hexKey == req.originHexKey }
                                if (req.isPurchase && originSettlement != null) {
                                    val parsedSettlements = current.getAllSettlements(game).allSettlements
                                    val originParsed = parsedSettlements.find { it.id == originSettlement.sceneId }
                                    val originPurchaseLevel = originParsed?.itemPurchaseLevel ?: 0
                                    if (req.itemLevel > originPurchaseLevel) {
                                        ui.notifications.warn(
                                            t("kingdom.caravans.itemLevelTooHigh", recordOf("level" to originPurchaseLevel.toString()))
                                        )
                                        return@buildPromise
                                    }
                                }

                                val availableGp = (actor.asDynamic().inventory?.coins?.goldValue).unsafeCast<Double?>() ?: 0.0
                                if (availableGp < roundedGoldCost) {
                                    ui.notifications.warn(t("kingdom.caravans.notEnoughGold"))
                                } else {
                                    // Party gold lives in the PF2e inventory (actor.inventory.coins), not
                                    // system.resources.coins — which does not exist on a party actor, so the old
                                    // path always read 0 and every dispatch failed as "not enough gold". Charge
                                    // whole gp via removeCoins(byValue) so higher denominations are broken as needed.
                                    val costGp = kotlin.math.ceil(roundedGoldCost).toInt().coerceAtLeast(0)
                                    val payCoins = js("{}")
                                    payCoins.gp = costGp
                                    val paid = actor.asDynamic().inventory
                                        .removeCoins(payCoins, js("{ byValue: true }"))
                                        .unsafeCast<kotlin.js.Promise<Boolean>>().await()
                                    if (paid == false) {
                                        ui.notifications.warn(t("kingdom.caravans.notEnoughGold"))
                                        return@buildPromise
                                    }

                                    val etaTurns = caravanEtaTurns(routeCost, speed)
                                    val originLabel = locations.find { it.hexKey == req.originHexKey }?.label
                                        ?: req.originHexKey.toIntOrNull()?.let { "${it / 1000}.${it % 1000}" } ?: req.originHexKey
                                    val destLabel = locations.find { it.hexKey == req.destHexKey }?.label
                                        ?: req.destHexKey.toIntOrNull()?.let { "${it / 1000}.${it % 1000}" } ?: req.destHexKey

                                    val newShipment = RawCaravanShipment(
                                        id = "shipment-${kotlin.js.Date().getTime().toLong()}",
                                        itemName = req.itemName,
                                        itemQuantity = req.itemQuantity,
                                        itemLevel = req.itemLevel,
                                        itemBulk = req.itemBulk,
                                        itemPriceGp = req.itemPriceGp,
                                        originHexKey = req.originHexKey,
                                        destHexKey = req.destHexKey,
                                        originLabel = originLabel,
                                        destLabel = destLabel,
                                        caravanType = req.caravanType,
                                        goldCost = roundedGoldCost,
                                        etaTurns = etaTurns,
                                        turnsRemaining = etaTurns,
                                        path = route.path.toTypedArray(),
                                        currentHexKey = req.originHexKey,
                                        status = "inTransit"
                                    )
                                    current.shipments = (current.shipments ?: emptyArray()) + newShipment
                                    actor.setKingdom(current)
                                }
                            }
                        }
                    }.launch()
                }
            }

            "recall-shipment" -> buildPromise {
                // Caravan dispatch and recall move real resources -- goods, gold and RP -- so they
                // are GM-only. The template gate is presentation: players are OWNERs of the party
                // actor, so an ungated handler is reachable regardless of what the sheet renders.
                if (!game.user.isGM) return@buildPromise
                val id = target.dataset["shipmentId"]
                checkNotNull(id)
                val current = getKingdom()
                val shipment = (current.shipments ?: emptyArray()).find { it.id == id }
                if (shipment != null && shipment.status == "inTransit") {
                    // Refund into the PF2e inventory (see dispatch note on the gold path).
                    val refundGp = kotlin.math.ceil(shipment.goldCost).toInt().coerceAtLeast(0)
                    val refundCoins = js("{}")
                    refundCoins.gp = refundGp
                    actor.asDynamic().inventory.addCoins(refundCoins).unsafeCast<kotlin.js.Promise<*>>().await()

                    current.shipments = (current.shipments ?: emptyArray()).filter { it.id != id }.toTypedArray()
                    actor.setKingdom(current)
                }
            }

            "perform-activity" -> buildPromise {
                val activityId = target.dataset["activity"]
                checkNotNull(activityId)
                val kingdom = actor.getKingdom()
                checkNotNull(kingdom)
                val activity = kingdom.getActivity(activityId)
                checkNotNull(activity)
                actor.getKingdom()?.let { kingdom ->
                    val allFeatures = kingdom.getExplodedFeatures()
                    val chosenFeatures = kingdom.getChosenFeatures(allFeatures)
                    val chosenFeats = kingdom.getChosenFeats(chosenFeatures)
                    val government = kingdom.getChosenGovernment()
                    val kingdomRanks = kingdom.parseSkillRanks(
                        chosenFeatures = chosenFeatures,
                        chosenFeats = chosenFeats,
                        government = government,
                    )
                    when (activityId) {
                        "build-structure" -> {
                            StructureBrowser(
                                actor = actor,
                                kingdom = kingdom,
                                worldStructures = game.getImportedStructures(),
                                game = game,
                                kingdomRanks = kingdomRanks,
                                chosenFeats = chosenFeats,
                                realmData = game.getRealmData(actor, kingdom),
                            ).launch()
                        }

                        "recruit-army" -> armyBrowser(
                            game = game,
                            kingdomActor = actor,
                            kingdom = kingdom
                        )

                        "train-army" -> armyTacticsBrowser(
                            game = game,
                            kingdomActor = actor,
                            kingdom = kingdom
                        )

                        else -> {
                            val groups = when (activity.id) {
                                "request-foreign-aid",
                                "request-foreign-aid-vk",
                                    -> kingdom.groups.filter {
                                    it.relations != Relations.NONE.value
                                }.toTypedArray()

                                "send-diplomatic-envoy" -> kingdom.groups.filter {
                                    it.relations == Relations.NONE.value
                                }.toTypedArray()

                                "establish-trade-agreement" -> kingdom.groups.filter {
                                    it.relations == Relations.DIPLOMATIC_RELATIONS.value
                                }.toTypedArray()

                                "pledge-of-fealty" -> kingdom.groups.filterNot {
                                    it.preventPledgeOfFealty
                                }.toTypedArray()

                                else -> kingdom.groups
                            }
                            val events = when (activity.id) {
                                else -> kingdom.getOngoingEvents()
                            }
                            val deployedArmy = if (activity.id == "deploy-army") {
                                game.getSelectedArmies().firstOrNull()
                            } else {
                                null
                            }
                            // Cleanse Item is the one activity whose DC is not the kingdom's own:
                            // it counteracts a specific item, so it needs the item picked before
                            // the check exists. Prepare first, then roll against that DC.
                            if (activity.id == "cleanse-item") {
                                openCleanseItemDialog(
                                    kingdomLevel = kingdom.level,
                                    settlements = kingdom.getAllSettlements(game).allSettlements.map { settlement ->
                                        CleanseItemSettlement(
                                            id = settlement.id,
                                            name = settlement.name,
                                            structureNames = settlement.constructedStructures.map { it.name }.toSet(),
                                        )
                                    },
                                ) { preparation ->
                                    buildPromise {
                                        rollCleanseItem(
                                            game = game,
                                            actor = actor,
                                            kingdom = kingdom,
                                            activity = activity,
                                            preparation = preparation,
                                            groups = groups,
                                            events = events,
                                        )
                                        render()
                                    }
                                }
                                return@buildPromise
                            }
                            kingdomCheckDialog(
                                game = game,
                                kingdom = kingdom,
                                kingdomActor = actor,
                                check = CheckType.PerformActivity(activity),
                                selectedLeader = game.getActiveLeader(),
                                groups = groups,
                                events = events,
                                afterRoll = { degree ->
                                    actor.recordActivityPerformed(activity.id)
                                    if (activity.id == "deploy-army") {
                                        // The army selected when the check was rolled is the one
                                        // whose weary/mired badges modified it, so it is the one the
                                        // outcome lands on. Captured before the dialog so a change
                                        // of token selection mid-roll cannot retarget the effects.
                                        offerDeployArmyOutcome(
                                            game = game,
                                            actor = actor,
                                            army = deployedArmy,
                                            degree = degree,
                                            cardId = "deploy-${kotlin.js.Date().getTime().toLong()}",
                                        )
                                    }
                                    render()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    /** (sceneId, name) pairs for the quest access-grant settlement picker. */
    private fun questSettlementOptions(): List<Pair<String, String>> =
        getKingdom().getAllSettlements(game).allSettlements.map { it.id to it.name }

    private suspend fun postAddToOngoingEvents(
        result: TableAndDraw?,
        rollMode: RollMode,
        kingdom: KingdomData,
    ) {
        result
            ?.draw
            ?.results
            ?.firstNotNullOf { it.text }
            ?.stripHtml()
            ?.let { kingdom.getEvent(it) }
            ?.let { event ->
                val modifier = event.modifier
                val traits = event.traits.mapNotNull { KingdomEventTrait.fromString(it) }
                val stages = event.stages.map {
                    val leader = Leader.fromString(it.leader) ?: Leader.RULER
                    val criticalSuccess = enrichHtml(it.criticalSuccess?.msg ?: "")
                    val success = enrichHtml(it.success?.msg ?: "")
                    val failure = enrichHtml(it.failure?.msg ?: "")
                    val criticalFailure = enrichHtml(it.criticalFailure?.msg ?: "")
                    recordOf(
                        "leader" to t(leader),
                        "skills" to it.skills
                            .mapNotNull { KingdomSkill.fromString(it) }
                            .map { t(it) }
                            .toTypedArray(),
                        "criticalSuccess" to criticalSuccess,
                        "success" to success,
                        "failure" to failure,
                        "criticalFailure" to criticalFailure,
                    )
                }.toTypedArray()
                val description = enrichHtml(event.description)
                postChatTemplate(
                    templatePath = "chatmessages/event.hbs",
                    templateContext = recordOf(
                        "actorUuid" to actor.uuid,
                        "eventId" to event.id,
                        "label" to event.name + if (modifier != null && modifier != 0) " (${modifier.formatAsModifier()})" else "",
                        "traits" to traits.map { t(it) }.toTypedArray(),
                        "location" to event.location,
                        "special" to event.special,
                        "description" to description,
                        "resolution" to event.resolution,
                        "stages" to stages
                    ),
                    rollMode = rollMode,
                )
            }
    }

    suspend fun importStructures() {
        if (game.importStructures().isNotEmpty()) {
            ui.notifications.info(t("kingdom.importedStructures"))
        }
    }

    suspend fun importSettlement(
        sceneName: String,
        terrain: SettlementTerrain,
        waterBorders: Int,
        type: SettlementType,
        layoutType: SettlementLayoutType,
    ) {
        val scene = game.importSettlementScene(
            sceneName = sceneName,
            terrain = terrain,
            waterBorders = waterBorders,
            layoutType = layoutType,
        )
        scene.id?.let {
            val kingdom = getKingdom()
            kingdom.settlements += RawSettlement(
                sceneId = it,
                lots = 1,
                level = 1,
                type = type.value,
                secondaryTerritory = false,
                manualSettlementLevel = false,
                waterBorders = waterBorders,
                layoutType = layoutType.value,
            )
            kingdom.activeSettlement = it
            actor.setKingdom(kingdom)
            scene.activate().await()
        }
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<KingdomSheetContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        val kingdom = getKingdom()
        val isGM = game.user.isGM
        val ownedRoles = getOwnedLeaderRoles(game, kingdom)
        
        // Merge manual quests and generated campaign quests
        val manualQuests = kingdom.quests ?: emptyArray()
        val campaignQuestsList = (kingdom.campaignQuests ?: emptyArray<Any>()).map { cq ->
            val d = cq.asDynamic()
            val status = d.status as? String ?: "active"
            val rewardsDyn = d.rewards
            val commodities = rewardsDyn.commodities
            
            val mappedRewards = js("({})")
            mappedRewards.rp = rewardsDyn.rp ?: 0
            mappedRewards.xp = rewardsDyn.xp ?: 0
            mappedRewards.unrest = rewardsDyn.unrestReduction ?: 0
            mappedRewards.food = if (commodities != null) commodities["food"] ?: 0 else 0
            mappedRewards.lumber = if (commodities != null) commodities["lumber"] ?: 0 else 0
            mappedRewards.stone = if (commodities != null) commodities["stone"] ?: 0 else 0
            mappedRewards.ore = if (commodities != null) commodities["ore"] ?: 0 else 0
            mappedRewards.luxuries = if (commodities != null) commodities["luxuries"] ?: 0 else 0
            
            val mappedType = d.type
            val typeStr = if (mappedType != null && js("typeof mappedType === 'object'") as Boolean) {
                mappedType.value as? String ?: "other"
            } else {
                mappedType as? String ?: "other"
            }
            
            js("""({
                id: d.id,
                title: d.name,
                description: d.description,
                giver: d.sourceEventName || "",
                sourceEventName: d.sourceEventName || null,
                status: status,
                type: typeStr,
                level: d.level || null,
                target: null,
                rewards: mappedRewards,
                flavorTextCompleted: "",
                notes: null,
                hidden: false,
                source: null,
                createdAtLabel: null,
                updatedAtLabel: null,
                showUpdated: false,
                generatedByEvent: true,
                turnsRemaining: d.turnsRemaining
            })""")
        }
        
        val allQuests = manualQuests.map { q ->
            val d = q.asDynamic()
            js("""({
                id: d.id,
                title: d.title,
                description: d.description,
                giver: d.giver,
                status: d.status,
                type: d.type,
                category: d.category,
                level: d.level || null,
                target: d.target,
                rewards: d.rewards,
                flavorTextCompleted: d.flavorTextCompleted,
                notes: d.notes,
                hidden: d.hidden || false,
                source: d.source,
                createdAtLabel: d.createdAt ? new Date(d.createdAt).toLocaleString() : null,
                updatedAtLabel: d.updatedAt ? new Date(d.updatedAt).toLocaleString() : null,
                showUpdated: (d.updatedAt && d.createdAt && d.updatedAt !== d.createdAt) ? true : false,
                generatedByEvent: false,
                turnsRemaining: null
            })""")
        } + campaignQuestsList

        // Players never see hidden quests; the GM sees them greyed out (see template).
        val visibleQuests = if (isGM) allQuests else allQuests.filter { (it.hidden as? Boolean) != true }
        // Default ordering: by level ascending; quests without a level sort FIRST (they need to be
        // dealt with sooner). Sort is stable, so quests sharing a level keep their creation order.
        fun questLevelKey(q: dynamic): Double = (q.level as? Number)?.toDouble() ?: Double.NEGATIVE_INFINITY
        val activeQuests = visibleQuests
            .filter { (it.status as? String) == "active" }
            .sortedBy { questLevelKey(it) }
            .toTypedArray()
        val completedQuests = visibleQuests
            .filter {
                val s = it.status as? String
                s == "completed" || s == "failed"
            }
            .sortedBy { questLevelKey(it) }
            .toTypedArray()
        val allFeatures = kingdom.getExplodedFeatures()
        val chosenFeatures = kingdom.getChosenFeatures(allFeatures)
        val vacancies = kingdom.vacancies(
            choices = chosenFeatures,
            bonusFeats = kingdom.bonusFeats,
            government = kingdom.government,
        )
        val realm = game.getRealmData(actor, kingdom)
        val settlements = kingdom.getAllSettlements(game)
        val controlDc = calculateControlDC(kingdom.level, realm, vacancies.ruler)
        val globalBonuses = evaluateGlobalBonuses(settlements.allSettlements)
        val chosenFeats = kingdom.getChosenFeats(chosenFeatures)
        val leaderActors = kingdom.parseLeaderActors()
        val storage = calculateStorage(realm, settlements.allSettlements)
        val leaderSkills = kingdom.settings.leaderSkills.parse()
        val defaultLeaderBonuses = if (kingdom.settings.enableLeadershipModifiers) {
            getHighestLeadershipModifiers(
                leaderActors = leaderActors,
                leaderSkills = leaderSkills,
            )
        } else {
            calculateInvestedBonus(kingdom.level, leaderActors)
        }
        val expressionContext = kingdom.createSimpleContext(settlements)
        val modifiers = kingdom.createModifiers(settlements)
        val consumption = calculateConsumption(
            seasonal = game.currentSeasonalModifiers(),
            settlements = settlements.allSettlements,
            realmData = realm,
            armyConsumption = kingdom.consumption.armies,
            now = kingdom.consumption.now,
            expressionContext = expressionContext,
            modifiers = modifiers,
        )
        val automateResources = kingdom.settings.automateResources != AutomateResources.MANUAL.value
        val projected = if (automateResources) {
            calculateProjectedResources(
                seasonal = game.currentSeasonalModifiers(),
                kingdomData = kingdom,
                realmData = realm,
                chosenFeats = chosenFeats,
                settlements = settlements.allSettlements,
                expressionContext = expressionContext,
                modifiers = modifiers,
            )
        } else {
            null
        }
        val kingdomNameInput = TextInput(
            name = "name",
            label = t("applications.kingdom"),
            value = kingdom.name,
            elementClasses = listOf("km-width-medium"),
            labelClasses = listOf("km-slim-inputs"),
            required = false,
            stacked = false,
            readonly = !isGM,
        )
        val settlementInput = Select(
            name = "activeSettlement",
            label = t("kingdom.activeSettlement"),
            value = kingdom.activeSettlement,
            options = settlements.allSettlements.map { SelectOption(it.name, it.id) },
            required = false,
            labelClasses = listOf("km-slim-inputs"),
            disabled = !isGM,
        )
        val xpInput = NumberInput(
            name = "xp",
            label = t("applications.xp"),
            hideLabel = true,
            elementClasses = listOf("km-width-small", "km-slim-inputs"),
            value = kingdom.xp,
            stacked = false,
            readonly = !isGM,
        )
        val xpThresholdInput = NumberInput(
            name = "xpThreshold",
            label = t("kingdom.xpThreshold"),
            hideLabel = true,
            elementClasses = listOf("km-width-small", "km-slim-inputs"),
            value = kingdom.xpThreshold,
            stacked = false,
            readonly = !isGM,
        )
        val levelInput = Select.range(
            name = "level",
            label = t("applications.level"),
            value = kingdom.level,
            elementClasses = listOf("km-width-small"),
            labelClasses = listOf("km-slim-inputs"),
            from = 1,
            to = 20,
            stacked = false,
            disabled = !isGM,
        )
        val atWarInput = CheckboxInput(
            name = "atWar",
            value = kingdom.atWar,
            label = t("kingdom.atWar"),
            disabled = !isGM,
        )
        val anarchyAt = calculateAnarchy(chosenFeats)
        val unrestInput = Select.range(
            name = "unrest",
            label = t("kingdom.unrest"),
            value = kingdom.unrest,
            from = 0,
            to = anarchyAt,
            stacked = false,
            elementClasses = listOf("km-width-small"),
            labelClasses = listOf("km-slim-inputs"),
            disabled = !isGM,
        )
        val sizeInput = if (kingdom.settings.automateResources == AutomateResources.MANUAL.value) {
            NumberInput(
                name = "size",
                label = t("kingdom.size"),
                value = kingdom.size,
                elementClasses = listOf("km-slim-inputs", "km-width-small"),
                hideLabel = true,
                stacked = false,
                readonly = !isGM,
            )
        } else {
            HiddenInput(
                name = "size",
                value = kingdom.size.toString(),
                overrideType = OverrideType.NUMBER,
            )
        }
        val supernaturalSolutionsInput = NumberInput(
            name = "supernaturalSolutions",
            value = kingdom.supernaturalSolutions,
            label = t("kingdom.supernaturalSolutions"),
            stacked = false,
            elementClasses = listOf("km-width-small"),
            labelClasses = listOf("km-slim-inputs"),
            readonly = !isGM,
        )
        val creativeSolutionsInput = NumberInput(
            name = "creativeSolutions",
            value = kingdom.creativeSolutions,
            label = t("kingdom.creativeSolutions"),
            stacked = false,
            elementClasses = listOf("km-width-small"),
            labelClasses = listOf("km-slim-inputs"),
            readonly = !isGM,
        )
        val bonusResourceDiceInput = NumberInput(
            name = "bonusResourceDice",
            value = kingdom.bonusResourceDice,
            label = t("kingdom.bonusResourceDice"),
            stacked = false,
            elementClasses = listOf("km-width-small"),
            labelClasses = listOf("km-slim-inputs"),
            readonly = !isGM,
        )
        val unrestPenalty = calculateUnrestPenalty(kingdom.unrest)
        val feats = kingdom.getFeats()
            .filter { it.id !in kingdom.featBlacklist }
            .toTypedArray()
        val increaseScorePicksBy = kingdom.settings.increaseScorePicksBy
        val kingdomSectionNav = createKingdomSectionNav()
        val heartlandBlacklist = kingdom.heartlandBlacklist.toSet()
        val charterBlacklist = kingdom.charterBlacklist.toSet()
        val governmentBlacklist = kingdom.governmentBlacklist.toSet()
        val enabledHeartlands = kingdom.getHeartlands().filter { it.id !in heartlandBlacklist }
        val enabledCharters = kingdom.getCharters().filter { it.id !in charterBlacklist }
        val enabledGovernments = kingdom.getGovernments().filter { it.id !in governmentBlacklist }
        val notesContext = kingdom.notes.toContext(isGM)
        val government = kingdom.getChosenGovernment()
        val heartland = kingdom.getChosenHeartland()
        val charter = kingdom.getChosenCharter()
        val trainedSkills = kingdom.getTrainedSkills(chosenFeats, government)
        val resourceDiceNum = kingdom.getResourceDiceAmount(
            chosenFeats,
            settlements.allSettlements,
            kingdomLevel = kingdom.level,
        )
        // RAW grants 4 trained skill proficiencies at creation; the V&K charter/heartland
        // sub-rules each add one more independently of the V&K XP setting (see [vkInitialSkillSlots]).
        val initialProficiencies = (0 until vkInitialSkillSlots(kingdom.settings)).map { index ->
            val proficiency = kingdom.initialProficiencies.getOrNull(index)
                ?.let { KingdomSkill.fromString(it) }
            val result = Select(
                name = "initialProficiencies.$index",
                label = t("kingdom.skillTraining"),
                value = proficiency?.value,
                options = KingdomSkill.entries.filter { it == proficiency || it !in trainedSkills }
                    .map { SelectOption(value = it.value, label = t(it)) },
                required = false,
                hideLabel = true,
            ).toContext()
            result
        }.toTypedArray()
        val currentSceneId = game.scenes.current?.id
        val allSettlementSceneIds = kingdom.settlements.map { it.sceneId }.toSet()
        val canAddCurrentScene = currentSceneId != null && currentSceneId !in allSettlementSceneIds
        val kingdomSkillRanks = kingdom.parseSkillRanks(
            chosenFeatures = chosenFeatures,
            chosenFeats = chosenFeats,
            government = government,
        )
        val effectiveBlacklist = if (kingdom.settings.vanceAndKerensharaXP) {
            val baseIdsToDisable = vkToBaseActivityIds.values.toSet()
            (kingdom.activityBlacklist.toSet() - vkActivityIds + baseIdsToDisable).toTypedArray()
        } else {
            (kingdom.activityBlacklist.toSet() + vkActivityIds - vkToBaseActivityIds.values.toSet()).toTypedArray()
        }
        // Improve Settlement is a house rule behind its own toggle. It ships enabled: true, so
        // without this it showed on every kingdom sheet regardless of the setting.
        val houseRuleGated = if (game.settings.pfrpg2eKingdomCampingWeather.getCanUpgradeNonCapital()) {
            effectiveBlacklist.toSet()
        } else {
            effectiveBlacklist.toSet() + IMPROVE_SETTLEMENT_ACTIVITY
        }
        val effectiveStructureBlacklist = if (kingdom.settings.vanceAndKerensharaXP) {
            // V&K ON: hide base counterparts, show V&K variants
            vkToBaseStructureIds.values.toSet()
        } else {
            // V&K OFF: hide V&K variants, show base counterparts
            vkStructureIds.toSet()
        }
        kingdom.structureBlacklist = effectiveStructureBlacklist.toTypedArray()
        val activities = toActivitiesContext(
            actor = actor,
            activities = kingdom.getAllActivities(),
            activityBlacklist = houseRuleGated,
            unlockedActivities = globalBonuses.unlockedActivities,
            allowCapitalInvestment = settlements.current?.allowCapitalInvestment == true,
            kingdomSkillRanks = kingdomSkillRanks,
            chosenFeatures = chosenFeatures,
            openedDetails = openedDetails,
            kingdom = kingdom,
            chosenFeats = chosenFeats,
            activeLeader = game.getActiveLeader(),
            anarchyAt = anarchyAt,
            currentUnrest = kingdom.unrest,
            increaseLeadershipActivities = globalBonuses.increaseLeadershipActivities,
        )
        val leadersContext = kingdom.leaders.toContext(
            leaderActors = leaderActors,
            bonuses = defaultLeaderBonuses,
            vacancies = vacancies,
        )
        val automateStats = kingdom.settings.automateStats
        val checks = skillChecks(
            kingdom = kingdom,
            settlements = settlements,
            skillRanks = kingdomSkillRanks,
        )

        val abilityScores = kingdom.parseAbilityScores(
            chosenCharter = charter,
            chosenHeartland = heartland,
            chosenGovernment = government,
            chosenFeatures = chosenFeatures,
        )
        val cooldowns = kingdom.councilCooldowns ?: RawCouncilCooldowns(0, 0, 0, 0)
        val status = deriveCouncilMissionsStatus(kingdom, leaderActors, vacancies)
        val canAudit = status.canAudit
        val canScrying = status.canScrying
        val canLockdown = status.canLockdown
        val canFeast = status.canFeast
        val auditCooldownTurns = status.auditCooldownTurns
        val scryingCooldownTurns = status.scryingCooldownTurns
        val lockdownCooldownTurns = status.lockdownCooldownTurns
        val feastCooldownTurns = status.feastCooldownTurns
        val auditAffordable = status.auditAffordable
        val scryingAffordable = status.scryingAffordable
        val lockdownAffordable = status.lockdownAffordable
        val feastAffordable = status.feastAffordable
        val ongoingEvents = kingdom.getOngoingEvents().toContext(
            openedDetails = openedDetails,
            isGM = isGM,
            settlements = settlements
        )
        val campaignQuests = kingdom.campaignQuests ?: emptyArray<Any>()
        val generatedQuestCount = campaignQuests.filter {
            (it.asDynamic().status as? String) == "active" && (it.asDynamic().generatedByEvent as? Boolean) == true
        }.size
        val campaignKingdomEvents = kingdom.campaignKingdomEvents ?: emptyArray<Any>()
        val activeEventCount = campaignKingdomEvents.filter {
            (it.asDynamic().status as? String) == "active"
        }.size
        val questTimerChanges = emptyArray<Any>()
        val activeLeaderOptions = if (isGM) {
            Leader.entries.map { SelectOption(t(it), it.value) }
        } else {
            ownedRoles.map { SelectOption(t(it), it.value) }
        }
        val activeLeaderContext = Select(
            name = "activeLeader",
            label = t("kingdom.activeLeader"),
            required = false,
            value = game.getActiveLeader()?.value,
            options = activeLeaderOptions,
            labelClasses = listOf("km-slim-inputs"),
            disabled = !isGM && ownedRoles.isEmpty(),
        ).toContext()
        val activeSettlementType = settlements.current?.size?.type?.value ?: "none"
        val background = game.settings.pfrpg2eKingdomCampingWeather.resolveKingdomBackground(activeSettlementType)
        val history = kingdom.turnHistory ?: emptyArray()
        val hasData = history.isNotEmpty()

        val seriesList = if (hasData) {
            val allMetrics = listOf(
                "unrest" to "kingdom.analytics.unrest",
                "resourcePoints" to "kingdom.analytics.resourcePoints",
                "consumption" to "kingdom.analytics.consumption",
                "fame" to "kingdom.analytics.fame",
                "xpAwarded" to "kingdom.analytics.xpAwarded",
                "warPressure" to "kingdom.analytics.warPressure",
                "pressurePerTurn" to "kingdom.analytics.pressurePerTurn",
                "level" to "kingdom.analytics.level",
                "size" to "kingdom.analytics.size",
                "ruinCorruption" to "kingdom.analytics.ruinCorruption",
                "ruinCrime" to "kingdom.analytics.ruinCrime",
                "ruinDecay" to "kingdom.analytics.ruinDecay",
                "ruinStrife" to "kingdom.analytics.ruinStrife"
            )

            // Player-safe series filtering (extracted + unit-tested in AnalyticsContext.kt).
            val metrics = filterAnalyticsMetricsForUser(allMetrics, isGM)

            metrics.mapNotNull { (key, labelKey) ->
                val series = extractSeries(history, key, limit = if (analyticsWindowSize > 0) analyticsWindowSize else null)
                val summary = summarizeSeries(series)
                if (summary != null) {
                    val graphicPoints = mapSeriesToCoordinates(series, 400.0, 150.0, 15.0)
                    val pointsCtx = graphicPoints.map { p ->
                        MetricPointContext(turn = p.turn, value = p.value, x = p.x, y = p.y)
                    }.toTypedArray()
                    val polylinePoints = graphicPoints.joinToString(" ") { "${it.x},${it.y}" }

                    val thresholdLinesList = mutableListOf<ThresholdLineContext>()
                    
                    if (key == "level") {
                        val partyLevels = actor.partyMembers().map { it.system.details.level.value }
                        val avgPartyLevel = if (partyLevels.isNotEmpty()) partyLevels.sum() / partyLevels.size else null
                        val targetLevel = analyticsLevelTarget(
                            isGM = isGM,
                            chapterTargetLevel = kingdom.settings.pacingChapterTargetLevel(),
                            avgPartyLevel = avgPartyLevel,
                        )
                        val range = kingdom.settings.pacingLevelMismatchRange()
                        if (targetLevel != null) {
                            val upperVal = (targetLevel + range).toDouble()
                            val lowerVal = (targetLevel - range).toDouble()
                            
                            val minVal = series.minOf { it.second }
                            val maxVal = series.maxOf { it.second }
                            val valRange = maxVal - minVal
                            val innerHeight = 150.0 - 2 * 15.0
                            
                            fun getY(v: Double): Double {
                                return if (valRange > 0.0) {
                                    15.0 + innerHeight - ((v - minVal) / valRange) * innerHeight
                                } else {
                                    15.0 + innerHeight / 2.0
                                }
                            }
                            
                            thresholdLinesList.add(ThresholdLineContext(
                                label = t("kingdom.analytics.targetLevelMax"),
                                value = upperVal,
                                y = getY(upperVal),
                                textY = getY(upperVal) - 4.0
                            ))
                            thresholdLinesList.add(ThresholdLineContext(
                                label = t("kingdom.analytics.targetLevelMin"),
                                value = lowerVal,
                                y = getY(lowerVal),
                                textY = getY(lowerVal) + 12.0
                            ))
                        }
                    }

                    MetricSeriesContext(
                        key = key,
                        label = t(labelKey),
                        points = pointsCtx,
                        polylinePoints = polylinePoints,
                        min = summary.min,
                        max = summary.max,
                        current = summary.current,
                        mean = summary.mean,
                        deltaFromStart = summary.deltaFromStart,
                        hasThresholds = thresholdLinesList.isNotEmpty(),
                        thresholdLines = if (thresholdLinesList.isNotEmpty()) thresholdLinesList.toTypedArray() else null
                    )
                } else {
                    null
                }
            }.toTypedArray()
        } else {
            emptyArray()
        }

        val analyticsContext = AnalyticsContext(
            series = seriesList,
            windowSize = analyticsWindowSize,
            hasData = hasData,
            isGM = isGM
        )

        KingdomSheetContext(
            partId = parent.partId,
            isFormValid = true,
            kingdomSectionNav = kingdomSectionNav,
            kingdomNameInput = kingdomNameInput.toContext(),
            settlementInput = settlementInput.toContext(),
            xpInput = xpInput.toContext(),
            xpThresholdInput = xpThresholdInput.toContext(),
            levelInput = levelInput.toContext(),
            fameContext = kingdom.fame.toContext(kingdom.settings.maximumFamePoints),
            atWarInput = atWarInput.toContext(),
            unrestInput = unrestInput.toContext(),
            controlDc = controlDc,
            unrestPenalty = unrestPenalty,
            anarchyAt = anarchyAt,
            decadentFeastsShieldActive = kingdom.decadentFeastsShieldActive == true,
            bankedBonusesContext = kingdom.bankedBonusList().map { bonus ->
                BankedBonusContext(
                    value = bonus.value,
                    source = bonus.source,
                    gainedTurn = bonus.gainedTurn,
                    expiry = bonus.expiresTurn?.toString() ?: t("kingdom.bankedAid.noExpiry"),
                    expired = bonus.isExpired(kingdom.currentTurn ?: 0),
                )
            }.toTypedArray(),
            ruinContext = kingdom.ruin.toContext(
                automateStats,
                kingdom.parseRuins(
                    choices = chosenFeatures,
                    baseThreshold = kingdom.settings.ruinThreshold,
                    government = kingdom.government,
                )
            ),
            commoditiesContext = kingdom.commodities.toContext(storage, automateResources, projected),
            worksitesContext = kingdom.workSites.toContext(realm.worksites, automateResources),
            unclaimedWorksites = game.getUnclaimedWorksites(kingdom)
                .map {
                    UnclaimedWorksiteContext(
                        typeLabel = t("kingdom.worksiteName.${it.camp}"),
                        hexLabel = it.hexLabel,
                    )
                }
                .toTypedArray(),
            caravans = (kingdom.caravans ?: emptyArray()).toCaravanRowContexts { caravan ->
                // Same helpers the End-Turn tick and the map overlay use, so the DC shown on the
                // board is the DC the caravan will actually be rolled against.
                runCatching {
                    val path = computeCaravanRoute(
                        KingmakerHexGridProvider(),
                        caravan.originHexKey,
                        caravan.destHexKey,
                    )?.path.orEmpty()
                    if (path.isEmpty()) return@runCatching null
                    val safety = caravanRouteSafety(path.map(::routeHexSafety))
                    val partner = kingdom.groups.find { it.name == caravan.partnerName }
                    caravanRaidDc(
                        baseDc = CARAVAN_BASE_RAID_DC,
                        partnerStanding = partner?.standing,
                        atWar = partner?.atWar == true,
                        claimedFraction = safety.claimedFraction,
                        fullyRoadedThroughClaimed = safety.fullyRoadedThroughClaimed,
                        seasonalDcDelta = game.currentSeasonalModifiers().caravanRaidDcDelta,
                    )
                }.getOrNull()
            },
            shipmentHistory = kingdom.shipmentHistoryList()
                .asReversed()
                .map { entry ->
                    ShipmentHistoryRowContext(
                        turn = entry.turn,
                        partner = entry.partner,
                        cargo = entry.cargo,
                        outcome = entry.outcome.name.lowercase(),
                        outcomeLabel = t("kingdom.shipmentHistory.outcome.${entry.outcome.name.lowercase()}"),
                        rd = entry.rdGained?.let { t("kingdom.shipmentHistory.rd", recordOf("rd" to it)) } ?: "",
                    )
                }
                .toTypedArray(),
            shipmentHistoryDelivered = shipmentOutcomeCounts(kingdom.shipmentHistoryList())[ShipmentOutcome.DELIVERED] ?: 0,
            shipmentHistoryRaided = shipmentOutcomeCounts(kingdom.shipmentHistoryList())[ShipmentOutcome.RAIDED] ?: 0,
            shipmentHistoryRecalled = shipmentOutcomeCounts(kingdom.shipmentHistoryList())[ShipmentOutcome.RECALLED] ?: 0,
            shipments = (kingdom.shipments ?: emptyArray()).toShipmentRowContexts { shipment ->
                // A shipment already stores the path it is walking, so this needs no pathfinding.
                runCatching {
                    shipment.path.takeIf { it.isNotEmpty() }
                        ?.let { shipmentRaidDc(caravanRouteSafety(it.map(::routeHexSafety)), seasonalDcDelta = game.currentSeasonalModifiers().caravanRaidDcDelta) }
                }.getOrNull()
            },
            sizeInput = sizeInput.toContext(),
            size = realm.size,
            kingdomSize = t(realm.sizeInfo.type),
            resourcePointsContext = kingdom.resourcePoints.toContext("resourcePoints", t("kingdom.resourcePoints")),
            resourceDiceContext = kingdom.resourceDice.toContext("resourceDice", t("kingdom.resourceDice"), automate = automateResources, projectedValue = if (automateResources) resourceDiceNum else null),
            consumptionContext = kingdom.consumption.toContext(kingdom.settings.autoCalculateArmyConsumption),
            supernaturalSolutionsInput = supernaturalSolutionsInput.toContext(),
            creativeSolutionsInput = creativeSolutionsInput.toContext(),
            notesContext = notesContext,
            leadersContext = leadersContext,
            charter = kingdom.charter.toContext(enabledCharters),
            heartland = kingdom.heartland.toContext(enabledHeartlands),
            government = kingdom.government.toContext(enabledGovernments, feats),
            abilityBoosts = kingdom.abilityBoosts.toContext("", BASE_ABILITY_BOOSTS + increaseScorePicksBy + vkExtraAbilityBoosts(kingdom.settings)),
            currentNavEntry = currentNavEntry.value,
            hideCreation = currentCharacterSheetNavEntry != "Creation",
            hideBonus = currentCharacterSheetNavEntry != "Bonus",
            settings = kingdom.settings,
            featuresByLevel = kingdom.features.toContext(
                government = government,
                features = allFeatures.toTypedArray(),
                feats = feats,
                increaseBoostsBy = increaseScorePicksBy,
                navigationEntry = currentCharacterSheetNavEntry,
                bonusFeats = kingdom.bonusFeats,
                trainedSkills = trainedSkills,
                chosenFeats = chosenFeats,
                abilityScores = abilityScores,
                skillRanks = kingdomSkillRanks,
            )
                .sortedBy { it.level }
                .toTypedArray(),
            bonusFeat = createBonusFeatContext(
                government = government,
                feats = feats,
                choices = kingdom.features,
                bonusFeats = kingdom.bonusFeats,
                value = bonusFeat,
                trainedSkills = trainedSkills,
                chosenFeats = chosenFeats,
                abilityScores = abilityScores,
                skillRanks = kingdomSkillRanks,
            ),
            bonusFeats = kingdom.bonusFeats.toContext(
                kingdom.getFeats(),
                chosenFeats = chosenFeats,
                abilityScores = abilityScores,
                skillRanks = kingdomSkillRanks,
            ),
            groups = kingdom.groups.toContext(),
            abilityScores = kingdom.abilityScores.toContext(abilityScores, automateStats),
            skillRanks = kingdom.skillRanks.toContext(),
            milestones = kingdom.milestones.toContext(
                kingdom.getMilestones(),
                isGM,
                kingdom.settings.cultOfTheBloomEvents
            ),
            isGM = isGM,
            actor = actor,
            modifiers = kingdom.modifiers.toContext(),
            mainNav = createMainNav(kingdom),
            initialProficiencies = initialProficiencies,
            enableLeadershipModifiers = kingdom.settings.enableLeadershipModifiers,
            settlements = kingdom.settlements.toContext(
                game,
                kingdom.settings.autoCalculateSettlementLevel,
                kingdom.settings.kingdomAllStructureItemBonusesStack,
                kingdom.settings.capitalInvestmentInCapital,
                capStructureBonusAtKingdomLevel = kingdom.settings.capStructureBonusAtKingdomLevel,
                kingdomLevel = kingdom.level,
                capitalCanGrowOneSizeLarger = kingdom.settings.capitalCanGrowOneSizeLarger,
                chosenFeats = chosenFeats,
            ),
            settlementDetailsRows = kingdom.settlements.toSettlementDetailsMatrixRows(
                game,
                kingdom.settings.autoCalculateSettlementLevel,
                kingdom.settings.kingdomAllStructureItemBonusesStack,
                kingdom.settings.capitalInvestmentInCapital,
                capStructureBonusAtKingdomLevel = kingdom.settings.capStructureBonusAtKingdomLevel,
                kingdomLevel = kingdom.level,
                capitalCanGrowOneSizeLarger = kingdom.settings.capitalCanGrowOneSizeLarger,
            ),
            canAddCurrentSceneAsSettlement = canAddCurrentScene,
            turnSectionNav = createTabs<TurnNavEntry>("scroll-to"),
            canLevelUp = kingdom.canLevelUp(),
            vkXp = kingdom.settings.vanceAndKerensharaXP,
            activities = activities,
            cultOfTheBloomEvents = kingdom.settings.cultOfTheBloomEvents,
            ongoingEvents = ongoingEvents,
            eventDC = getEventDC(kingdom),
            cultEventDC = getCultEventDC(kingdom),
            civicPlanning = kingdom.level >= 12,
            heartlandLabel = heartland?.name,
            leadershipActivities = if (globalBonuses.increaseLeadershipActivities)
                game.settings.pfrpg2eKingdomCampingWeather.getLeadershipActivityCapWithTownhall()
            else
                game.settings.pfrpg2eKingdomCampingWeather.getLeadershipActivityCap(),
            collectTaxesReduceUnrestDisabled = kingdom.unrest <= 0,
            consumption = consumption.total,
            automateStats = automateStats,
            resourceDiceIncome = "$resourceDiceNum${realm.sizeInfo.resourceDieSize.value}",
            bonusResourceDiceInput = bonusResourceDiceInput.toContext(),
            skillChecks = checks,
            automateResources = automateResources,
            useLeadershipModifiers = kingdom.settings.enableLeadershipModifiers,
            sheetBackground = background,
            actorUuid = actor.uuid,
            activeLeader = activeLeaderContext,
            enableCouncilMissions = kingdom.settings.enableCouncilMissions,
            councilCooldowns = cooldowns,
            canAudit = canAudit,
            canScrying = canScrying,
            canLockdown = canLockdown,
            canFeast = canFeast,
            auditCooldownTurns = auditCooldownTurns,
            scryingCooldownTurns = scryingCooldownTurns,
            lockdownCooldownTurns = lockdownCooldownTurns,
            feastCooldownTurns = feastCooldownTurns,
            auditAffordable = auditAffordable,
            scryingAffordable = scryingAffordable,
            lockdownAffordable = lockdownAffordable,
            feastAffordable = feastAffordable,
            activeQuests = activeQuests,
            completedQuests = completedQuests,
            rosterContext = (kingdom.companions ?: emptyArray()).map { character ->
                val uuid = character.actorUuid
                if (uuid != null) {
                    val companionActor = fromUuidOfTypes(uuid, PF2ECharacter::class, PF2ENpc::class)
                    if (companionActor != null) {
                        character.name = companionActor.name
                        character.img = companionActor.img
                    }
                }
                character
            }.toTypedArray().toRosterContext(
                isGM = isGM,
                personalQuests = kingdom.companionPersonalQuests ?: emptyArray(),
                expeditions = kingdom.companionExpeditions ?: emptyArray(),
            ) { t(it) },
            expeditionsContext = (kingdom.companionExpeditions ?: emptyArray()).toExpeditionsContext(
                isGM = isGM,
                companions = kingdom.companions ?: emptyArray(),
                chronicle = kingdom.expeditionChronicle ?: emptyArray(),
            ) { t(it) },
            partyInfluenceContext = buildPartyInfluenceContext(
                companions = (kingdom.companions ?: emptyArray()).map {
                    CompanionRef(
                        companionId = it.actorUuid ?: it.name,
                        name = it.name,
                        img = it.img,
                        roleLabel = if (it.role == "npc") "NPC" else "Companion",
                    )
                },
                members = actor.partyMembers().map {
                    PartyMemberRef(uuid = it.uuid, name = it.name, img = it.img)
                },
                stored = kingdom.partyInfluence ?: emptyArray(),
                isGM = isGM,
            ),
            armyPressureContext = buildArmyPressureContext(
                buildArmyPressureView(
                    threats = kingdom.warThreats,
                    deployments = kingdom.armyDeployments,
                    pressure = kingdom.warPressure,
                    settings = kingdom.settings,
                    currentTurn = kingdom.currentTurn ?: 0,
                    settlementNames = settlements.allSettlements.associate { it.id to it.name },
                    isGM = isGM,
                )
            ),
            pacingAlertContext = buildPacingAlertContext(
                buildPacingAlertView(kingdom.pacingAlerts)
            ),
            sessionPrepContext = buildSessionPrepContext(
                forecast = buildForecastPanelContext(buildForecast(game, actor, horizonDays = forecastHorizonDays)),
                view = buildSessionPrepView(
                    quests = kingdom.quests,
                    clocks = kingdom.campaignClocks,
                    events = kingdom.campaignKingdomEvents,
                    hexContents = kingdom.hexContents,
                    companionQuests = kingdom.companionPersonalQuests,
                    isGM = isGM,
                    turnHistory = kingdom.turnHistory,
                    companionExpeditions = kingdom.companionExpeditions,
                    companions = kingdom.companions,
                    warThreats = kingdom.warThreats,
                )
            ),
            showDetailedMatrix = showDetailedMatrix,
            campaignClocks = kingdom.campaignClocks.toDashboardContext(isGM),
            generatedQuestCount = generatedQuestCount,
            activeEventCount = activeEventCount,
            questTimerChanges = emptyArray(),
            personalQuests = buildCompanionQuestRows(
                quests = kingdom.companionPersonalQuests ?: emptyArray(),
                companions = kingdom.companions ?: emptyArray(),
                isGM = isGM,
            ) { t(it) },
            analyticsContext = analyticsContext,
        )
    }

    private fun getCultEventDC(kingdom: KingdomData): Int =
        max(1, kingdom.settings.cultEventDc - kingdom.turnsWithoutCultEvent * kingdom.settings.cultEventDcStep)

    private fun getEventDC(kingdom: KingdomData): Int =
        max(1, kingdom.settings.eventDc - kingdom.turnsWithoutEvent * kingdom.settings.eventDcStep)

    private fun createMainNav(kingdom: KingdomData): Array<NavEntryContext> {
        val tradeAgreements = kingdom.groups.count { it.relations == Relations.TRADE_AGREEMENT.value }
        val isGM = game.user.isGM
        return MainNavEntry.entries
            .map {
                val postfix = when (it) {
                    MainNavEntry.TRADE_AGREEMENTS -> " ($tradeAgreements)"
                    MainNavEntry.SETTLEMENTS -> {
                        val size = kingdom.settlements.mapNotNull { game.scenes.get(it.sceneId) }.size
                        " ($size)"
                    }

                    MainNavEntry.MODIFIERS -> " (${kingdom.modifiers.size})"
                    MainNavEntry.QUESTS -> {
                        val activeSize = (kingdom.quests ?: emptyArray()).count { it.status == "active" }
                        " ($activeSize)"
                    }
                    else -> ""
                }
                NavEntryContext(
                    label = "${t(it)}$postfix",
                    active = currentNavEntry == it,
                    link = it.value,
                    title = t(it),
                    action = "change-nav",
                )
            }
            .toTypedArray()
    }

    private fun createKingdomSectionNav(): Array<NavEntryContext> {
        return (1..20).map { it.toString() }
            .map {
                NavEntryContext(
                    label = it,
                    active = currentCharacterSheetNavEntry == it,
                    link = it,
                    title = "Level: $it",
                    action = "change-kingdom-section-nav",
                )
            }
            .toTypedArray()
    }

    override fun _attachPartListeners(partId: String, htmlElement: HTMLElement, options: ApplicationRenderOptions) {
        super._attachPartListeners(partId, htmlElement, options)
        htmlElement.querySelectorAll(".km-gain-lose").asList()
            .filterIsInstance<HTMLElement>()
            .forEach { elem ->
                elem.addEventListener("click", {
                    actor.getKingdom()?.let { kingdom ->
                        buildPromise {
                            val activityId = elem.closest(".km-kingdom-activity")
                                ?.takeIfInstance<HTMLElement>()
                                ?.dataset["activityId"]
                            executeResourceButton(
                                game = game,
                                actor = actor,
                                kingdom = kingdom,
                                elem = elem,
                                activityId = activityId,
                            )
                        }
                    }
                })
            }
        // need to manually keep track of opened details because fucking foundry
        // re-renders the entire dom when you change anything, thereby closing all details
        htmlElement.querySelectorAll(".km-kingdom-details > details > summary").asList()
            .filterIsInstance<HTMLElement>()
            .forEach { elem ->
                elem.addEventListener("click", {
                    if (it.target.unsafeCast<HTMLElement>().classList.contains("km-detail-label")) {
                        it.preventDefault()
                        it.stopPropagation()
                        val id = (it.currentTarget as HTMLElement).dataset["id"] ?: ""
                        if (id in openedDetails) {
                            openedDetails.remove(id)
                        } else {
                            openedDetails.add(id)
                        }
                        render()
                    }
                })
            }
        attachQuestFilter(htmlElement)
        attachThreatFilter(htmlElement)
    }

    // Wires the quick-filter bar above the active quests grid. The inputs carry no
    // `name`, and we stopPropagation on their events, so they never feed the form's
    // submitOnChange (which would re-render and steal focus). Filter state is mirrored
    // into instance fields so it can be restored after an unrelated re-render.
    /**
     * Status filter and sort for the war-threats table.
     *
     * Mirrors attachQuestFilter: listeners stopPropagation so the sheet's submitOnChange does not
     * re-render and steal focus, and the selection is mirrored into instance fields so it survives
     * an unrelated re-render.
     *
     * "Arrived" is filtered on the row's derived data-arrived, not on data-status: WarThreatStatus
     * has no such member, so a chip matching status="arrived" would match nothing forever.
     */
    private fun attachThreatFilter(htmlElement: HTMLElement) {
        val bar = htmlElement.querySelector(".km-threat-filters")?.takeIfInstance<HTMLElement>() ?: return
        val statusSelect = bar.querySelector(".km-threat-filter-status")
        val sortSelect = bar.querySelector(".km-threat-filter-sort")
        val clearBtn = bar.querySelector(".km-threat-filter-clear")
        val countEl = bar.querySelector(".km-threat-filter-count")?.takeIfInstance<HTMLElement>()
        val noMatches = htmlElement.querySelector(".km-threat-no-matches")?.takeIfInstance<HTMLElement>()
        val rows = htmlElement.querySelectorAll(".km-threat-row").asList().filterIsInstance<HTMLElement>()
        val body = rows.firstOrNull()?.parentElement

        fun strVal(e: Element?): String = (e?.asDynamic()?.value as? String)?.trim() ?: ""
        fun setVal(e: Element?, v: String) { e?.asDynamic()?.value = v }

        val apply = {
            val status = strVal(statusSelect).ifEmpty { "all" }
            val sort = strVal(sortSelect).ifEmpty { "eta" }
            var shown = 0
            rows.forEach { row ->
                val show = when (status) {
                    "all" -> true
                    "arrived" -> row.dataset["arrived"] == "1"
                    else -> row.dataset["status"] == status
                }
                if (show) {
                    row.classList.remove("km-threat-filtered-out")
                    shown++
                } else {
                    row.classList.add("km-threat-filtered-out")
                }
            }
            // Re-append in sorted order. ETA ascending (soonest first, unset last via its 9999
            // sentinel, because Handlebars treats a real 0 as falsy); escalation descending.
            body?.let { tbody ->
                rows.sortedWith(
                    if (sort == "escalation") {
                        compareByDescending { it.dataset["escalation"]?.toIntOrNull() ?: 0 }
                    } else {
                        compareBy { it.dataset["eta"]?.toIntOrNull() ?: 9999 }
                    }
                ).forEach { tbody.appendChild(it) }
            }
            threatFilterStatus = status
            threatSortMode = sort
            countEl?.textContent = "$shown / ${rows.size}"
            noMatches?.hidden = shown != 0 || rows.isEmpty()
        }

        setVal(statusSelect, threatFilterStatus)
        setVal(sortSelect, threatSortMode)

        listOfNotNull(statusSelect, sortSelect).forEach { c ->
            c.addEventListener("change", { it.stopPropagation(); apply() })
        }
        clearBtn?.addEventListener("click", {
            it.preventDefault()
            it.stopPropagation()
            setVal(statusSelect, "all")
            setVal(sortSelect, "eta")
            apply()
        })
        apply()
    }

    private fun attachQuestFilter(htmlElement: HTMLElement) {
        val bar = htmlElement.querySelector(".km-quest-filters")?.takeIfInstance<HTMLElement>() ?: return
        val titleInput = bar.querySelector(".km-quest-filter-title")
        val minInput = bar.querySelector(".km-quest-filter-level-min")
        val maxInput = bar.querySelector(".km-quest-filter-level-max")
        val hiddenSelect = bar.querySelector(".km-quest-filter-hidden")
        val categorySelect = bar.querySelector(".km-quest-filter-category")
        val clearBtn = bar.querySelector(".km-quest-filter-clear")
        val countEl = bar.querySelector(".km-quest-filter-count")?.takeIfInstance<HTMLElement>()
        val noMatches = htmlElement.querySelector(".km-quest-no-matches")?.takeIfInstance<HTMLElement>()
        val cards = htmlElement.querySelectorAll(".km-quests-grid .km-quest-card").asList()
            .filterIsInstance<HTMLElement>()
        // Scoped to .km-completed-section on purpose: .km-quests-list is also used by the personal
        // quests block, and an unscoped selector would silently pull those rows into the pager.
        val rows = htmlElement.querySelectorAll(".km-completed-section .km-quest-row").asList()
            .filterIsInstance<HTMLElement>()
        val completedCountEl = htmlElement.querySelector(".km-completed-quest-count")?.takeIfInstance<HTMLElement>()
        val completedNoMatches = htmlElement.querySelector(".km-completed-no-matches")?.takeIfInstance<HTMLElement>()
        val expandBtn = htmlElement.querySelector(".km-completed-expand-btn")?.takeIfInstance<HTMLElement>()

        fun strVal(e: Element?): String = (e?.asDynamic()?.value as? String)?.trim() ?: ""
        fun setVal(e: Element?, v: String) { e?.asDynamic()?.value = v }

        val applyFilter = {
            val q = strVal(titleInput).lowercase()
            val min = strVal(minInput).toIntOrNull()
            val max = strVal(maxInput).toIntOrNull()
            val hiddenMode = strVal(hiddenSelect).ifEmpty { "all" }
            val categoryMode = strVal(categorySelect).ifEmpty { "all" }
            var shown = 0
            cards.forEach { card ->
                val title = (card.dataset["title"] ?: "").lowercase()
                val level = card.dataset["level"]?.toIntOrNull() ?: 0
                val isHidden = card.dataset["hidden"] == "1"
                val category = card.dataset["category"] ?: ""
                val show = (q.isEmpty() || title.contains(q)) &&
                    (min == null || level >= min) &&
                    (max == null || level <= max) &&
                    (categoryMode == "all" || category == categoryMode) &&
                    when (hiddenMode) {
                        "hidden" -> isHidden
                        "visible" -> !isHidden
                        else -> true
                    }
                if (show) {
                    card.classList.remove("km-quest-filtered-out")
                    shown++
                } else {
                    card.classList.add("km-quest-filtered-out")
                }
            }
            // Completed/failed archive, driven by the SAME bar values computed above -- one filter
            // state, not two. The decision itself is delegated to the pure, unit-tested core, and
            // rows are only class-toggled, never removed, so every row keeps its live data-id
            // reopen button (regression guard: aa1d3322).
            val entries = rows.mapNotNull { row ->
                val rowId = row.dataset["id"] ?: return@mapNotNull null
                val rowHidden = row.dataset["hidden"] == "1"
                // Visibility is applied OUTSIDE the core: CompletedQuestFilter has no visibility
                // member and adding one would change an already-green tested contract.
                val visible = when (hiddenMode) {
                    "hidden" -> rowHidden
                    "visible" -> !rowHidden
                    else -> true
                }
                if (!visible) null else CompletedQuestEntry(
                    id = rowId,
                    title = row.dataset["title"] ?: "",
                    level = row.dataset["level"]?.toIntOrNull(),
                    category = row.dataset["category"],
                )
            }
            val page = pageCompletedQuests(
                filterCompletedQuests(
                    entries,
                    CompletedQuestFilter(
                        titleQuery = q,
                        minLevel = min,
                        maxLevel = max,
                        category = categoryMode.takeIf { it != "all" },
                    ),
                ),
                limit = COMPLETED_QUESTS_DEFAULT_LIMIT,
                expanded = completedQuestsExpanded,
            )
            val shownIds = page.shown.mapTo(mutableSetOf()) { it.id }
            rows.forEach { row ->
                if ((row.dataset["id"] ?: "") in shownIds) {
                    row.classList.remove("km-quest-filtered-out")
                } else {
                    row.classList.add("km-quest-filtered-out")
                }
            }
            completedCountEl?.textContent = "${page.shown.size} / ${rows.size}"
            completedNoMatches?.hidden = page.total != 0 || rows.isEmpty()
            expandBtn?.hidden = page.hiddenCount == 0 && !completedQuestsExpanded
            expandBtn?.textContent = if (completedQuestsExpanded) {
                t("kingdom.quests.filter.showFewer")
            } else {
                t("kingdom.quests.filter.showAll", recordOf("count" to page.hiddenCount))
            }

            questFilterTitle = strVal(titleInput)
            questFilterMin = strVal(minInput)
            questFilterMax = strVal(maxInput)
            questFilterHidden = hiddenMode
            questFilterCategory = categoryMode
            countEl?.textContent = "$shown / ${cards.size}"
            noMatches?.hidden = shown != 0 || cards.isEmpty()
        }

        // restore any filter that was active before the last re-render
        setVal(titleInput, questFilterTitle)
        setVal(minInput, questFilterMin)
        setVal(maxInput, questFilterMax)
        setVal(hiddenSelect, questFilterHidden)
        setVal(categorySelect, questFilterCategory)

        listOfNotNull(titleInput, minInput, maxInput, hiddenSelect, categorySelect).forEach { c ->
            c.addEventListener("change", { it.stopPropagation(); applyFilter() })
        }
        listOfNotNull(titleInput, minInput, maxInput).forEach { c ->
            c.addEventListener("input", { it.stopPropagation(); applyFilter() })
        }
        clearBtn?.addEventListener("click", {
            it.preventDefault()
            it.stopPropagation()
            setVal(titleInput, "")
            setVal(minInput, "")
            setVal(maxInput, "")
            setVal(hiddenSelect, "all")
            setVal(categorySelect, "all")
            // Deliberately does NOT reset completedQuestsExpanded: "clear filters" is about the
            // filter bar, not about how much of the archive the GM chose to unfold.
            applyFilter()
        })
        expandBtn?.addEventListener("click", {
            it.preventDefault()
            it.stopPropagation()
            completedQuestsExpanded = !completedQuestsExpanded
            applyFilter()
        })
        applyFilter()
    }

    override fun onParsedSubmit(value: KingdomSheetData): Promise<Void> = buildPromise {
        if (isFormValid) {
            if (!game.user.isGM) {
                game.settings.pfrpg2eKingdomCampingWeather.setKingdomActiveLeader(value.activeLeader)
                render()
                return@buildPromise null
            }
            val previousKingdom = getKingdom()
            val kingdom = deepClone(previousKingdom)
            kingdom.name = value.name
            kingdom.atWar = value.atWar
            kingdom.fame = value.fame
            kingdom.level = value.level
            kingdom.xp = value.xp
            kingdom.xpThreshold = value.xpThreshold
            kingdom.unrest = value.unrest
            kingdom.ruin = value.ruin
            kingdom.commodities = value.commodities
            kingdom.workSites = value.workSites
            kingdom.size = value.size
            kingdom.resourcePoints = value.resourcePoints
            kingdom.resourceDice = value.resourceDice
            kingdom.bonusResourceDice = value.bonusResourceDice
            kingdom.activeSettlement = value.activeSettlement
            kingdom.supernaturalSolutions = value.supernaturalSolutions
            kingdom.creativeSolutions = value.creativeSolutions
            kingdom.consumption = if (kingdom.settings.autoCalculateArmyConsumption) {
                RawConsumption.copy(value.consumption, armies = kingdom.consumption.armies)
            } else {
                value.consumption
            }
            kingdom.charter = value.charter
            kingdom.heartland = value.heartland
            kingdom.government = value.government
            kingdom.abilityBoosts = value.abilityBoosts
            kingdom.features = value.features
            kingdom.bonusFeats = value.bonusFeats
            kingdom.leaders = value.leaders
            // The submitted groups carry only the six fields the sheet renders as inputs (see
            // KingdomSheetDataModel's array("groups") schema). standing, standingLog and
            // allianceLevel are display-only, so assigning value.groups wholesale erased a
            // faction's entire attitude history on every sheet save. Carry those three across by
            // position -- the form renders groups in kingdom order, and add/delete go through their
            // own data-action handlers, so the indices line up.
            // Declaring war on a trade partner is a sheet edit, so this is where a shipment already
            // on the road to them learns about it. Blocking new dispatches was only half the embargo.
            val partnersBefore = kingdom.groups.associate { it.name to it.atWar }
            kingdom.groups = mergeSubmittedGroups(value.groups, kingdom.groups)
            val newlyAtWar = partnersNewlyAtWar(
                before = partnersBefore,
                after = kingdom.groups.associate { it.name to it.atWar },
            ).toSet()
            kingdom.skillRanks = value.skillRanks
            kingdom.abilityScores = value.abilityScores
            kingdom.milestones = value.milestones
            kingdom.notes = value.notes
            kingdom.initialProficiencies = value.initialProficiencies

            val automateResources = kingdom.settings.automateResources != AutomateResources.MANUAL.value
            if (automateResources) {
                val realm = game.getRealmData(actor, kingdom)
                val settlements = kingdom.getAllSettlements(game)
                val allFeatures = kingdom.getExplodedFeatures()
                val chosenFeatures = kingdom.getChosenFeatures(allFeatures)
                val chosenFeats = kingdom.getChosenFeats(chosenFeatures)
                val expressionContext = kingdom.createSimpleContext(settlements)
                val modifiers = kingdom.createModifiers(settlements)

                val projected = calculateProjectedResources(
                    seasonal = game.currentSeasonalModifiers(),
                    kingdomData = kingdom,
                    realmData = realm,
                    chosenFeats = chosenFeats,
                    settlements = settlements.allSettlements,
                    expressionContext = expressionContext,
                    modifiers = modifiers,
                )
                kingdom.commodities.next.ore = projected.income.ore
                kingdom.commodities.next.stone = projected.income.stone
                kingdom.commodities.next.lumber = projected.income.lumber
                kingdom.commodities.next.luxuries = projected.income.luxuries
                kingdom.commodities.next.food = 0
                kingdom.resourceDice.next = projected.income.resourceDice
            }

            beforeKingdomUpdate(previousKingdom, kingdom)
            actor.setKingdom(kingdom)
            // Posted after the persist so the offer cards refer to state that is already saved.
            postCaravanWarOffers(game, actor, kingdom, newlyAtWar)
            // custom handling for values, that don't persist data on the document and therefore don't
            // trigger a rerender by default
            val needsReRender = bonusFeat != value.bonusFeat ||
                    game.settings.pfrpg2eKingdomCampingWeather.getKingdomActiveLeader() != value.activeLeader
            bonusFeat = value.bonusFeat
            game.settings.pfrpg2eKingdomCampingWeather.setKingdomActiveLeader(value.activeLeader)
            if (needsReRender) {
                render()
            }
        }
        null
    }
}

suspend fun openOrCreateKingdomSheet(game: Game, dispatcher: ActionDispatcher, actor: KingdomActor) {
    if (actor.getKingdom() == null) {
        actor.setKingdom(createKingdomDefaults(t("kingdom.newKingdom")))
        openJournal("Compendium.pf2e-kingmaker-tools.kingmaker-tools-journals.JournalEntry.FwcyYZARAnOHlKkE")
        actor.update(recordOf("ownership" to actor.ownershipOwnersOnly())).await()
    }
    KingdomSheet(game, actor, dispatcher).launch()
}
