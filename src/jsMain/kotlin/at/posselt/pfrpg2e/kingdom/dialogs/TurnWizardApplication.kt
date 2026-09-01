package at.posselt.pfrpg2e.kingdom.dialogs

import kotlinx.coroutines.sync.withLock
import com.foundryvtt.pf2e.actor.PF2ECharacter
import at.posselt.pfrpg2e.kingdom.factionAgendaMoveSpecs
import at.posselt.pfrpg2e.kingdom.factionAgendaArchetypeSpecs
import at.posselt.pfrpg2e.kingdom.localizeAgendaMoveLine
import at.posselt.pfrpg2e.kingdom.postHoldingDamageOffer
import at.posselt.pfrpg2e.kingdom.holdingsAt
import at.posselt.pfrpg2e.kingdom.data.RawPersonalHolding
import at.posselt.pfrpg2e.data.kingdom.DamageSeverity
import at.posselt.pfrpg2e.kingdom.postHoldingIncomeOffer
import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import at.posselt.pfrpg2e.kingdom.councilVoteMutex
import at.posselt.pfrpg2e.kingdom.mapdynamism.clearedUnclaimedHexes
import at.posselt.pfrpg2e.kingdom.mapdynamism.reconcileRewild
import at.posselt.pfrpg2e.kingdom.stampEpithetOffersMade
import at.posselt.pfrpg2e.kingdom.data.toTurnTallies
import at.posselt.pfrpg2e.data.kingdom.spotlightOfTheTurn
import at.posselt.pfrpg2e.kingdom.localizeSpotlight
import at.posselt.pfrpg2e.kingdom.rulerActorUuid
import at.posselt.pfrpg2e.kingdom.postEpithetOffers
import at.posselt.pfrpg2e.kingdom.pendingEpithetOffers
import at.posselt.pfrpg2e.Config
import at.posselt.pfrpg2e.app.forms.SimpleApp
import com.foundryvtt.core.abstract.DatabaseUpdateOperation
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.clearPerformedActivities
import at.posselt.pfrpg2e.kingdom.restoreTurnWizardState
import com.foundryvtt.pf2e.item.PF2EItem
import at.posselt.pfrpg2e.kingdom.rivalGrowthProfilesById
import at.posselt.pfrpg2e.kingdom.collectRivalOffers
import at.posselt.pfrpg2e.kingdom.data.withWarOfferRecorded
import at.posselt.pfrpg2e.kingdom.evaluateNpcMemoriesForTurn
import at.posselt.pfrpg2e.kingdom.expirePetitionsForTurn
import at.posselt.pfrpg2e.kingdom.generatePetitionsForTurn
import at.posselt.pfrpg2e.kingdom.postNewPetitionNotice
import at.posselt.pfrpg2e.kingdom.postPetitionExpiryOffers
import at.posselt.pfrpg2e.kingdom.postSettlementLifeDigest
import at.posselt.pfrpg2e.kingdom.rollSettlementLifeEvents
import at.posselt.pfrpg2e.data.regions.getSeasonForMonth
import at.posselt.pfrpg2e.utils.getCurrentMonth
import at.posselt.pfrpg2e.kingdom.npcMemoryRules
import at.posselt.pfrpg2e.kingdom.postNpcAttitudeShiftOffers
import at.posselt.pfrpg2e.kingdom.xp.postXpLedgerDigest
import at.posselt.pfrpg2e.kingdom.postFactionMoveDigest
import at.posselt.pfrpg2e.kingdom.postRivalOfferDigests
import at.posselt.pfrpg2e.kingdom.localizeRivalHeadline
import at.posselt.pfrpg2e.kingdom.getPerformedActivities
import at.posselt.pfrpg2e.kingdom.digest.postEndTurnDigest
import at.posselt.pfrpg2e.kingdom.mapdynamism.postMapDynamismOffers
import at.posselt.pfrpg2e.kingdom.pings.TurnReadiness
import at.posselt.pfrpg2e.kingdom.pings.pingsReadyForTurn
import at.posselt.pfrpg2e.kingdom.pings.readinessStrip
import at.posselt.pfrpg2e.kingdom.sheet.contexts.ReadinessStripContext
import at.posselt.pfrpg2e.kingdom.getActivity
import at.posselt.pfrpg2e.data.armies.BattleStatus
import at.posselt.pfrpg2e.kingdom.formatTurnGazette
import at.posselt.pfrpg2e.kingdom.getExplodedFeatures
import at.posselt.pfrpg2e.kingdom.data.getChosenFeatures
import at.posselt.pfrpg2e.kingdom.data.getChosenFeats
import at.posselt.pfrpg2e.kingdom.createSimpleContext
import at.posselt.pfrpg2e.kingdom.createModifiers
import at.posselt.pfrpg2e.kingdom.TurnTickingEngine
import at.posselt.pfrpg2e.kingdom.TickChange
import at.posselt.pfrpg2e.kingdom.TickResult
import at.posselt.pfrpg2e.kingdom.DEFAULT_QUEST_EXTEND_TURNS
import at.posselt.pfrpg2e.kingdom.ActivityCapCalculator
import at.posselt.pfrpg2e.kingdom.countQuestsFailingThisTurn
import at.posselt.pfrpg2e.kingdom.countWarThreatsAtMaxEscalation
import at.posselt.pfrpg2e.kingdom.countExpeditionsAwaitingResolution
import at.posselt.pfrpg2e.kingdom.countInjuredCompanions
import at.posselt.pfrpg2e.kingdom.CARAVAN_BASE_RAID_DC
import at.posselt.pfrpg2e.kingdom.CaravanEventKind
import at.posselt.pfrpg2e.kingdom.CaravanEvent
import at.posselt.pfrpg2e.kingdom.CaravanTickInput
import at.posselt.pfrpg2e.kingdom.caravanRaidDc
import at.posselt.pfrpg2e.kingdom.caravanBonusRdCap
import at.posselt.pfrpg2e.kingdom.caravanRdPerCommodity
import at.posselt.pfrpg2e.kingdom.appendShipment
import at.posselt.pfrpg2e.kingdom.caravanEventToHistory
import at.posselt.pfrpg2e.kingdom.tickCaravans
import at.posselt.pfrpg2e.kingdom.tickShipments
import at.posselt.pfrpg2e.kingdom.ShipmentTickInput
import at.posselt.pfrpg2e.kingdom.sheet.ProjectedResources
import at.posselt.pfrpg2e.kingdom.sheet.calculateProjectedResources
import at.posselt.pfrpg2e.kingdom.sheet.upkeepGainFame
import at.posselt.pfrpg2e.kingdom.sheet.upkeepAdjustUnrest
import at.posselt.pfrpg2e.kingdom.sheet.upkeepCollectResources
import at.posselt.pfrpg2e.kingdom.sheet.upkeepPayConsumption
import at.posselt.pfrpg2e.kingdom.data.RawCaravanShipment
import at.posselt.pfrpg2e.kingdom.data.RawExpeditionChronicleEntry
import at.posselt.pfrpg2e.kingdom.data.RawGroup
import at.posselt.pfrpg2e.kingdom.computeCaravanRoute
import at.posselt.pfrpg2e.kingdom.currentSeasonalModifiers
import at.posselt.pfrpg2e.kingdom.postWarThreatArrivalOffer
import at.posselt.pfrpg2e.kingdom.map.routeHexSafety
import at.posselt.pfrpg2e.kingdom.CaravanRouteSafety
import at.posselt.pfrpg2e.kingdom.caravanRouteSafety
import at.posselt.pfrpg2e.kingdom.shipmentRaidDc
import at.posselt.pfrpg2e.kingdom.map.KingmakerHexGridProvider
import com.foundryvtt.kingmaker.kingmaker
import at.posselt.pfrpg2e.utils.postChatMessage
import js.array.component1
import js.array.component2
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.kingdom.getRealmData
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.kingdom.getAllSettlements
import at.posselt.pfrpg2e.kingdom.parseRuins
import at.posselt.pfrpg2e.kingdom.trackUnrestStagnation
import at.posselt.pfrpg2e.kingdom.trackLevelMismatch
import at.posselt.pfrpg2e.kingdom.trackLootImbalance
import at.posselt.pfrpg2e.kingdom.trackRealizedLootImbalance
import at.posselt.pfrpg2e.kingdom.loot.pacingLootInput
import at.posselt.pfrpg2e.kingdom.data.toModel
import at.posselt.pfrpg2e.kingdom.pacingMaxTurnGap
import at.posselt.pfrpg2e.kingdom.pacingMinUnrestDelta
import at.posselt.pfrpg2e.kingdom.pacingLevelMismatchRange
import at.posselt.pfrpg2e.kingdom.pacingLootImbalanceEnabled
import at.posselt.pfrpg2e.kingdom.pacingLootImbalanceRange
import at.posselt.pfrpg2e.kingdom.pacingChapterTargetLevel
import at.posselt.pfrpg2e.kingdom.postPacingAlertChat
import at.posselt.pfrpg2e.kingdom.data.ChosenFeature
import at.posselt.pfrpg2e.kingdom.data.RawPacingAlert
import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord
import at.posselt.pfrpg2e.kingdom.appendTurnRecord
import at.posselt.pfrpg2e.kingdom.sheet.calculateXpChange
import at.posselt.pfrpg2e.kingdom.countArmyVictories
import at.posselt.pfrpg2e.kingdom.detectFiredDeeds
import at.posselt.pfrpg2e.kingdom.postDeedsDigest
import at.posselt.pfrpg2e.kingdom.computeLastTurnRecap
import at.posselt.pfrpg2e.kingdom.buildTurnRecord
import at.posselt.pfrpg2e.data.kingdom.structures.CommodityStorage
import at.posselt.pfrpg2e.data.kingdom.attitudeFor
import at.posselt.pfrpg2e.data.kingdom.shouldOfferDiplomacyQuest
import at.posselt.pfrpg2e.data.kingdom.shouldOfferWarThreat
import at.posselt.pfrpg2e.actor.partyMembers
import at.posselt.pfrpg2e.kingdom.resources.calculateStorage
import at.posselt.pfrpg2e.kingdom.sheet.contexts.TurnWizardContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.ChecklistItemContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.KingdomStateContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.ActivityCapContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.TickChangeContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.RuinContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.toContext
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.getAppFlag
import at.posselt.pfrpg2e.utils.setAppFlag
import at.posselt.pfrpg2e.utils.unsetAppFlag
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.kingdom.data.EndTurnSnapshot
import com.foundryvtt.core.utils.deepClone
import com.foundryvtt.core.Game
import com.foundryvtt.core.game
import com.foundryvtt.core.ui
import com.foundryvtt.core.applications.api.ApplicationRenderOptions
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.get
import js.objects.Object
import js.objects.Record
import js.objects.recordOf
import kotlin.js.Promise
import kotlinx.coroutines.await
import at.posselt.pfrpg2e.utils.asSequence
import at.posselt.pfrpg2e.kingdom.logToCalendar
import at.posselt.pfrpg2e.kingdom.PULL_TOGETHER_BASE_DC
import at.posselt.pfrpg2e.kingdom.pullTogetherDcAfterTurn
import at.posselt.pfrpg2e.kingdom.LIQUIDATE_RESOURCES_NEXT_TURN_RD_PENALTY
import at.posselt.pfrpg2e.utils.d20Check
import at.posselt.pfrpg2e.kingdom.postIrrigationPlagueOffer
import at.posselt.pfrpg2e.kingdom.irrigationPlagueFlatCheckDc

fun TickChange.toDisplayString(): String {
    return when {
        category == "resourcePoints" && field == "tribute" -> {
            val params = js("{}")
            params["amount"] = newValue.toString()
            t("kingdom.turnWizard.preview.tributeRp", params.unsafeCast<com.foundryvtt.core.AnyObject>())
        }
        category == "projectedResources" -> {
            val params = js("{}")
            params["commodity"] = when (field) {
                "lumber" -> t("kingdom.lumber")
                "luxuries" -> t("kingdom.luxuries")
                "ore" -> t("kingdom.ore")
                "stone" -> t("kingdom.stone")
                else -> field
            }
            t("kingdom.turn.commodityCappedByStorage", params.unsafeCast<com.foundryvtt.core.AnyObject>())
        }
        category == "resourcePoints" && field == "now" -> {
            val params = js("{}")
            params["old"] = oldValue.toString()
            params["new"] = newValue.toString()
            t("kingdom.turnWizard.preview.changeRp", params.unsafeCast<com.foundryvtt.core.AnyObject>())
        }
        category == "fame" && field == "now" -> {
            val params = js("{}")
            params["old"] = oldValue.toString()
            params["new"] = newValue.toString()
            t("kingdom.turnWizard.preview.changeFame", params.unsafeCast<com.foundryvtt.core.AnyObject>())
        }
        category == "consumption" && field == "now" -> {
            val params = js("{}")
            params["old"] = oldValue.toString()
            params["new"] = newValue.toString()
            t("kingdom.turnWizard.preview.changeConsumption", params.unsafeCast<com.foundryvtt.core.AnyObject>())
        }
        category == "modifiers" && field == "expired" -> {
            val params = js("{}")
            params["count"] = newValue.toString()
            t("kingdom.turnWizard.preview.expiredModifiers", params.unsafeCast<com.foundryvtt.core.AnyObject>())
        }
        else -> {
            val label = when (category) {
                "resourcePoints" -> "RP"
                "resourceDice" -> "RD"
                "fame" -> "Fame"
                "consumption" -> "Consumption"
                else -> category
            }
            if (oldValue != null) {
                "$label ($field): $oldValue → $newValue"
            } else {
                "$label ($field): $newValue"
            }
        }
    }
}

private fun ProjectedResources.storageCapPreviewChanges(): List<TickChange> = listOfNotNull(
    if (lumberCappedByStorage) TickChange("projectedResources", "lumber", null, null) else null,
    if (luxuriesCappedByStorage) TickChange("projectedResources", "luxuries", null, null) else null,
    if (oreCappedByStorage) TickChange("projectedResources", "ore", null, null) else null,
    if (stoneCappedByStorage) TickChange("projectedResources", "stone", null, null) else null,
)

/**
 * Single source of truth for assembling [TurnTickingEngine.tick] arguments from a kingdom
 * snapshot. Both the End Turn commit path ([performEndTurn]) and the Turn Wizard preview
 * MUST call this — never tick() directly — so the preview cannot drift from what
 * committing actually applies. [currentTurn] is the turn being ticked into (previous + 1).
 */
/**
 * actorUuid -> level for every holding owner, resolved through the suspend uuid lookup. Failures
 * simply omit the entry -- the engine falls back to kingdom level for that holding.
 */
suspend fun resolveHoldingOwnerLevels(kingdom: KingdomData): Map<String, Int> {
    val uuids = (kingdom.personalHoldings ?: emptyArray()).mapNotNull { it.actorUuid }.distinct()
    if (uuids.isEmpty()) return emptyMap()
    val levels = mutableMapOf<String, Int>()
    for (uuid in uuids) {
        runCatching { fromUuidTypeSafe<PF2ECharacter>(uuid)?.system?.details?.level?.value }
            .getOrNull()
            ?.let { levels[uuid] = it }
    }
    return levels
}

fun runKingdomTurnTick(
    kingdom: KingdomData,
    storage: CommodityStorage,
    currentTurn: Int,
    /** actorUuid -> level, resolved impurely BY EACH CALLER before this pure function: the
     *  resolution needs a suspend uuid lookup, and a missing entry falls back to kingdom level
     *  inside the engine, so preview and commit still agree when an actor fails to resolve. */
    holdingOwnerLevels: Map<String, Int> = emptyMap(),
): TickResult =
    TurnTickingEngine.tick(
        fame = kingdom.fame,
        resourcePoints = kingdom.resourcePoints,
        resourceDice = kingdom.resourceDice,
        consumption = kingdom.consumption,
        commodities = kingdom.commodities,
        storage = storage,
        councilCooldowns = kingdom.councilCooldowns,
        activityUsage = kingdom.activityUsage ?: emptyArray(),
        modifiers = kingdom.modifiers,
        campaignClocks = kingdom.campaignClocks,
        campaignQuests = kingdom.campaignQuests ?: emptyArray(),
        kingdomLevel = kingdom.level,
        warThreats = kingdom.warThreats ?: emptyArray(),
        armyDeployments = kingdom.armyDeployments ?: emptyArray(),
        warPressure = kingdom.warPressure,
        currentTurn = currentTurn,
        xp = kingdom.xp,
        xpThreshold = kingdom.xpThreshold,
        rpNow = kingdom.resourcePoints.now,
        rpToXpConversionRate = kingdom.settings.rpToXpConversionRate,
        rpToXpConversionLimit = kingdom.settings.rpToXpConversionLimit,
        maximumFamePoints = kingdom.settings.maximumFamePoints,
        autoGainFamePerTurn = kingdom.settings.autoGainFamePerTurn,
        bonusResourceDice = kingdom.bonusResourceDice,
        activeBattles = kingdom.activeBattles ?: emptyArray(),
        // `groups` is typed non-null but can be undefined at runtime on kingdoms
        // predating the diplomacy subsystem; cast to nullable so the guard is a real
        // runtime check (matches the sibling arrays above) and tick() never sees undefined.
        groups = kingdom.groups.unsafeCast<Array<RawGroup>?>() ?: emptyArray(),
        kingdomName = kingdom.name,
        agendaMoves = factionAgendaMoveSpecs(),
        agendaArchetypes = factionAgendaArchetypeSpecs(),
        vanceAndKerensharaXp = kingdom.settings.vanceAndKerensharaXP,
        // Rival realms ride the SAME chokepoint as groups, so the End Turn commit, the wizard
        // preview and the forecast adapter all see identical growth (the KDoc above forbids
        // calling tick() directly for exactly this reason).
        rivalRealms = kingdom.rivalRealms ?: emptyArray(),
        personalHoldings = kingdom.personalHoldings ?: emptyArray(),
        holdingOwnerLevels = holdingOwnerLevels,
        rivalProfiles = runCatching { rivalGrowthProfilesById() }.getOrDefault(emptyMap()),
        factionStandingDriftPerTurn = kingdom.settings.factionStandingDriftPerTurn ?: 0,
    )

/**
 * Advance the kingdom by one turn. GM-only, enforced HERE rather than only at the callers:
 * ending a turn mutates the whole kingdom and every caller is a click handler on the party
 * actor, which players own. Gating only the sheet's "end-turn" button left the Turn Wizard's
 * Commit Turn button — ungated in both its template and its listener — as a complete bypass,
 * so the guard belongs at the single chokepoint every path funnels through.
 *
 * Returns null when the caller is not a GM; no caller uses the [TickResult].
 */
/**
 * Ends the kingdom turn. The entire tick -- read, mutate, persist, and the offer cards -- runs
 * under [councilVoteMutex], the same lock every council handler takes, because the tick is one
 * long read-modify-write of the whole kingdom flag held across MANY suspension points (chat
 * round-trips, rolls, inventory writes). Without the lock, a ballot cast or a vote closed while
 * the turn was ticking was persisted by its handler and then silently overwritten by the tick's
 * own wholesale write. With it, mid-tick council writes queue and apply to the POST-tick kingdom.
 *
 * The kingdom is read HERE, inside the lock, not passed in: the sheet's caller used to read it,
 * hold a confirm dialog open for an unbounded time, and then hand the stale copy over.
 *
 * No deadlock: nothing this function awaits takes the mutex itself -- the offer handlers do, but
 * they run on click, and a click during the tick simply queues until the lock is released.
 */
suspend fun performEndTurn(game: Game, actor: KingdomActor): TickResult? {
    // GM gate BEFORE the lock: a refused call must neither queue behind a ballot nor read a thing
    if (!game.user.isGM) {
        ui.notifications.warn(t("kingdom.turn.endTurnGmOnly"))
        return null
    }
    return councilVoteMutex.withLock { performEndTurnLocked(game, actor) }
}

private suspend fun performEndTurnLocked(game: Game, actor: KingdomActor): TickResult? {
    val kingdom = actor.getKingdom() ?: return null
    val seasonal = game.currentSeasonalModifiers()
    // Capture snapshot BEFORE any mutations — enables "undo-end-turn" (exact revert). Must cover
    // EVERYTHING End Turn mutates, not just the kingdom flag: the turn-wizard-state flag (performed
    // activity counts, cleared below) and the ids of shipment items added to the party inventory
    // (collected during delivery and written back into the snapshot afterward).
    val snapshotTurn = (kingdom.currentTurn ?: 0) + 1
    val deliveredItemIds = mutableListOf<String>()
    val snapshot = EndTurnSnapshot(
        kingdom = deepClone(kingdom),
        snapshotTurn = snapshotTurn,
        turnWizardState = actor.getAppFlag<KingdomActor, Any?>("turn-wizard-state")?.let { deepClone(it) },
        deliveredItemIds = emptyArray(),
    )
    actor.setAppFlag("lastTurnSnapshot", snapshot)

    val currentTurn = snapshotTurn
    kingdom.currentTurn = currentTurn

    val performed = actor.getPerformedActivities()
    val activitySummaries = performed.mapNotNull { (id, count) ->
        val act = kingdom.getActivity(id)
        if (act != null) {
            val title = act.title
            if (count > 1) "$title (x$count)" else title
        } else {
            null
        }
    }
    var caravanEvents = emptyList<CaravanEvent>()
    var shipmentEvents = emptyList<CaravanEvent>()

    actor.clearPerformedActivities()
    val realm = game.getRealmData(actor, kingdom)
    val previousSize = kingdom.turnHistory?.lastOrNull()?.size ?: realm.size
    val sizeChange = realm.size - previousSize
    val settlements = kingdom.getAllSettlements(game)
    val storage = calculateStorage(realm = realm, settlements = settlements.allSettlements)

    // Captured BEFORE the tick overwrites kingdom.warThreats: the digest reports escalations by
    // diffing pre- vs post-tick levels, and after the assignment below both sides would be equal.
    // Captured BEFORE the tick: it rewrites every finished battle's status to "archived", so a
    // read afterwards can never match DEFEAT and the gazette line was permanently empty.
    val preTickBattleDefeats = (kingdom.activeBattles ?: emptyArray())
        .filter { it.status == BattleStatus.DEFEAT.value }
        .map { it.name }

    val preTickThreats = kingdom.warThreats?.toList() ?: emptyList()
    val tickResult = runKingdomTurnTick(kingdom, storage, currentTurn, resolveHoldingOwnerLevels(kingdom))
    kingdom.supernaturalSolutions = tickResult.supernaturalSolutions
    kingdom.creativeSolutions = tickResult.creativeSolutions
    kingdom.fame = tickResult.fame
    kingdom.resourcePoints = tickResult.resourcePoints
    kingdom.resourceDice = tickResult.resourceDice
    kingdom.consumption = tickResult.consumption
    kingdom.commodities = tickResult.commodities
    kingdom.councilCooldowns = tickResult.councilCooldowns
    kingdom.activityUsage = tickResult.activityUsage
    // Pull Together: the flat-check DC decays by 1 per turn the feat goes unused, floored at its
    // printed 11, and the once-per-turn allowance resets.
    kingdom.pullTogetherCurrentDC = pullTogetherDcAfterTurn(
        currentDc = kingdom.pullTogetherCurrentDC ?: PULL_TOGETHER_BASE_DC,
        usedThisTurn = kingdom.pullTogetherUsedThisTurn == true,
    )
    kingdom.pullTogetherUsedThisTurn = false
    // Quality of Life's luxury bonus is once per Kingdom turn.
    kingdom.luxuryBonusUsedThisTurn = false
    // Envy of the World's free ignore is once per Kingdom turn.
    kingdom.envyOfTheWorldFirstIgnoreUsed = false
    // Decadent Feasts' shield is explicitly "this Kingdom turn" and does not carry over.
    kingdom.decadentFeastsShieldActive = false

    // Irrigation critical failures breed disease: each turn, a flat check whose DC climbs with the
    // number of spoiled hexes, and a failure invites a Plague event. Rolled at the turn boundary
    // because that is the module's one reliable per-turn chokepoint; RAW places it at the start of
    // the Event phase, which this immediately precedes.
    // set here, posted after the persist below -- the offer's button writes the kingdom flag
    var offerIrrigationPlague = false
    val spoiledHexes = kingdom.critFailedIrrigationHexes ?: 0
    if (spoiledHexes > 0) {
        val plagueDc = irrigationPlagueFlatCheckDc(spoiledHexes)
        val passed = d20Check(
            dc = plagueDc,
            flavor = t("kingdom.irrigation.plagueCheck", recordOf("hexes" to spoiledHexes)),
        ).degreeOfSuccess.succeeded()
        if (!passed) {
            offerIrrigationPlague = true
        }
    }
    // Liquidate Resources: the next turn rolls 4 fewer Resource Dice. Spending the penalty here
    // also clears the flag, which is what makes it the once-per-turn marker during the turn itself.
    // The Liquidate penalty is NOT applied here. It reduces the dice actually ROLLED, which happens
    // in collectResources at the start of the next turn -- this spot wrote to resourceDice.next,
    // which the tick has already zeroed, and cleared the flag so nothing could retry.
        // THE apply step that never existed: the conversion was reported three ways and granted
    // nowhere. Level handling mirrors the sheet converter's calculateXpChange.
    if (tickResult.xpAwarded > 0) {
        val xpChange = kingdom.calculateXpChange(tickResult.xpAwarded)
        kingdom.level += xpChange.addLevel
        kingdom.xp += xpChange.addXp
    }
    kingdom.modifiers = tickResult.modifiers
    kingdom.campaignQuests = tickResult.campaignQuests
    kingdom.warThreats = tickResult.warThreats
    kingdom.armyDeployments = tickResult.armyDeployments
    kingdom.warPressure = tickResult.warPressure
    kingdom.bonusResourceDice = tickResult.bonusResourceDice
    kingdom.activeBattles = tickResult.activeBattles
    kingdom.groups = tickResult.groups
    kingdom.rivalRealms = tickResult.rivalRealms
    kingdom.personalHoldings = tickResult.updatedPersonalHoldings.takeIf { it.isNotEmpty() } ?: kingdom.personalHoldings

    // GM-confirmed rival offers (plan section 5): collect from the post-growth state, then stamp
    // the war-offer watermark BEFORE the persist. The stamp is what stops an UNANSWERED offer
    // re-posting an identical digest every turn at an unchanged army count -- click-time
    // bookkeeping alone cannot, because an unclicked card bumps nothing (this is the contract
    // RawRivalRealm.lastWarOfferArmyCount documents: the count the offer FIRED at, not the count
    // the GM answered at). The digests themselves are posted after the persist, below.
    val (rivalWarRows, rivalShiftRows) = collectRivalOffers(
        realms = tickResult.rivalRealms,
        groups = tickResult.groups,
        moves = tickResult.rivalMoves.toList(),
    )
    if (rivalWarRows.isNotEmpty()) {
        val offeredIds = rivalWarRows.map { it.realmId }.toSet()
        kingdom.rivalRealms = kingdom.rivalRealms?.map { realm ->
            if (realm.id in offeredIds) realm.withWarOfferRecorded(realm.armyCount ?: 0) else realm
        }?.toTypedArray()
    }

    // Deadline + plague offers are COLLECTED here and posted after the persist below: their

    // buttons write the kingdom flag from the clicking client, and a click landing before End

    // Turn's own write is silently reverted by it (same class as the epithet cards).

    val deadlineQuestsToOffer = mutableListOf<dynamic>()



    // Post GM offer cards for quests that hit their deadline this turn
    if (tickResult.questDeadlineReached.isNotEmpty()) {
        tickResult.questDeadlineReached.forEach { questId ->
            val quest = kingdom.campaignQuests.find { it.id == questId }
            if (quest != null) {
                deadlineQuestsToOffer.add(quest)
            }
        }
    }

    // Apply campaign clock tick results (already included in tickResult)
    kingdom.campaignClocks = tickResult.updatedClocks
    if (tickResult.totalUnrestChange > 0) {
        kingdom.unrest = kingdom.unrest + tickResult.totalUnrestChange
    }

    // Caravan economy: advance in-transit caravans — raid checks, then arrival deliveries.
    val inTransitCaravans = (kingdom.caravans ?: emptyArray()).filter { it.status == "inTransit" }
    if (inTransitCaravans.isNotEmpty()) {
        val groupsByName = kingdom.groups.associateBy { it.name }
        val caravanResult = tickCaravans(
            inTransitCaravans.map { caravan ->
                val partner = groupsByName[caravan.partnerName]
                val provider = KingmakerHexGridProvider()
                val route = computeCaravanRoute(provider, caravan.originHexKey, caravan.destHexKey)
                // Same shared helper the map overlay uses, so the DC a caravan is actually
                // rolled against matches the one drawn on the route.
                val safety = caravanRouteSafety(route?.path.orEmpty().map { routeHexSafety(it) })
                CaravanTickInput(
                    caravan = caravan,
                    raidDc = caravanRaidDc(
                        baseDc = CARAVAN_BASE_RAID_DC,
                        partnerStanding = partner?.standing,
                        atWar = partner?.atWar == true,
                        claimedFraction = safety.claimedFraction,
                        fullyRoadedThroughClaimed = safety.fullyRoadedThroughClaimed,
                        seasonalDcDelta = seasonal.caravanRaidDcDelta,
                    ),
                    raidRoll = kotlin.random.Random.nextInt(1, 21),
                    rdPerCommodity = caravanRdPerCommodity(partner?.standing, partner?.allianceLevel),
                )
            },
            bonusRdCap = caravanBonusRdCap(kingdom.level),
        )
        kingdom.caravans = caravanResult.remaining.toTypedArray()
        caravanEvents = caravanResult.events
        // Record every resolved caravan to the delivery history. Appended to the in-hand kingdom so
        // it rides the turn's existing persist rather than adding a second write.
        caravanResult.events.forEach { event ->
            caravanEventToHistory(event, currentTurn)?.let { kingdom.appendShipment(it) }
        }
        if (caravanResult.bonusResourceDice != 0) {
            kingdom.bonusResourceDice = kingdom.bonusResourceDice + caravanResult.bonusResourceDice
        }
        if (caravanResult.deliveredCommodities.isNotEmpty()) {
            val now = kingdom.commodities.now.asDynamic()
            caravanResult.deliveredCommodities.forEach { (commodity, amount) ->
                now[commodity] = ((now[commodity].unsafeCast<Int?>()) ?: 0) + amount
            }
        }
        val caravanLines = caravanResult.events.map { event ->
            when (event.kind) {
                CaravanEventKind.DELIVERED ->
                    if (event.bonusResourceDice > 0)
                        t("kingdom.caravans.chatDeliveredRd", recordOf("summary" to event.summary, "rd" to event.bonusResourceDice.toString()))
                    else
                        t("kingdom.caravans.chatDeliveredCommodity", recordOf("summary" to event.summary, "amount" to event.deliveredAmount.toString()))
                CaravanEventKind.RAIDED ->
                    t("kingdom.caravans.chatRaided", recordOf("summary" to event.summary, "lost" to event.cargoLost.toString()))
                CaravanEventKind.LOST ->
                    t("kingdom.caravans.chatLost", recordOf("summary" to event.summary))
            }
        }
        val allCaravanLines = if (caravanResult.bonusResourceDiceCapped) {
            caravanLines + t(
                "kingdom.caravans.chatRdCapped",
                recordOf(
                    "earned" to caravanResult.uncappedBonusResourceDice.toString(),
                    "cap" to caravanResult.bonusResourceDice.toString(),
                ),
            )
        } else {
            caravanLines
        }
        if (allCaravanLines.isNotEmpty()) {
            val body = allCaravanLines.joinToString("") { "<li>$it</li>" }
            postChatMessage("<h3>${t("kingdom.caravans.title")}</h3><ul>$body</ul>", isHtml = true)
        }
    }

    // Caravan shipments: tick active/in-transit shipments en route.
    val inTransitShipments = (kingdom.shipments ?: emptyArray()).filter { it.status == "inTransit" }
    if (inTransitShipments.isNotEmpty()) {
        val shipmentResult = tickShipments(
            inTransitShipments.map { shipment ->
                // Same route-safety helper the caravans and the map overlay use. This used to be an
                // inline copy that scored only claimed hexes (never cleared ones), ignored roads
                // entirely, and read an empty route as perfectly safe rather than unknown.
                val safety = runCatching {
                    caravanRouteSafety(shipment.path.map { routeHexSafety(it) })
                }.getOrDefault(CaravanRouteSafety(claimedFraction = 0.0, fullyRoadedThroughClaimed = false))
                ShipmentTickInput(
                    shipment = shipment,
                    raidDc = shipmentRaidDc(safety, seasonalDcDelta = seasonal.caravanRaidDcDelta),
                    raidRoll = kotlin.random.Random.nextInt(1, 21),
                )
            }
        )

        // For delivered shipments, add them to the PF2e Party actor's inventory
        for (shipment in shipmentResult.delivered) {
            val itemData = js("""
                {
                    name: "",
                    type: "equipment",
                    system: {
                        quantity: 1,
                        level: { value: 1 },
                        bulk: { value: "1" },
                        price: { value: { gp: 0 } }
                    }
                }
            """)
            itemData.name = shipment.itemName
            itemData.system.quantity = shipment.itemQuantity
            itemData.system.level.value = shipment.itemLevel
            itemData.system.bulk.value = shipment.itemBulk
            itemData.system.price.value.gp = shipment.itemPriceGp
            actor.addToInventory(itemData.unsafeCast<com.foundryvtt.core.AnyObject>()).await()
                ?.id?.let { deliveredItemIds.add(it) }
        }

        // Save remaining in-transit shipments
        kingdom.shipments = shipmentResult.remaining.toTypedArray()

        // Record delivered item ids into the snapshot so undo can remove them (no double-deliver).
        if (deliveredItemIds.isNotEmpty()) {
            snapshot.deliveredItemIds = deliveredItemIds.toTypedArray()
            actor.setAppFlag("lastTurnSnapshot", snapshot)
        }
        shipmentEvents = shipmentResult.events

        // Generate chat logs
        val shipmentLines = shipmentResult.events.map { event ->
            when (event.kind) {
                CaravanEventKind.DELIVERED ->
                    t("kingdom.caravans.chatDeliveredShipment", recordOf("summary" to event.summary, "amount" to event.deliveredAmount.toString()))
                CaravanEventKind.RAIDED ->
                    t("kingdom.caravans.chatRaidedShipment", recordOf("summary" to event.summary, "lost" to event.cargoLost.toString()))
                CaravanEventKind.LOST ->
                    t("kingdom.caravans.chatLostShipment", recordOf("summary" to event.summary))
            }
        }
        if (shipmentLines.isNotEmpty()) {
            val body = shipmentLines.joinToString("") { "<li>$it</li>" }
            postChatMessage("<h3>${t("kingdom.caravans.shipmentTitle")}</h3><ul>$body</ul>", isHtml = true)
        }
    }

    // Balance & pacing alerts (roadmap #13): fire-once advisories, accumulated then posted to chat below.
    val firedPacingAlerts = mutableListOf<RawPacingAlert>()

    // Unrest stagnation — warns when unrest hasn't moved for too many turns.
    val stagnationTrack = trackUnrestStagnation(
        previousUnrest = kingdom.pacingLastUnrest,
        currentUnrest = kingdom.unrest,
        previousCount = kingdom.pacingTurnsSinceUnrestChange,
        maxTurnGap = kingdom.settings.pacingMaxTurnGap(),
        minDelta = kingdom.settings.pacingMinUnrestDelta(),
        turn = currentTurn,
    )
    kingdom.pacingTurnsSinceUnrestChange = stagnationTrack.turnsSinceUnrestChange
    kingdom.pacingLastUnrest = kingdom.unrest
    stagnationTrack.alert?.let { firedPacingAlerts.add(it) }

    // Level mismatch + loot imbalance — compared against the configured chapter target
    // level when one is set, otherwise the party's average level.
    val partyLevels = actor.partyMembers().map { it.system.details.level.value }
    val avgPartyLevel = if (partyLevels.isNotEmpty()) partyLevels.sum() / partyLevels.size else null
    val targetLevel = kingdom.settings.pacingChapterTargetLevel() ?: avgPartyLevel
    if (targetLevel != null) {
        val levelTrack = trackLevelMismatch(
            kingdomLevel = kingdom.level,
            partyLevel = targetLevel,
            range = kingdom.settings.pacingLevelMismatchRange(),
            previousSeverity = kingdom.pacingLastLevelMismatch,
            turn = currentTurn,
        )
        kingdom.pacingLastLevelMismatch = levelTrack.severity
        levelTrack.alert?.let { firedPacingAlerts.add(it) }

        // Loot imbalance — highest settlement item-purchase level vs the target level.
        if (kingdom.settings.pacingLootImbalanceEnabled()) {
            val offendingSettlement = settlements.allSettlements.maxByOrNull { it.itemPurchaseLevel }
            if (offendingSettlement != null) {
                val lootTrack = trackLootImbalance(
                    itemAccessLevel = offendingSettlement.itemPurchaseLevel,
                    partyLevel = targetLevel,
                    range = kingdom.settings.pacingLootImbalanceRange(),
                    previousSeverity = kingdom.pacingLastLootImbalance,
                    turn = currentTurn,
                    relatedEntityId = offendingSettlement.id,
                )
                kingdom.pacingLastLootImbalance = lootTrack.severity
                lootTrack.alert?.let { firedPacingAlerts.add(it) }
            }

            // Realized loot — treasure actually AWARDED vs the target level (loot-manifests SS6.1).
            // A second, independent track: the settlement metric above asks whether players can
            // BUY above their level, this one whether they have been HANDED too much. Its own
            // fire-once field, so the two cannot fight over severity. Runs even with an empty
            // ledger (implied level 1 simply never exceeds the range).
            val realizedTrack = trackRealizedLootImbalance(
                input = pacingLootInput(
                    ledger = (kingdom.treasureLedger ?: emptyArray()).map { it.toModel() },
                    partyLevel = targetLevel,
                    turn = currentTurn,
                ),
                range = kingdom.settings.pacingLootImbalanceRange(),
                previousSeverity = kingdom.pacingLastRealizedLootImbalance,
            )
            kingdom.pacingLastRealizedLootImbalance = realizedTrack.severity
            realizedTrack.alert?.let { firedPacingAlerts.add(it) }
        }
    }

    if (firedPacingAlerts.isNotEmpty()) {
        kingdom.pacingAlerts = (kingdom.pacingAlerts ?: emptyArray()) + firedPacingAlerts.toTypedArray()
    }

    // Per-turn history record (gap analysis item 2): snapshot post-tick kingdom state.
    val clockEventNames = tickResult.clockEvents.map { it.label }.toTypedArray()
    val tributeRp = tickResult.changes.find { it.category == "resourcePoints" && it.field == "tribute" }?.newValue as? Int ?: 0
    val battleDefeats = preTickBattleDefeats
    // Rival growth is visible on the map, so the same headlines go into BOTH the GM gazette and
    // the player-safe one -- unlike campaign clocks, there is nothing secret to strip.
    val rivalHeadlines = tickResult.rivalMoves.map { localizeRivalHeadline(it) }
    // Faction agenda lines are public by the same argument: the move is visible in the world.
    val factionMoveLines = tickResult.factionAgendaMoves.map { localizeAgendaMoveLine(it) }
    // the Spotlight names a PC and counts their own public deeds, so it is player-safe by
    // construction and goes into BOTH gazettes -- a line that only reached the GM's whisper
    // would vanish from the campaign's written record
    val spotlightLine = spotlightOfTheTurn(
        (kingdom.currentTurnContributions ?: emptyArray()).toTurnTallies()
    )?.let { localizeSpotlight(it) }
    // Settlement life rolls here, before the gazette is written, so its lines land in the turn
    // record; the records it appends ride the single persist below and the digest posts after.
    val lifeEventsFired = rollSettlementLifeEvents(
        kingdom = kingdom,
        settlements = kingdom.getAllSettlements(game).allSettlements,
        season = runCatching { getSeasonForMonth(game.getCurrentMonth().ordinal).value }.getOrNull(),
        currentTurn = currentTurn,
    )
    val lifeEventLines = lifeEventsFired.map { it.gazetteLine }

    val turnNotes = formatTurnGazette(
        activities = activitySummaries,
        sizeChange = sizeChange,
        currentSize = realm.size,
        caravanEvents = caravanEvents,
        shipmentEvents = shipmentEvents,
        campaignClocks = tickResult.clockEvents.map { it.label },
        tributeRp = tributeRp,
        expeditionChronicle = kingdom.expeditionChronicle?.toList() ?: emptyList(),
        battleDefeats = battleDefeats,
        turn = currentTurn,
        rivalMoves = rivalHeadlines,
        factionMoves = factionMoveLines,
        spotlight = spotlightLine,
        lifeEvents = lifeEventLines,
        localize = ::t,
    )
    // Player-safe gazette: identical EXCEPT the secret campaign-clock progress is dropped, so the
    // player Recent-Turns timeline can show the public notes without leaking GM-only clock labels.
    val playerTurnNotes = formatTurnGazette(
        activities = activitySummaries,
        sizeChange = sizeChange,
        currentSize = realm.size,
        caravanEvents = caravanEvents,
        shipmentEvents = shipmentEvents,
        campaignClocks = emptyList(),
        tributeRp = tributeRp,
        expeditionChronicle = kingdom.expeditionChronicle?.toList() ?: emptyList(),
        battleDefeats = battleDefeats,
        turn = currentTurn,
        rivalMoves = rivalHeadlines,
        factionMoves = factionMoveLines,
        spotlight = spotlightLine,
        lifeEvents = lifeEventLines,
        localize = ::t,
    )

    val warPressureNow = kingdom.warPressure?.currentPressure
    val warPressurePerTurn = kingdom.warPressure?.pressurePerTurn
    kingdom.turnHistory = appendTurnRecord(
        history = kingdom.turnHistory,
        record = buildTurnRecord(
            turn = currentTurn,
            timestamp = kotlin.js.Date().toISOString(),
            fame = kingdom.fame.now,
            resourcePoints = kingdom.resourcePoints.now,
            consumption = kingdom.consumption.now,
            unrest = kingdom.unrest,
            xpAwarded = tickResult.xpAwarded,
            clockEvents = clockEventNames,
            warPressure = warPressureNow,
            pressurePerTurn = warPressurePerTurn,
            level = kingdom.level,
            size = realm.size,
            ruinCorruption = kingdom.ruin.corruption.value,
            ruinCrime = kingdom.ruin.crime.value,
            ruinDecay = kingdom.ruin.decay.value,
            ruinStrife = kingdom.ruin.strife.value,
            notes = turnNotes,
            playerNotes = playerTurnNotes,
            // votes the council closed THIS turn, derived rather than stamped at click time: a
            // vote records the turn it closed on, so the record can always recompute the set --
            // and a vote closed and reopened within the same turn correctly leaves nothing behind
            // the turn's per-PC tallies are FROZEN onto the record here and the live arrays
            // reset below, so next turn starts from zero and this turn's Spotlight stays readable
            // forever
            contributions = (kingdom.currentTurnContributions ?: emptyArray()).takeIf { it.isNotEmpty() },
            closedVoteIds = (kingdom.councilVotes ?: emptyArray())
                .filter { it.closedTurn == currentTurn }
                .mapNotNull { it.id }
                .toTypedArray()
                .takeIf { it.isNotEmpty() },
        ),
    )

    // NPC memories read the record that was just appended plus its predecessor, and write
    // straight onto the roster rows -- all inside the same single persist below.
    val npcMemoryOutcome = evaluateNpcMemoriesForTurn(
        kingdom = kingdom,
        currentTurn = currentTurn,
        rules = npcMemoryRules(),
        fameMax = kingdom.settings.maximumFamePoints,
    )

    // Renown: the epithet offers this turn earned, then the reset. Offers are composed BEFORE
    // the reset because they read the cumulative ledger, not the turn tally, and the reset only
    // clears the in-progress arrays.
    val epithetOffers = pendingEpithetOffers(
        renownRows = kingdom.renown,
        rulerUuid = kingdom.rulerActorUuid(),
        turn = currentTurn,
    )
    // the STAMP is part of the tick and must be inside the persist; the CARDS go out after it,
    // for the same reason the rival digests do -- their grant buttons write the kingdom flag from
    // other clients, and a click in the pre-persist window is silently lost
    stampEpithetOffersMade(kingdom, currentTurn, epithetOffers)

    // Damage hook #4: a hex-bound holding whose hex BECAME unclaimed this turn. Edge-detected via
    // lastKnownClaimed -- without the previous value this could only see "is unclaimed" and would
    // re-offer every turn. The stamp is part of the tick (inside the persist); the offers post
    // after it with the rest. Read-only against kingmaker.state; never inside tick().
    val unclaimedHoldings = mutableListOf<at.posselt.pfrpg2e.kingdom.data.RawPersonalHolding>()
    kingdom.personalHoldings = kingdom.personalHoldings?.map { holding ->
        val hexKey = holding.boundHexKey ?: return@map holding
        val claimedNow = runCatching {
            com.foundryvtt.kingmaker.kingmaker.state.hexes[hexKey]?.claimed == true
        }.getOrDefault(true)
        val wasClaimed = holding.lastKnownClaimed
        if (wasClaimed == true && !claimedNow) {
            val stamped = RawPersonalHolding.copy(holding, lastKnownClaimed = false)
            unclaimedHoldings.add(stamped)
            stamped
        } else if (wasClaimed != claimedNow) {
            RawPersonalHolding.copy(holding, lastKnownClaimed = claimedNow)
        } else holding
    }?.toTypedArray()

    // Expiry runs BEFORE generation so a role whose audience lapses this turn is free to receive
    // the next one immediately, rather than sitting a turn out behind a petition already dead.
    val expiredPetitions = expirePetitionsForTurn(kingdom, currentTurn)
    val newPetitions = generatePetitionsForTurn(
        kingdom = kingdom,
        currentTurn = currentTurn,
        structureNames = kingdom.getAllSettlements(game).allSettlements
            .flatMap { settlement -> settlement.constructedStructures.map { it.name } }
            .toSet(),
        ongoingEventNames = kingdom.ongoingEvents.map { it.id }.toSet(),
    )

    // Rewild bookkeeping is part of the tick, so it belongs inside the persist below rather than
    // in the offer poster that runs after it (where its write would clobber card clicks).
    runCatching { reconcileRewild(kingdom, clearedUnclaimedHexes(), currentTurn) }
    kingdom.currentTurnContributions = emptyArray()
    kingdom.currentTurnDeeds = emptyArray()

    val automateResources = kingdom.settings.automateResources != "manual"
    if (automateResources) {
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

    actor.setKingdom(kingdom)

    // Rival offer digests go out only now, after the tick is persisted: the cards' buttons
    // read-modify-write the actor flag from OTHER clients, and a click landing in the window
    // between a mid-tick post and the persist above would either be clobbered by it or clobber
    // the whole ticked turn with pre-tick state.
    postEpithetOffers(
        game = game,
        actorUuid = actor.uuid,
        offers = epithetOffers,
    )

    if (offerIrrigationPlague) postIrrigationPlagueOffer(game, actor)
    deadlineQuestsToOffer.forEach { quest -> postQuestDeadlineOffer(game, actor, quest) }

    // Damage hook #1: a war threat that TRIGGERED this turn strikes the holdings at its target
    // hex or settlement -- a triggered siege is MAJOR. Same post-after-persist rule as every card.
    for (threat in tickResult.newlyTriggeredThreats) {
        val struck = holdingsAt(
            kingdom.personalHoldings,
            hexKey = threat.targetHexLocation,
            sceneId = threat.targetSettlementSceneId,
        )
        for (holding in struck) {
            postHoldingDamageOffer(
                game = game,
                actorUuid = actor.uuid,
                holding = holding,
                severity = DamageSeverity.MAJOR,
                cause = threat.name ?: t("kingdom.holdings.damageOffer.warThreat"),
            )
        }
    }
    // Damage hook #4's offers (the edge was detected and stamped before the persist)
    for (holding in unclaimedHoldings) {
        postHoldingDamageOffer(
            game = game,
            actorUuid = actor.uuid,
            holding = holding,
            severity = DamageSeverity.MINOR,
            cause = t("kingdom.holdings.damageOffer.hexLost"),
        )
    }

    postHoldingIncomeOffer(
        game = game,
        actorUuid = actor.uuid,
        currentTurn = currentTurn,
        lines = tickResult.holdingIncomeOffers.toList(),
    )

    postRivalOfferDigests(
        game = game,
        actorUuid = actor.uuid,
        currentTurn = currentTurn,
        warRows = rivalWarRows,
        shiftRows = rivalShiftRows,
    )

    postFactionMoveDigest(
        game = game,
        actorUuid = actor.uuid,
        currentTurn = currentTurn,
        moves = tickResult.factionAgendaMoves,
    )

    postXpLedgerDigest(game, actor.uuid, currentTurn)

    postSettlementLifeDigest(game, actor.uuid, currentTurn, lifeEventsFired)
    postPetitionExpiryOffers(game, actor.uuid, expiredPetitions)
    postNewPetitionNotice(game, actor.uuid, newPetitions)

    postNpcAttitudeShiftOffers(
        game = game,
        actorUuid = actor.uuid,
        crossings = npcMemoryOutcome.crossings,
    )

    // Post clock tick events to chat
    if (tickResult.clockEvents.isNotEmpty()) {
        val clockContext = js("{}")
        clockContext.events = tickResult.clockEvents
        clockContext.totalUnrestChange = tickResult.totalUnrestChange
        postChatTemplate(
            templatePath = "chatmessages/clock-tick.hbs",
            templateContext = clockContext,
        )
    }

    // Post war threat arrival offer cards for newly triggered threats (roadmap #12 payoff)
    if (tickResult.newlyTriggeredThreats.isNotEmpty()) {
        // GM-whispered: the card carries GM-only offer buttons and leaks linked-hex existence,
        // so it must never render on player clients (baked isGM was evaluated on the POSTING
        // client and shipped true to everyone).
        val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
        if (gmUserIds.isNotEmpty()) {
            for (threat in tickResult.newlyTriggeredThreats) {
                // Built by the shared poster so a rerolled card is identical to the original
                // except for the freshly rolled selection.
                postWarThreatArrivalOffer(
                    game = game,
                    actorUuid = actor.uuid,
                    kingdom = kingdom,
                    threat = threat,
                    gmUserIds = gmUserIds,
                )
            }
        }
    }

    // War pressure crossed its ruin threshold this turn: GM-confirmed offer to let the strain
    // bite as +1 to a Ruin of the GM's choice (or be dismissed). GM-whispered — the buttons are
    // GM-only and must never render on player clients.
    if (tickResult.ruinThresholdCrossed) {
        val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
        if (gmUserIds.isNotEmpty()) {
            val offerContext = js("{}")
            offerContext.actorUuid = actor.uuid
            offerContext.turn = currentTurn
            postChatTemplate(
                templatePath = "chatmessages/war-pressure-ruin-offer.hbs",
                templateContext = offerContext,
                whisper = gmUserIds,
            )
        }
    }

    // Auto-detected deeds (deeds-chronicle plan): read-only detection over standing state, one
    // whispered digest, never an auto-award. Detection runs AFTER the persist so it sees the turn
    // this End Turn just wrote -- history-based deeds would otherwise miss their own last turn.
    postDeedsDigest(
        game, actor, kingdom,
        detectFiredDeeds(
            kingdom = kingdom,
            currentTurn = currentTurn,
            // realm.size is the authoritative size; kingdom.size only tracks it in manual mode
            realmSize = realm.size,
            // parsed, not RawSettlement.level: that field is written once as 1 and never updated
            settlementSizes = kingdom.getAllSettlements(game).allSettlements.map { it.size.type },
            // counted AFTER the tick from archivedOutcome, so the deed stays
            // level-triggered instead of vanishing the turn after a win
            armiesWon = countArmyVictories(kingdom),
        ),
    )

    // Post any pacing advisories that fired this turn to chat
    firedPacingAlerts.forEach { alert -> postPacingAlertChat(alert) }

    val endTurnContext = js("{}")
    endTurnContext.clockEvents = tickResult.clockEvents
    endTurnContext.changes = tickResult.changes.map { it.toDisplayString() }.toTypedArray()
    endTurnContext.kingdomName = kingdom.name
    endTurnContext.xpAwarded = tickResult.xpAwarded
    endTurnContext.fame = kingdom.fame.now
    endTurnContext.maximumFamePoints = kingdom.settings.maximumFamePoints
    endTurnContext.actorUuid = actor.uuid
    // Snapshot exists after End Turn (unless undone), so the chat card can show the undo button.
    endTurnContext.hasUndoSnapshot = true
    endTurnContext.snapshotTurn = snapshotTurn
    endTurnContext.standingChanges = tickResult.changes
        .filter { it.category == "factionStanding" }
        .map { change ->
            val groupName = kingdom.groups.find { it.name == change.field }?.name ?: change.field
            val oldStanding = change.oldValue as? Int ?: 0
            val newStanding = change.newValue as? Int ?: 0
            val entry = js("{}")
            entry.group = groupName
            entry.oldAttitude = t(attitudeFor(oldStanding).i18nKey)
            entry.newAttitude = t(attitudeFor(newStanding).i18nKey)
            entry.delta = newStanding - oldStanding
            // GM-confirmed threshold offers (never auto-applied): surface a button only on
            // the tick that crosses into Hostile (war threat) or Friendly+ (diplomacy quest).
            entry.offerWarThreat = shouldOfferWarThreat(oldStanding, newStanding)
            entry.offerDiplomacyQuest = shouldOfferDiplomacyQuest(oldStanding, newStanding)
            entry
        }.toTypedArray()
    postChatTemplate(
        templatePath = "chatmessages/end-turn.hbs",
        templateContext = endTurnContext,
    )

    // One public read-aloud interlude summarising the turn's off-screen feeds (player-safe by
    // construction; see DigestAdapter's KDoc for which feeds qualify and why). ADDITIVE for
    // now: the per-feed caravan/shipment cards above still post; silencing them in favour of
    // the digest is a rollout decision for Gregory, not something to bundle into this commit.
    postEndTurnDigest(
        game = game,
        actor = actor,
        caravanEvents = caravanEvents,
        shipmentEvents = shipmentEvents,
        expeditionChronicle = kingdom.expeditionChronicle,
        preTickThreats = preTickThreats,
        postTickThreats = kingdom.warThreats?.toList() ?: emptyList(),
        turn = currentTurn,
        enabledSetting = kingdom.settings.meanwhileDigestEnabled,
        maxBeatsSetting = kingdom.settings.meanwhileDigestMaxBeats,
    )

    // Map dynamism (plan SS3.3): reconcile the re-wild timers and offer any threat steps /
    // re-wilds as ONE GM card. Offers only -- the shared map changes in the click handlers.
    postMapDynamismOffers(game, actor, kingdom, currentTurn)

    val changesText = tickResult.changes
        .map { it.toDisplayString() }
        .joinToString("\n") { "- $it" }
    logToCalendar(
        title = "Kingdom Turn $currentTurn Complete",
        content = "Kingdom Turn $currentTurn completed.\n\nChanges:\n$changesText"
    )

    return tickResult
}

/**
 * Revert the most recent End Turn from the `lastTurnSnapshot` flag. Restores the kingdom AND the
 * turn-wizard-state flag (performed-activity counts) AND deletes the party-inventory items that
 * shipment delivery created this turn, so the revert is EXACT and re-running End Turn cannot
 * double-deliver. Does NOT un-post chat messages or un-log the calendar (documented limitation).
 * Returns true if an undo happened. Callers must GM-gate.
 */
/** Undo is the tick's mirror -- a wholesale restore of the snapshot -- so it takes the same lock
 *  for the same reason: a ballot landing mid-undo must queue rather than be reverted. */
suspend fun undoEndTurn(game: Game, actor: KingdomActor): Boolean =
    councilVoteMutex.withLock { undoEndTurnLocked(game, actor) }

private suspend fun undoEndTurnLocked(game: Game, actor: KingdomActor): Boolean {
    val snap = actor.getAppFlag<KingdomActor, Any?>("lastTurnSnapshot")?.unsafeCast<EndTurnSnapshot>() ?: return false
    // The erased external-interface cast can't detect a malformed flag; validate shape (undefined == null in JS).
    val d = snap.asDynamic()
    if (d.kingdom == null || d.snapshotTurn == null) {
        actor.unsetAppFlag("lastTurnSnapshot")
        return false
    }
    val kingdom = actor.getKingdom() ?: return false
    // Only the most recent End Turn is undoable.
    if ((kingdom.currentTurn ?: 0) != snap.snapshotTurn) return false

    actor.setKingdom(deepClone(snap.kingdom))
    actor.restoreTurnWizardState(snap.turnWizardState)
    snap.deliveredItemIds?.takeIf { it.isNotEmpty() }?.let { ids ->
        actor.deleteEmbeddedDocuments<PF2EItem>("Item", ids).await()
    }
    actor.unsetAppFlag("lastTurnSnapshot")
    // The digest dedup baseline now describes a turn that no longer happened; clearing it keeps
    // the flag honest (the re-run re-posts its card -- the same documented limitation as every
    // other chat message under undo). This belongs HERE, on the path where an undo actually
    // happened: sitting in the malformed-snapshot guard above, it fired only when NOTHING was
    // undone -- destroying a valid baseline in that case and leaving a stale one in this one.
    actor.unsetAppFlag("lastDigestBeats")

    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    val ctx = js("{}")
    ctx.turn = snap.snapshotTurn
    ctx.kingdomName = snap.kingdom.name
    if (gmUserIds.isNotEmpty()) {
        postChatTemplate(templatePath = "chatmessages/end-turn-undo.hbs", templateContext = ctx, whisper = gmUserIds)
    } else {
        postChatMessage(t("chatMessages.endTurn.endTurnUndone", recordOf("turn" to snap.snapshotTurn.toString())))
    }
    return true
}

private fun signedDelta(n: Int): String = if (n > 0) "+$n" else n.toString()

/**
 * Posts a GM-whispered "Last Turn Recap" chat card summarizing the most recent turn's stat deltas
 * and gazette notes. Fired when the Turn Wizard opens (the card's trigger). Idempotent per turn via
 * the "lastRecapTurn" app-flag, so re-opening the wizard for the same turn does not re-post. GM-only
 * and GM-whispered (nothing reaches players); skipped when there is no history or no GM users.
 */
suspend fun postLastTurnRecap(game: Game, actor: KingdomActor) {
    if (!game.user.isGM) return
    val kingdom = actor.getKingdom() ?: return
    val recap = computeLastTurnRecap(kingdom.turnHistory) ?: return
    if (actor.getAppFlag<KingdomActor, Int?>("lastRecapTurn") == recap.turn) return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return
    val ctx = js("{}")
    ctx.turn = recap.turn
    ctx.fameDelta = signedDelta(recap.fameDelta)
    ctx.fameNow = recap.fameNow
    ctx.rpDelta = signedDelta(recap.rpDelta)
    ctx.rpNow = recap.rpNow
    ctx.unrestDelta = signedDelta(recap.unrestDelta)
    ctx.unrestNow = recap.unrestNow
    ctx.hasWarPressure = recap.hasWarPressure
    ctx.warPressureDelta = signedDelta(recap.warPressureDelta)
    ctx.warPressureNow = recap.warPressureNow
    ctx.xpAwarded = recap.xpAwarded
    val noteLines = recap.notes?.split(" | ")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    // the turn's Spotlight, surfaced at TURN OPEN rather than End Turn: it is the "here is what
    // your table did last month" beat, and it belongs with the rest of the recap
    recap.spotlight?.let { ctx.spotlight = localizeSpotlight(it) }
    if (noteLines.isNotEmpty()) ctx.notesList = noteLines.toTypedArray()
    postChatTemplate(
        templatePath = "chatmessages/last-turn-recap.hbs",
        templateContext = ctx,
        whisper = gmUserIds,
    )
    actor.setAppFlag("lastRecapTurn", recap.turn)
}

class TurnWizardApplication(
    private val kingdomActor: KingdomActor,
) : SimpleApp<TurnWizardContext>(
    title = t("kingdom.turnWizard.title"),
    template = "applications/kingdom/turn-wizard.hbs",
    classes = setOf("km-scroll-application"),
    scrollable = setOf(".window-content"),
    id = "kmTurnWizard-${kingdomActor.uuid}",
    width = 600,
    height = 700,
    resizable = true,
) {
    private var cachedChanges: Array<TickChange> = emptyArray()

    override fun _preparePartContext(
        partId: String,
        context: at.posselt.pfrpg2e.app.HandlebarsRenderContext,
        options: com.foundryvtt.core.applications.api.HandlebarsRenderOptions
    ): Promise<TurnWizardContext> = buildPromise {
        val kingdom = kingdomActor.getKingdom() ?: throw IllegalStateException("No kingdom data")
        
        val state = kingdomActor.getAppFlag<KingdomActor, dynamic>("turn-wizard-state")
        val checkedItems = mutableSetOf<String>()
        var showPreview = false
        if (state != null) {
            showPreview = state.showPreview.unsafeCast<Boolean>()
            if (state.checklist != null) {
                val array = state.checklist.unsafeCast<Array<String>>()
                checkedItems.addAll(array)
            }
        }
        
        val previewChangesContext = cachedChanges.map { change ->
            TickChangeContext(
                category = change.category,
                field = change.field,
                displayText = change.toDisplayString()
            )
        }.toTypedArray()
        
        buildContext(
            kingdom = kingdom,
            actor = kingdomActor,
            partId = partId,
            isFormValid = true,
            checkedItems = checkedItems,
            showPreview = showPreview,
            previewChanges = previewChangesContext
        )
    }

    override fun _attachPartListeners(partId: String, htmlElement: HTMLElement, options: com.foundryvtt.core.applications.api.ApplicationRenderOptions) {
        super._attachPartListeners(partId, htmlElement, options)
        
        htmlElement.querySelectorAll("input[type='checkbox'].km-checklist-toggle").asList()
            .filterIsInstance<HTMLElement>()
            .forEach { checkbox ->
                checkbox.addEventListener("change", {
                    val id = checkbox.dataset["id"] ?: return@addEventListener
                    buildPromise {
                        toggleChecklistItem(id)
                    }
                })
            }
            
        htmlElement.querySelector("button[data-action='preview-turn']")
            ?.addEventListener("click", {
                buildPromise {
                    previewTurn()
                }
            })

        htmlElement.querySelector("button[data-action='run-upkeep']")
            ?.addEventListener("click", {
                buildPromise {
                    if (game.user.isGM) {
                        runUpkeepBatch()
                    }
                }
            })
            
        htmlElement.querySelector("button[data-action='commit-turn']")
            ?.addEventListener("click", {
                buildPromise {
                    // Same guard as the run-upkeep sibling above. The button is also hidden
                    // for players in the template, but template gating is not a permission
                    // check — players own the party actor and this listener is theirs to fire.
                    if (game.user.isGM) {
                        commitTurn()
                    } else {
                        ui.notifications.warn(t("kingdom.turn.endTurnGmOnly"))
                    }
                }
            })
            
        htmlElement.querySelector("button[data-action='cancel']")
            ?.addEventListener("click", {
                buildPromise {
                    cancel()
                }
            })

        htmlElement.querySelector("button[data-action='undo-end-turn']")
            ?.addEventListener("click", {
                buildPromise {
                    if (game.user.isGM && undoEndTurn(game, kingdomActor)) {
                        render()
                    }
                }
            })
    }

    private suspend fun toggleChecklistItem(id: String) {
        var state = kingdomActor.getAppFlag<KingdomActor, dynamic>("turn-wizard-state")
        if (state == null) {
            state = js("{ checklist: [], showPreview: false }")
        }
        if (state.checklist == null) {
            state.checklist = emptyArray<String>()
        }
        val checklistArray = state.checklist.unsafeCast<Array<String>>()

        val kingdom = kingdomActor.getKingdom() ?: return
        val isStrict = kingdom.settings.enableStrictPhaseGating == true

        val newChecklist = applyChecklistToggle(checklistArray.toList(), id, isStrict).toTypedArray()
        state.checklist = newChecklist
        // Persist with Foundry's automatic re-render suppressed, then drive a serialized forced
        // render of the Kingdom Sheet. Two problems make the naive path unreliable:
        //  1. Foundry does not fire the actor-update hook when the checklist array *shrinks*
        //     (cascading uncheck), so gating would stay stale.
        //  2. The Kingdom Sheet's render is async and slow; overlapping renders from rapid toggles
        //     race and a stale one can paint last.
        // Suppressing the auto-render and serializing forced renders (one in flight at a time, with
        // a re-render queued if more toggles arrive) guarantees the final render reads the settled
        // checklist, so phase gating repaints reliably regardless of toggle speed.
        val updateData = js("{}")
        updateData["flags.${Config.moduleId}.turn-wizard-state"] = state
        kingdomActor.update(updateData, js("{ render: false }").unsafeCast<DatabaseUpdateOperation>()).await()
        refreshKingdomSheets()
        render()
    }

    private var sheetRenderInFlight: Boolean = false
    private var sheetRenderQueued: Boolean = false

    /**
     * Forced re-render of open Kingdom Sheets, serialized so at most one render runs at a time.
     * If more toggles arrive mid-render, exactly one follow-up render is queued, which re-reads the
     * latest checklist — so the final paint always reflects the settled state and slow async renders
     * cannot race. The Turn Wizard is a [SimpleApp] not registered on the actor, so only the sheet
     * is refreshed.
     */
    private suspend fun refreshKingdomSheets() {
        sheetRenderQueued = true
        if (sheetRenderInFlight) return
        sheetRenderInFlight = true
        try {
            while (sheetRenderQueued) {
                sheetRenderQueued = false
                Object.values(kingdomActor.apps).forEach {
                    it.render(ApplicationRenderOptions(force = true)).await()
                }
            }
        } finally {
            sheetRenderInFlight = false
        }
    }

    private suspend fun runUpkeepBatch() {
        val kingdom = kingdomActor.getKingdom() ?: return
        var state = kingdomActor.getAppFlag<KingdomActor, dynamic>("turn-wizard-state")
        if (state == null) {
            state = js("{ checklist: [], showPreview: false }")
        }
        if (state.checklist == null) {
            state.checklist = emptyArray<String>()
        }
        val checklistArray = state.checklist.unsafeCast<Array<String>>()
        val checkedItems = checklistArray.toMutableSet()

        val runFame = "gain-fame" !in checkedItems
        val runUnrest = "adjust-unrest" !in checkedItems
        val runResources = "collect-resources" !in checkedItems
        val runConsumption = "pay-consumption" !in checkedItems

        if (!runFame && !runUnrest && !runResources && !runConsumption) {
            ui.notifications.info(t("kingdom.turnWizard.upkeepAlreadyDone"))
            return
        }

        val clonedKingdom = deepClone(kingdom)

        val oldFame = kingdom.fame.now
        val oldUnrest = kingdom.unrest
        val oldRp = kingdom.resourcePoints.now
        val oldOre = kingdom.commodities.now.ore
        val oldLumber = kingdom.commodities.now.lumber
        val oldStone = kingdom.commodities.now.stone
        val oldLuxuries = kingdom.commodities.now.luxuries
        val oldFood = kingdom.commodities.now.food

        var fameDelta = 0
        var unrestDelta = 0
        var rpCollected = 0
        var oreCollected = 0
        var lumberCollected = 0
        var stoneCollected = 0
        var luxuriesCollected = 0
        var consumptionPaidAmount = 0

        if (runFame) {
            fameDelta = kingdomActor.upkeepGainFame(clonedKingdom, suppressChat = true)
            checkedItems.add("gain-fame")
        }
        if (runUnrest) {
            unrestDelta = kingdomActor.upkeepAdjustUnrest(game, clonedKingdom, suppressChat = true)
            checkedItems.add("adjust-unrest")
        }
        if (runResources) {
            val income = kingdomActor.upkeepCollectResources(game, clonedKingdom, suppressChat = true)
            rpCollected = income.resourcePoints - oldRp
            oreCollected = income.ore - oldOre
            lumberCollected = income.lumber - oldLumber
            stoneCollected = income.stone - oldStone
            luxuriesCollected = income.luxuries - oldLuxuries
            checkedItems.add("collect-resources")
        }
        if (runConsumption) {
            val foodDiff = kingdomActor.upkeepPayConsumption(game, clonedKingdom, suppressChat = true)
            consumptionPaidAmount = foodDiff
            checkedItems.add("pay-consumption")
        }

        kingdomActor.setKingdom(clonedKingdom)

        val isStrict = kingdom.settings.enableStrictPhaseGating == true
        var finalChecklist = checkedItems.toList()
        if (isStrict) {
            // Respect strict Checklist sequence ordering if strict phase gating is enabled
            val sequence = strictChecklistSequence
            val indexOrder = sequence.associateWith { sequence.indexOf(it) }
            // Ensure checked items are fully compliant with checklist toggling rules
            // We can just sort them by sequence index to keep clean ordering
            val sequenceSet = sequence.toSet()
            finalChecklist = finalChecklist.filter { it in sequenceSet }.sortedBy { indexOrder[it] ?: 999 } +
                             finalChecklist.filter { it !in sequenceSet }
        }
        state.checklist = finalChecklist.toTypedArray()

        val updateData = js("{}")
        updateData["flags.${Config.moduleId}.turn-wizard-state"] = state
        kingdomActor.update(updateData, js("{ render: false }").unsafeCast<DatabaseUpdateOperation>()).await()

        val summaryContext = js("{}")
        summaryContext.fameGained = runFame
        summaryContext.fameDelta = fameDelta
        summaryContext.fameNow = clonedKingdom.fame.now

        summaryContext.unrestAdjusted = runUnrest
        summaryContext.unrestDelta = unrestDelta
        summaryContext.unrestNow = clonedKingdom.unrest

        summaryContext.resourcesCollected = runResources
        summaryContext.rpCollected = rpCollected
        summaryContext.rpNow = clonedKingdom.resourcePoints.now
        summaryContext.oreCollected = oreCollected
        summaryContext.lumberCollected = lumberCollected
        summaryContext.stoneCollected = stoneCollected
        summaryContext.luxuriesCollected = luxuriesCollected

        summaryContext.consumptionPaid = runConsumption
        summaryContext.consumptionPaidAmount = consumptionPaidAmount
        summaryContext.foodNow = clonedKingdom.commodities.now.food

        postChatTemplate(
            templatePath = "chatmessages/upkeep-summary.hbs",
            templateContext = summaryContext,
        )

        refreshKingdomSheets()
        render()
    }

    private suspend fun previewTurn() {
        var state = kingdomActor.getAppFlag<KingdomActor, dynamic>("turn-wizard-state")
        if (state == null) {
            state = js("{ checklist: [], showPreview: false }")
        }
        state.showPreview = true
        kingdomActor.setAppFlag("turn-wizard-state", state)
        
        val kingdom = kingdomActor.getKingdom() ?: return
        val realm = game.getRealmData(kingdomActor, kingdom)
        val settlements = kingdom.getAllSettlements(game)
        val storage = calculateStorage(realm, settlements.allSettlements)
        // Simulate the same upcoming turn End Turn will tick into, without persisting the increment.
        val tickResult = runKingdomTurnTick(kingdom, storage, (kingdom.currentTurn ?: 0) + 1, resolveHoldingOwnerLevels(kingdom))

        val storageCapChanges = if (kingdom.settings.automateResources != "manual") {
            val allFeatures = kingdom.getExplodedFeatures()
            val chosenFeatures = kingdom.getChosenFeatures(allFeatures)
            val chosenFeats = kingdom.getChosenFeats(chosenFeatures)
            val expressionContext = kingdom.createSimpleContext(settlements)
            val modifiers = kingdom.createModifiers(settlements)
            calculateProjectedResources(
                seasonal = game.currentSeasonalModifiers(),
                kingdomData = kingdom,
                realmData = realm,
                chosenFeats = chosenFeats,
                settlements = settlements.allSettlements,
                expressionContext = expressionContext,
                modifiers = modifiers,
            ).storageCapPreviewChanges()
        } else {
            emptyList()
        }

        cachedChanges = (tickResult.changes + storageCapChanges).toTypedArray()
        render()
    }

    private suspend fun commitTurn() {
        kingdomActor.getKingdom() ?: return
        performEndTurn(game, kingdomActor)
        kingdomActor.unsetAppFlag("turn-wizard-state")
        close().await()
    }

    private suspend fun cancel() {
        kingdomActor.unsetAppFlag("turn-wizard-state")
        close().await()
    }

    companion object {
        // Ordered turn sequence used when strict phase gating is enabled. Upkeep steps come
        // first, then one boundary item per activity phase, then the event step.
        val strictChecklistSequence = listOf(
            "gain-fame", "adjust-unrest", "collect-resources", "pay-consumption",
            "leadership-phase", "civic-phase", "region-phase", "commerce-phase", "army-phase",
            "check-events",
        )

        /**
         * Pure transition for toggling a checklist [id] given the current [checklist] and whether
         * strict gating is active. Behaviour:
         *  - non-strict: plain add/remove.
         *  - strict check: only succeeds when every preceding sequence item is already checked.
         *  - strict uncheck: removes the item and every downstream sequence item.
         *  - ids not in the sequence fall back to plain add/remove.
         * Existing checklist order is preserved.
         */
        fun applyChecklistToggle(checklist: List<String>, id: String, isStrict: Boolean): List<String> {
            val present = id in checklist
            val idx = strictChecklistSequence.indexOf(id)
            if (!isStrict || idx == -1) {
                return if (present) checklist.filter { it != id } else checklist + id
            }
            return if (present) {
                val allowed = strictChecklistSequence.take(idx).toSet()
                checklist.filter { it in allowed }
            } else {
                val allPreviousChecked = strictChecklistSequence.take(idx).all { it in checklist }
                if (allPreviousChecked) checklist + id else checklist
            }
        }

        fun buildContext(
            kingdom: KingdomData,
            actor: KingdomActor? = null,
            partId: String = "",
            isFormValid: Boolean = true,
            checkedItems: Set<String> = emptySet(),
            showPreview: Boolean = false,
            previewChanges: Array<TickChangeContext> = emptyArray(),
        ): TurnWizardContext {
            class ChecklistItemInfo(
                val id: String,
                val label: String,
                val highlight: Boolean,
                val isAttention: Boolean = false,
                val count: Int = 0,
                val icon: String = "",
            )
            val isStrict = kingdom.settings.enableStrictPhaseGating == true

            val allItems = mutableListOf(
                ChecklistItemInfo("gain-fame", t("kingdom.turnWizard.checklist.gainFame"), false),
                ChecklistItemInfo("adjust-unrest", t("kingdom.turnWizard.checklist.adjustUnrest"), kingdom.unrest > 0),
                ChecklistItemInfo("collect-resources", t("kingdom.turnWizard.checklist.collectResources"), false),
                ChecklistItemInfo("pay-consumption", t("kingdom.turnWizard.checklist.payConsumption"), false)
            )

            if (isStrict) {
                allItems.addAll(listOf(
                    ChecklistItemInfo("leadership-phase", t("kingdom.turnWizard.checklist.leadershipPhase"), false),
                    ChecklistItemInfo("civic-phase", t("kingdom.turnWizard.checklist.civicPhase"), false),
                    ChecklistItemInfo("region-phase", t("kingdom.turnWizard.checklist.regionPhase"), false),
                    ChecklistItemInfo("commerce-phase", t("kingdom.turnWizard.checklist.commercePhase"), false),
                    ChecklistItemInfo("army-phase", t("kingdom.turnWizard.checklist.armyPhase"), false)
                ))
            }

            allItems.add(ChecklistItemInfo("check-events", t("kingdom.turnWizard.checklist.checkEvents"), false))

            // Attention rows (read-only, highlight when count > 0)
            val questsFailing = countQuestsFailingThisTurn(
                (kingdom.campaignQuests ?: emptyArray()).map { q ->
                    (q.status as? String ?: "") to (q.turnsRemaining as? Int)
                }
            )
            val warThreatsMax = countWarThreatsAtMaxEscalation(
                (kingdom.warThreats ?: emptyArray()).map { t ->
                    ((t.escalationLevel as? Int) ?: 0) to ((t.maxEscalation as? Int) ?: 3)
                }
            )
            val expeditionsAwaiting = countExpeditionsAwaitingResolution(
                (kingdom.companionExpeditions ?: emptyArray()).map { e ->
                    e.status as? String ?: ""
                }
            )
            val companionsInjured = countInjuredCompanions(
                (kingdom.companions ?: emptyArray()).map { c ->
                    c.injuryDaysRemaining as? Int
                }
            )

            allItems.addAll(listOf(
                ChecklistItemInfo("attention-quests-failing", t("kingdom.turnWizard.checklist.questsFailing"), questsFailing > 0, isAttention = true, count = questsFailing, icon = "fa-solid fa-scroll"),
                ChecklistItemInfo("attention-war-threats-max", t("kingdom.turnWizard.checklist.warThreatsMax"), warThreatsMax > 0, isAttention = true, count = warThreatsMax, icon = "fa-solid fa-skull-crossbones"),
                ChecklistItemInfo("attention-expeditions-awaiting", t("kingdom.turnWizard.checklist.expeditionsAwaiting"), expeditionsAwaiting > 0, isAttention = true, count = expeditionsAwaiting, icon = "fa-solid fa-compass"),
                ChecklistItemInfo("attention-companions-injured", t("kingdom.turnWizard.checklist.companionsInjured"), companionsInjured > 0, isAttention = true, count = companionsInjured, icon = "fa-solid fa-user-injured"),
            ))

            val shownSequence = allItems.map { it.id }
            val rawChecklist = mutableListOf<ChecklistItemContext>()

            for (idx in allItems.indices) {
                val item = allItems[idx]
                val checked = item.id in checkedItems
                val disabled = isStrict && idx > 0 && shownSequence.take(idx).any { it !in checkedItems } || item.isAttention
                rawChecklist.add(
                    ChecklistItemContext(
                        id = item.id,
                        label = item.label,
                        description = "",
                        checked = checked,
                        highlight = item.highlight,
                        disabled = disabled,
                        isAttention = item.isAttention,
                        count = item.count,
                        icon = item.icon,
                    )
                )
            }
            val checklist = rawChecklist.toTypedArray()

            val commodities = kingdom.commodities
            
            var foodCap = 0
            var lumberCap = 0
            var luxuriesCap = 0
            var oreCap = 0
            var stoneCap = 0
            val ruinContextArray = mutableListOf<RuinContext>()
            
            if (actor != null) {
                try {
                    val realm = game.getRealmData(actor, kingdom)
                    val settlements = kingdom.getAllSettlements(game)
                    val storage = calculateStorage(realm, settlements.allSettlements)
                    foodCap = storage.food
                    lumberCap = storage.lumber
                    luxuriesCap = storage.luxuries
                    oreCap = storage.ore
                    stoneCap = storage.stone
                    
                    val parseRuins = kingdom.parseRuins(
                        choices = emptyList<ChosenFeature>(),
                        baseThreshold = kingdom.settings.ruinThreshold,
                        government = kingdom.government,
                    )
                    val contextRuins = kingdom.ruin.toContext(kingdom.settings.automateStats, parseRuins)
                    ruinContextArray.addAll(contextRuins)
                } catch (e: Throwable) {
                    // Ignore errors during unit test environments
                }
            }

            val stateContext = KingdomStateContext(
                rpNow = kingdom.resourcePoints.now,
                rpNext = kingdom.resourcePoints.next,
                foodNow = commodities.now.food,
                foodCap = foodCap,
                lumberNow = commodities.now.lumber,
                lumberCap = lumberCap,
                luxuriesNow = commodities.now.luxuries,
                luxuriesCap = luxuriesCap,
                oreNow = commodities.now.ore,
                oreCap = oreCap,
                stoneNow = commodities.now.stone,
                stoneCap = stoneCap,
                consumption = kingdom.consumption.now,
                unrest = kingdom.unrest,
                ruin = ruinContextArray.toTypedArray(),
                activeModifiers = kingdom.modifiers.size
            )

            // Resolve the configured per-PC-leader activity allotment; fall back to RAW defaults
            // (2, or 3 with a Town Hall/Castle/Palace) when the game settings aren't available
            // (e.g. unit test environments). The total cap scales with the number of PC leaders.
            var leadershipCap = 2
            var leadershipCapWithTownhall = 3
            try {
                val leadershipSettings = game.settings.pfrpg2eKingdomCampingWeather
                leadershipCap = leadershipSettings.getLeadershipActivityCap()
                leadershipCapWithTownhall = leadershipSettings.getLeadershipActivityCapWithTownhall()
            } catch (e: Throwable) {
                // Ignore errors during unit test environments
            }
            val capsResult = ActivityCapCalculator.calculate(
                kingdom,
                emptyMap(),
                leadershipCap = leadershipCap,
                leadershipCapWithTownhall = leadershipCapWithTownhall,
            )
            val activityCaps = capsResult.caps.map { cap ->
                ActivityCapContext(
                    phase = cap.phase,
                    phaseLabel = t("kingdom.${cap.phase}"),
                    current = cap.current,
                    maximum = cap.maximum,
                    isOverCap = cap.isOverCap
                )
            }.toTypedArray()

            val canCommit = !capsResult.hasAnyOverCap

            // GM-only advisory strip (plan SS4.2): reads each player User's own readiness flag.
            // Data-level gate -- players get null, so the template cannot leak it. runCatching
            // because unit-test environments have no game.users registry.
            val readiness: ReadinessStripContext? = runCatching {
                if (!game.user.isGM) return@runCatching null
                val players = game.users.filter { !it.isGM }
                val playerIds = players.mapNotNull { it.id }
                if (playerIds.isEmpty()) return@runCatching null
                val records = players.mapNotNull { u ->
                    val id = u.id ?: return@mapNotNull null
                    u.pingsReadyForTurn()?.let { TurnReadiness(userId = id, turn = it, ready = true) }
                }
                val strip = readinessStrip(records, kingdom.currentTurn ?: 0, playerIds)
                val waiting = players
                    .filter { strip[it.id] != true }
                    .joinToString(", ") { it.name }
                ReadinessStripContext(
                    ready = strip.values.count { it },
                    total = playerIds.size,
                    waitingNames = waiting,
                    allReady = waiting.isEmpty(),
                )
            }.getOrNull()

            return TurnWizardContext(
                readiness = readiness,
                partId = partId,
                isFormValid = isFormValid,
                kingdomName = kingdom.name,
                checklist = checklist,
                kingdomState = stateContext,
                activityCaps = activityCaps,
                previewChanges = previewChanges,
                showPreview = showPreview,
                canCommit = canCommit,
                hasUndoSnapshot = actor?.getAppFlag<KingdomActor, Any>("lastTurnSnapshot") != null,
                snapshotTurn = actor?.getAppFlag<KingdomActor, Any?>("lastTurnSnapshot")
                    ?.unsafeCast<EndTurnSnapshot>()?.snapshotTurn ?: 0,
                actorUuid = actor?.uuid ?: "",
                isGM = runCatching { game.user.isGM }.getOrDefault(false),
            )
        }
        
        fun buildContextWithPreview(kingdom: KingdomData): TurnWizardContext {
            val dummyChange = TickChange("resourcePoints", "now", 10, 15)
            val changesContext = arrayOf(
                TickChangeContext(
                    category = dummyChange.category,
                    field = dummyChange.field,
                    displayText = dummyChange.toDisplayString()
                )
            )
            return buildContext(
                kingdom = kingdom,
                actor = null,
                partId = "",
                isFormValid = true,
                checkedItems = emptySet(),
                showPreview = true,
                previewChanges = changesContext
            )
        }
    }
}

/**
 * Posts a GM-only offer card for a quest that has reached its deadline.
 * Follows the km-offer-* pattern: buttons with data-* attributes, handled in ChatButtons.kt.
 */
suspend fun postQuestDeadlineOffer(game: Game, actor: KingdomActor, quest: dynamic) {
    val title = quest.title as String
    val questId = quest.id as String

    // The template localizes in place and gates its buttons on isGM, so the context must supply
    // exactly what it READS -- name, questId, extendTurns, actorUuid, isGM. It previously supplied
    // a different set (pre-localized title/body/labels the template never reads, and neither
    // isGM nor actorUuid), which shipped this card with a blank quest name and NO buttons at all.
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    // An EMPTY whisper array posts PUBLICLY rather than to nobody, and a quest deadline is GM
    // information -- so no GM connected means no card, exactly like the other offer posters.
    if (gmUserIds.isEmpty()) return

    val context = js("{}")
    context.name = title
    context.questId = questId
    context.extendTurns = DEFAULT_QUEST_EXTEND_TURNS
    context.actorUuid = actor.uuid
    context.isGM = true

    postChatTemplate(
        templatePath = "chatmessages/quest-deadline-offer.hbs",
        templateContext = context,
        whisper = gmUserIds,
    )
}
