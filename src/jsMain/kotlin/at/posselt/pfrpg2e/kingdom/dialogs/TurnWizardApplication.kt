package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.Config
import at.posselt.pfrpg2e.app.forms.SimpleApp
import com.foundryvtt.core.abstract.DatabaseUpdateOperation
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.clearPerformedActivities
import at.posselt.pfrpg2e.kingdom.getPerformedActivities
import at.posselt.pfrpg2e.kingdom.getActivity
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
import at.posselt.pfrpg2e.kingdom.caravanRdPerCommodity
import at.posselt.pfrpg2e.kingdom.tickCaravans
import at.posselt.pfrpg2e.kingdom.tickShipments
import at.posselt.pfrpg2e.kingdom.ShipmentTickInput
import at.posselt.pfrpg2e.kingdom.sheet.calculateProjectedResources
import at.posselt.pfrpg2e.kingdom.data.RawCaravanShipment
import at.posselt.pfrpg2e.kingdom.data.RawExpeditionChronicleEntry
import at.posselt.pfrpg2e.kingdom.data.RawGroup
import at.posselt.pfrpg2e.kingdom.computeCaravanRoute
import at.posselt.pfrpg2e.kingdom.map.KingmakerHexGridProvider
import com.foundryvtt.kingmaker.kingmaker
import at.posselt.pfrpg2e.utils.postChatMessage
import kotlin.math.roundToInt
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
import com.foundryvtt.core.Game
import com.foundryvtt.core.game
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

fun TickChange.toDisplayString(): String {
    return when {
        category == "resourcePoints" && field == "tribute" -> {
            val params = js("{}")
            params["amount"] = newValue.toString()
            t("kingdom.turnWizard.preview.tributeRp", params.unsafeCast<com.foundryvtt.core.AnyObject>())
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

/**
 * Single source of truth for assembling [TurnTickingEngine.tick] arguments from a kingdom
 * snapshot. Both the End Turn commit path ([performEndTurn]) and the Turn Wizard preview
 * MUST call this — never tick() directly — so the preview cannot drift from what
 * committing actually applies. [currentTurn] is the turn being ticked into (previous + 1).
 */
fun runKingdomTurnTick(kingdom: KingdomData, storage: CommodityStorage, currentTurn: Int): TickResult =
    TurnTickingEngine.tick(
        fame = kingdom.fame,
        resourcePoints = kingdom.resourcePoints,
        resourceDice = kingdom.resourceDice,
        consumption = kingdom.consumption,
        commodities = kingdom.commodities,
        storage = storage,
        councilCooldowns = kingdom.councilCooldowns,
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
        factionStandingDriftPerTurn = kingdom.settings.factionStandingDriftPerTurn ?: 0,
    )

suspend fun performEndTurn(game: Game, actor: KingdomActor, kingdom: KingdomData): TickResult {
    val currentTurn = (kingdom.currentTurn ?: 0) + 1
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

    val tickResult = runKingdomTurnTick(kingdom, storage, currentTurn)
    kingdom.supernaturalSolutions = tickResult.supernaturalSolutions
    kingdom.creativeSolutions = tickResult.creativeSolutions
    kingdom.fame = tickResult.fame
    kingdom.resourcePoints = tickResult.resourcePoints
    kingdom.resourceDice = tickResult.resourceDice
    kingdom.consumption = tickResult.consumption
    kingdom.commodities = tickResult.commodities
    kingdom.councilCooldowns = tickResult.councilCooldowns
    kingdom.modifiers = tickResult.modifiers
    kingdom.campaignQuests = tickResult.campaignQuests
    kingdom.warThreats = tickResult.warThreats
    kingdom.armyDeployments = tickResult.armyDeployments
    kingdom.warPressure = tickResult.warPressure
    kingdom.bonusResourceDice = tickResult.bonusResourceDice
    kingdom.activeBattles = tickResult.activeBattles
    kingdom.groups = tickResult.groups

    // Post GM offer cards for quests that hit their deadline this turn
    if (tickResult.questDeadlineReached.isNotEmpty()) {
        tickResult.questDeadlineReached.forEach { questId ->
            val quest = kingdom.campaignQuests.find { it.id == questId }
            if (quest != null) {
                postQuestDeadlineOffer(game, actor, quest)
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
                val claimedFraction = if (route != null && route.path.isNotEmpty()) {
                    val path = route.path
                    val safeCount = path.count { key ->
                        val hs = kingmaker.state.hexes[key]
                        hs?.claimed == true || hs?.cleared == true
                    }
                    safeCount.toDouble() / path.size
                } else {
                    0.0
                }
                CaravanTickInput(
                    caravan = caravan,
                    raidDc = caravanRaidDc(
                        baseDc = CARAVAN_BASE_RAID_DC,
                        partnerStanding = partner?.standing,
                        atWar = partner?.atWar == true,
                        claimedFraction = claimedFraction,
                    ),
                    raidRoll = kotlin.random.Random.nextInt(1, 21),
                    rdPerCommodity = caravanRdPerCommodity(partner?.standing, partner?.allianceLevel),
                )
            }
        )
        kingdom.caravans = caravanResult.remaining.toTypedArray()
        caravanEvents = caravanResult.events
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
        if (caravanLines.isNotEmpty()) {
            val body = caravanLines.joinToString("") { "<li>$it</li>" }
            postChatMessage("<h3>${t("kingdom.caravans.title")}</h3><ul>$body</ul>", isHtml = true)
        }
    }

    // Caravan shipments: tick active/in-transit shipments en route.
    val inTransitShipments = (kingdom.shipments ?: emptyArray()).filter { it.status == "inTransit" }
    if (inTransitShipments.isNotEmpty()) {
        val claimedHexes = runCatching {
            kingmaker.state.hexes.asSequence()
                .filter { (_, hex) -> hex.claimed == true }
                .map { (key, _) -> key }
                .toSet()
        }.getOrDefault(emptySet())

        val shipmentResult = tickShipments(
            inTransitShipments.map { shipment ->
                val routeHexes = shipment.path
                val claimedCount = routeHexes.count { it in claimedHexes }
                val claimedFraction = if (routeHexes.isEmpty()) 1.0 else claimedCount.toDouble() / routeHexes.size
                val raidDc = (CARAVAN_BASE_RAID_DC - (claimedFraction * 4).roundToInt()).coerceAtLeast(5)
                ShipmentTickInput(
                    shipment = shipment,
                    raidDc = raidDc,
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
        }

        // Save remaining in-transit shipments
        kingdom.shipments = shipmentResult.remaining.toTypedArray()
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
            val maxItemAccess = settlements.allSettlements.maxOfOrNull { it.itemPurchaseLevel }
            if (maxItemAccess != null) {
                val lootTrack = trackLootImbalance(
                    itemAccessLevel = maxItemAccess,
                    partyLevel = targetLevel,
                    range = kingdom.settings.pacingLootImbalanceRange(),
                    previousSeverity = kingdom.pacingLastLootImbalance,
                    turn = currentTurn,
                )
                kingdom.pacingLastLootImbalance = lootTrack.severity
                lootTrack.alert?.let { firedPacingAlerts.add(it) }
            }
        }
    }

    if (firedPacingAlerts.isNotEmpty()) {
        kingdom.pacingAlerts = (kingdom.pacingAlerts ?: emptyArray()) + firedPacingAlerts.toTypedArray()
    }

    // Per-turn history record (gap analysis item 2): snapshot post-tick kingdom state.
    val clockEventNames = tickResult.clockEvents.map { it.label }.toTypedArray()
    val tributeRp = tickResult.changes.find { it.category == "resourcePoints" && it.field == "tribute" }?.newValue as? Int ?: 0
    val turnNotes = formatTurnGazette(
        activities = activitySummaries,
        sizeChange = sizeChange,
        currentSize = realm.size,
        caravanEvents = caravanEvents,
        shipmentEvents = shipmentEvents,
        campaignClocks = tickResult.clockEvents.map { it.label },
        tributeRp = tributeRp,
        expeditionChronicle = kingdom.expeditionChronicle?.toList() ?: emptyList(),
        turn = currentTurn,
    )

    val warPressureNow = kingdom.warPressure?.currentPressure
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
            level = kingdom.level,
            size = realm.size,
            ruinCorruption = kingdom.ruin.corruption.value,
            ruinCrime = kingdom.ruin.crime.value,
            ruinDecay = kingdom.ruin.decay.value,
            ruinStrife = kingdom.ruin.strife.value,
            notes = turnNotes,
        ),
    )

    val automateResources = kingdom.settings.automateResources != "manual"
    if (automateResources) {
        val settlements = kingdom.getAllSettlements(game)
        val allFeatures = kingdom.getExplodedFeatures()
        val chosenFeatures = kingdom.getChosenFeatures(allFeatures)
        val chosenFeats = kingdom.getChosenFeats(chosenFeatures)
        val expressionContext = kingdom.createSimpleContext(settlements)
        val modifiers = kingdom.createModifiers(settlements)

        val projected = calculateProjectedResources(
            kingdomData = kingdom,
            realmData = realm,
            chosenFeats = chosenFeats,
            settlements = settlements.allSettlements,
            expressionContext = expressionContext,
            modifiers = modifiers,
        )
        kingdom.commodities.next.ore = projected.ore
        kingdom.commodities.next.stone = projected.stone
        kingdom.commodities.next.lumber = projected.lumber
        kingdom.commodities.next.luxuries = projected.luxuries
        kingdom.commodities.next.food = 0
        kingdom.resourceDice.next = projected.resourceDice
    }

    actor.setKingdom(kingdom)

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
        for (threat in tickResult.newlyTriggeredThreats) {
            // Find linked hex content for the "Queue encounter" button
            val hasLinkedHex = kingdom.hexContents?.any { it.linkedWarThreatId == threat.id } == true
            val offerContext = js("{}")
            offerContext.threatId = threat.id
            offerContext.threatName = threat.name
            offerContext.actorUuid = actor.uuid
            offerContext.hasLinkedHex = hasLinkedHex
            offerContext.isGM = game.user.isGM
            postChatTemplate(
                templatePath = "chatmessages/war-threat-arrival-offer.hbs",
                templateContext = offerContext,
            )
        }
    }

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

    val changesText = tickResult.changes
        .map { it.toDisplayString() }
        .joinToString("\n") { "- $it" }
    logToCalendar(
        title = "Kingdom Turn $currentTurn Complete",
        content = "Kingdom Turn $currentTurn completed.\n\nChanges:\n$changesText"
    )

    return tickResult
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
            
        htmlElement.querySelector("button[data-action='commit-turn']")
            ?.addEventListener("click", {
                buildPromise {
                    commitTurn()
                }
            })
            
        htmlElement.querySelector("button[data-action='cancel']")
            ?.addEventListener("click", {
                buildPromise {
                    cancel()
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
        val tickResult = runKingdomTurnTick(kingdom, storage, (kingdom.currentTurn ?: 0) + 1)
        
        cachedChanges = tickResult.changes.toTypedArray()
        render()
    }

    private suspend fun commitTurn() {
        val kingdom = kingdomActor.getKingdom() ?: return
        performEndTurn(game, kingdomActor, kingdom)
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

            return TurnWizardContext(
                partId = partId,
                isFormValid = isFormValid,
                kingdomName = kingdom.name,
                checklist = checklist,
                kingdomState = stateContext,
                activityCaps = activityCaps,
                previewChanges = previewChanges,
                showPreview = showPreview,
                canCommit = canCommit
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

    val context = js("{}")
    context.title = t("chatMessages.questDeadline.offerTitle")
    context.body = t("chatMessages.questDeadline.offerBody", recordOf("name" to title))
    context.questId = questId
    context.extendTurns = DEFAULT_QUEST_EXTEND_TURNS
    context.failNowLabel = t("chatMessages.questDeadline.failNow")
    context.extendLabel = t("chatMessages.questDeadline.extendTurns", recordOf("turns" to DEFAULT_QUEST_EXTEND_TURNS.toString()))

    postChatTemplate(
        templatePath = "chatmessages/quest-deadline-offer.hbs",
        templateContext = context,
    )
}
