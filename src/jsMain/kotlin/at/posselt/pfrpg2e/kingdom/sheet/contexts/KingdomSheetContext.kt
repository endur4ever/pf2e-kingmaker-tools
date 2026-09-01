package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.FormElementContext
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.KingdomSettings
import at.posselt.pfrpg2e.kingdom.RawCouncilCooldowns
import at.posselt.pfrpg2e.kingdom.data.RawQuest
import at.posselt.pfrpg2e.kingdom.sheet.contexts.RosterContext
import kotlinx.js.JsPlainObject

@Suppress("unused")
@JsPlainObject
external interface KingdomSheetContext : ValidatedHandlebarsContext {
    val activeQuests: Array<RawQuest>
    val completedQuests: Array<RawQuest>
    val kingdomNameInput: FormElementContext
    val settlementInput: FormElementContext
    val xpInput: FormElementContext
    val xpThresholdInput: FormElementContext
    val levelInput: FormElementContext
    val fameContext: FameContext
    val atWarInput: FormElementContext
    val sizeInput: FormElementContext
    val size: Int
    val kingdomSize: String
    val unrestInput: FormElementContext
    val commoditiesContext: CommoditiesContext
    val resourcePointsContext: ResourceContext
    val resourceDiceContext: ResourceContext
    val worksitesContext: Array<WorkSiteContext>
    val unclaimedWorksites: Array<UnclaimedWorksiteContext>
    val caravans: Array<CaravanRowContext>

    /** Resolved caravan/shipment outcomes, NEWEST first for display. */
    val shipmentHistory: Array<ShipmentHistoryRowContext>

    /** Per-outcome tallies shown as badges on the history header. */
    val shipmentHistoryDelivered: Int
    val shipmentHistoryRaided: Int
    val shipmentHistoryRecalled: Int
    val shipments: Array<ShipmentRowContext>
    val ruinContext: Array<RuinContext>

    /** Banked Request Foreign Aid bonuses, shown in the Turn tab with their expiry. */
    val bankedBonusesContext: Array<BankedBonusContext>

    /** Whether Decadent Feasts' unrest shield is currently armed. */
    val decadentFeastsShieldActive: Boolean
    val controlDc: Int
    val unrestPenalty: Int
    val anarchyAt: Int
    val consumptionContext: ConsumptionContext
    val supernaturalSolutionsInput: FormElementContext
    val creativeSolutionsInput: FormElementContext
    val notesContext: NotesContext
    val leadersContext: Array<LeaderValuesContext>
    val charter: CharterContext
    val heartland: HeartlandContext
    val government: GovernmentContext
    val abilityBoosts: AbilityBoostContext
    val featuresByLevel: Array<FeatureByLevelContext>
    val mainNav: Array<NavEntryContext>
    val kingdomSectionNav: Array<NavEntryContext>
    val hideCreation: Boolean
    val hideBonus: Boolean
    val currentNavEntry: String
    val settings: KingdomSettings
    val bonusFeat: AddBonusFeatContext
    val bonusFeats: Array<BonusFeatContext>
    val groups: Array<GroupContext>
    val rivalRealms: RivalRealmsContext
    val skillRanks: SkillRanksContext
    val abilityScores: Array<AbilityScoreContext>
    val milestones: Array<MilestoneContext>
    val isGM: Boolean
    val actor: KingdomActor
    val modifiers: Array<ModifierContext>
    val initialProficiencies: Array<FormElementContext>
    val enableLeadershipModifiers: Boolean
    val settlements: Array<SettlementsContext>
    val settlementDetailsRows: Array<SettlementDetailsMatrixRowContext>
    val canAddCurrentSceneAsSettlement: Boolean
    val turnSectionNav: Array<NavEntryContext>
    val vkXp: Boolean
    val activities: ActivitiesContext
    val canLevelUp: Boolean
    val ongoingEvents: Array<OngoingEventContext>
    val eventDC: Int
    val cultEventDC: Int
    val cultOfTheBloomEvents: Boolean
    val civicPlanning: Boolean
    val heartlandLabel: String?
    val leadershipActivities: Int
    val collectTaxesReduceUnrestDisabled: Boolean
    val consumption: Int
    val automateStats: Boolean
    val resourceDiceIncome: String
    val bonusResourceDiceInput: FormElementContext
    val skillChecks: Array<SkillChecksContext>
    val automateResources: Boolean
    val useLeadershipModifiers: Boolean
    val actorUuid: String
    val activeLeader: FormElementContext
    val sheetBackground: String?
    val enableCouncilMissions: Boolean
    val councilCooldowns: RawCouncilCooldowns
    val canAudit: Boolean
    val canScrying: Boolean
    val canLockdown: Boolean
    val canFeast: Boolean
    val auditCooldownTurns: Int
    val scryingCooldownTurns: Int
    val lockdownCooldownTurns: Int
    val feastCooldownTurns: Int
    val auditAffordable: Boolean
    val scryingAffordable: Boolean
    val lockdownAffordable: Boolean
    val feastAffordable: Boolean
    val rosterContext: RosterContext
    val partyInfluenceContext: PartyInfluenceContext
    val armyPressureContext: ArmyPressureContext
    val pacingAlertContext: PacingAlertContext
    val sessionPrepContext: SessionPrepContext
    /** Completed milestones, newest first; read-only and player-visible. */
    val chronicle: Array<DeedChronicleRowContext>
    /** Party XP ledger (Party tab); GM-editable, player-visible history. */
    val xpLedger: XpLedgerContext
    val petitions: PetitionInboxContext
    val councilVotesContext: CouncilVotesContext
    val renownCardContext: RenownCardContext
    val personalHoldingsContext: PersonalHoldingsSectionContext

    /** Unread feed bell for the CURRENT user; null only when the kingdom itself is missing. */
    val pingsContext: PingsPanelContext?

    /** Deadlines rows for the Campaign tab. GM-only by DATA -- null for players. */
    val deadlines: Array<DeadlineRowContext>?

    /** GM-only treasure ledger for the Session Prep tab; null for players. */
    val treasureLedger: TreasureLedgerContext?
    val showDetailedMatrix: Boolean
    val campaignClocks: CampaignClockContext
    val generatedQuestCount: Int
    val activeEventCount: Int
    val questTimerChanges: Array<QuestTimerChangeContext>
    val personalQuests: Array<CompanionQuestRowContext>
    val analyticsContext: AnalyticsContext?
    val expeditionsContext: ExpeditionsContext
}

@JsPlainObject
external interface QuestTimerChangeContext {
    val questName: String
    val changeLabel: String
}

/** One banked circumstance bonus, for the Turn tab list. */
@JsPlainObject
external interface BankedBonusContext {
    val value: Int
    val source: String
    val gainedTurn: Int
    /** Already-localized expiry text: a turn number, or "never". */
    val expiry: String
    /** True once the bonus has lapsed — shown struck through rather than hidden. */
    val expired: Boolean
}
