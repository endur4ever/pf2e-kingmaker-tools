package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.postHoldingDamageOffer
import at.posselt.pfrpg2e.kingdom.holdingsAt
import at.posselt.pfrpg2e.data.kingdom.HOLDING_DAMAGING_EVENT_IDS
import at.posselt.pfrpg2e.kingdom.conditionEnum
import at.posselt.pfrpg2e.kingdom.applyHoldingDamage
import at.posselt.pfrpg2e.data.kingdom.repairedCondition
import at.posselt.pfrpg2e.data.kingdom.DamageSeverity
import at.posselt.pfrpg2e.kingdom.data.RawPersonalHolding
import at.posselt.pfrpg2e.kingdom.data.RawPcRenown
import at.posselt.pfrpg2e.actions.ActionMessage
import at.posselt.pfrpg2e.actions.handlers.CastCouncilVoteData
import at.posselt.pfrpg2e.actions.handlers.CouncilVoteLifecycleData
import at.posselt.pfrpg2e.data.kingdom.ABSTAIN_OPTION
import at.posselt.pfrpg2e.data.kingdom.RIVAL_STANDING_SHIFT_DELTA
import at.posselt.pfrpg2e.data.kingdom.applyStandingDelta
import at.posselt.pfrpg2e.kingdom.data.RawFactionStandingEntry
import at.posselt.pfrpg2e.kingdom.data.RawGroup
import at.posselt.pfrpg2e.kingdom.data.RawRivalRealm
import at.posselt.pfrpg2e.companion.LevelUpResult
import at.posselt.pfrpg2e.companion.applyCompanionXp
import at.posselt.pfrpg2e.data.armies.BattleArmyState
import at.posselt.pfrpg2e.data.armies.applyLevelUp
import at.posselt.pfrpg2e.data.armies.xpThresholdForLevel
import at.posselt.pfrpg2e.utils.fromUuidOfTypes
import at.posselt.pfrpg2e.data.actor.isValuedCondition
import com.foundryvtt.pf2e.actor.PF2EArmy
import com.foundryvtt.pf2e.actor.PF2ECharacter
import com.foundryvtt.pf2e.actor.PF2ENpc
import kotlin.math.min
import at.posselt.pfrpg2e.data.events.KingdomEventTrait
import at.posselt.pfrpg2e.kingdom.dialogs.AddExpeditionDialog
import at.posselt.pfrpg2e.kingdom.data.RawQuest
import at.posselt.pfrpg2e.kingdom.data.RawQuestRewards
import at.posselt.pfrpg2e.kingdom.rival.RivalPartyMove
import at.posselt.pfrpg2e.kingdom.rival.applyRivalColocation
import at.posselt.pfrpg2e.kingdom.rival.applyRivalDiscovery
import at.posselt.pfrpg2e.kingdom.rival.postRivalCharterDigest
import at.posselt.pfrpg2e.kingdom.rival.rivalLifecycleStatus
import at.posselt.pfrpg2e.kingdom.data.effectiveLevel
import at.posselt.pfrpg2e.data.kingdom.RIVAL_STATUS_DEFECTED
import at.posselt.pfrpg2e.data.kingdom.RIVAL_STATUS_JOINED
import at.posselt.pfrpg2e.data.kingdom.RIVAL_STATUS_RETIRED
import at.posselt.pfrpg2e.camping.Rumor
import at.posselt.pfrpg2e.camping.currentWorldDay
import at.posselt.pfrpg2e.camping.updateRumors
import at.posselt.pfrpg2e.actor.partyMembers
import at.posselt.pfrpg2e.kingdom.rival.mergeRivalFormFields
import at.posselt.pfrpg2e.kingdom.dialogs.ModifyRivalCharterParty
import at.posselt.pfrpg2e.kingdom.dialogs.AddQuest
import at.posselt.pfrpg2e.kingdom.applyPetitionAnswer
import at.posselt.pfrpg2e.kingdom.SPRING_FLOOD_EVENT_ID
import at.posselt.pfrpg2e.kingdom.lifeEventGazetteLine
import at.posselt.pfrpg2e.kingdom.applyPetitionOverdue
import at.posselt.pfrpg2e.kingdom.dialogs.AddWarThreat
import at.posselt.pfrpg2e.kingdom.launchExpedition
import at.posselt.pfrpg2e.kingdom.buildExpeditionDestinationOptions
import at.posselt.pfrpg2e.kingdom.logExpeditionLaunched
import at.posselt.pfrpg2e.settings.Pfrpg2eKingdomCampingWeatherSettings
import at.posselt.pfrpg2e.kingdom.dialogs.pickEventSettlement
import at.posselt.pfrpg2e.kingdom.dialogs.pickLeader
import at.posselt.pfrpg2e.kingdom.sheet.executeResourceButton
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import at.posselt.pfrpg2e.kingdom.structures.StructureActor
import at.posselt.pfrpg2e.kingdom.structures.validateUsingSchema
import at.posselt.pfrpg2e.kingdom.data.EndTurnSnapshot
import at.posselt.pfrpg2e.kingdom.data.MilestoneChoice
import at.posselt.pfrpg2e.kingdom.sheet.beforeKingdomUpdate
import at.posselt.pfrpg2e.kingdom.dialogs.undoEndTurn
import at.posselt.pfrpg2e.takeIfInstance
import at.posselt.pfrpg2e.utils.bindChatClick
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.deserializeB64Json
import at.posselt.pfrpg2e.utils.launch
import at.posselt.pfrpg2e.utils.getAppFlag
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import kotlinx.coroutines.await
import com.foundryvtt.core.AnyObject
import at.posselt.pfrpg2e.utils.roll
import at.posselt.pfrpg2e.utils.d20Check
import at.posselt.pfrpg2e.utils.unsetAppFlag
import at.posselt.pfrpg2e.utils.typeSafeUpdate
import at.posselt.pfrpg2e.utils.setAppFlag
import com.foundryvtt.core.Game
import com.foundryvtt.core.helpers.TypedHooks
import com.foundryvtt.core.helpers.onRenderChatLog
import com.foundryvtt.core.ui
import com.foundryvtt.core.utils.deepClone
import io.github.uuidjs.uuid.v4
import js.array.tupleOf
import at.posselt.pfrpg2e.app.awaitablePrompt
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.kingdom.data.RawSubsystemThreshold
import at.posselt.pfrpg2e.kingdom.councilVoteMutex
import at.posselt.pfrpg2e.kingdom.xp.xpLedgerActor
import at.posselt.pfrpg2e.kingdom.xp.xpLedger
import at.posselt.pfrpg2e.kingdom.xp.updateXpLedger
import at.posselt.pfrpg2e.kingdom.xp.answerEntry
import at.posselt.pfrpg2e.kingdom.xp.XpOfferStatus
import at.posselt.pfrpg2e.macros.updateXP
import at.posselt.pfrpg2e.actor.partyMembers
import at.posselt.pfrpg2e.kingdom.xp.proposeXpOffer
import kotlinx.coroutines.sync.withLock
import js.objects.recordOf
import kotlinx.html.org.w3c.dom.events.Event
import kotlinx.js.JsPlainObject
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.get
import at.posselt.pfrpg2e.kingdom.dialogs.postComplexDegreeOfSuccess
import at.posselt.pfrpg2e.takeIfInstance
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.kingdom.PLAGUE_EVENT_ID
import at.posselt.pfrpg2e.actions.ActionDispatcher
import at.posselt.pfrpg2e.kingdom.pings.savePingsReady
import at.posselt.pfrpg2e.kingdom.sheet.KingdomSheet
import at.posselt.pfrpg2e.kingdom.sheet.navigation.MainNavEntry
import at.posselt.pfrpg2e.kingdom.loot.awardLootManifest
import at.posselt.pfrpg2e.kingdom.loot.dismissLootManifest
import at.posselt.pfrpg2e.kingdom.CleanseItemSettlement
import at.posselt.pfrpg2e.kingdom.dialogs.openCleanseItemDialog
import com.foundryvtt.pf2e.item.PF2EItem
import at.posselt.pfrpg2e.kingdom.mapdynamism.hexDisplayLabel
import at.posselt.pfrpg2e.kingdom.mapdynamism.kingmakerNeighbors
import at.posselt.pfrpg2e.kingdom.mapdynamism.nextThreatHex
import at.posselt.pfrpg2e.camping.downtimeRollPrompt
import at.posselt.pfrpg2e.camping.rollRandomEncounter
import at.posselt.pfrpg2e.camping.getCampingActors
import at.posselt.pfrpg2e.kingdom.downtime.DowntimeKind
import at.posselt.pfrpg2e.kingdom.pressure.confirmPressureFiring
import at.posselt.pfrpg2e.kingdom.pressure.dismissPressureFiring
import at.posselt.pfrpg2e.kingdom.pressure.pendingPressureRows

private data class ChatButton(
    val buttonClass: String,
    val callback: suspend (game: Game, actor: KingdomActor, event: Event, button: HTMLElement) -> Unit,
)

@Suppress("unused")
@JsPlainObject
external interface PayStructureContext {
    val rp: Int
    val lumber: Int
    val luxuries: Int
    val stone: Int
    val ore: Int
}

private val buttons = listOf(
    ChatButton("km-pay-structure") { game, actor, event, button ->
        val rp = button.dataset["rp"]?.toInt() ?: 0
        val lumber = button.dataset["lumber"]?.toInt() ?: 0
        val luxuries = button.dataset["luxuries"]?.toInt() ?: 0
        val stone = button.dataset["stone"]?.toInt() ?: 0
        val ore = button.dataset["ore"]?.toInt() ?: 0
        actor.getKingdom()?.let { kingdom ->
            kingdom.commodities.now.luxuries = (kingdom.commodities.now.luxuries - luxuries).coerceIn(0, Int.MAX_VALUE)
            kingdom.commodities.now.lumber = (kingdom.commodities.now.lumber - lumber).coerceIn(0, Int.MAX_VALUE)
            kingdom.commodities.now.stone = (kingdom.commodities.now.stone - stone).coerceIn(0, Int.MAX_VALUE)
            kingdom.commodities.now.ore = (kingdom.commodities.now.ore - ore).coerceIn(0, Int.MAX_VALUE)
            kingdom.resourcePoints.now = (kingdom.resourcePoints.now - rp).coerceIn(0, Int.MAX_VALUE)
            actor.setKingdom(kingdom)
            postChatTemplate(
                templatePath = "chatmessages/paid-structure.hbs",
                templateContext = PayStructureContext(
                    rp = rp,
                    lumber = lumber,
                    luxuries = luxuries,
                    stone = stone,
                    ore = ore,
                )
            )
        }
    },
    ChatButton("km-cleanse-pay") { _, actor, _, button ->
        val luxuries = button.dataset["luxuries"]?.toInt() ?: 0
        actor.getKingdom()?.let { kingdom ->
            kingdom.commodities.now.luxuries = (kingdom.commodities.now.luxuries - luxuries)
                .coerceIn(0, Int.MAX_VALUE)
            actor.setKingdom(kingdom)
            postChatMessage(t("activities.cleanse-item.paid", recordOf("luxuries" to luxuries)))
        }
    },
    ChatButton("km-gain-lose") { game, actor, event, button ->
        val activityId = button.closest(".chat-message")
            ?.querySelector(".km-upgrade-result")
            ?.takeIfInstance<HTMLElement>()
            ?.dataset["activityId"]
        actor.getKingdom()?.let { kingdom ->
            executeResourceButton(
                game = game,
                actor = actor,
                kingdom = kingdom,
                elem = button,
                activityId = activityId,
            )
        }

    },
    ChatButton("km-gain-fame-button") { game, actor, event, button ->
        actor.getKingdom()?.let { kingdom ->
            kingdom.fame.now = (kingdom.fame.now + 1).coerceIn(0, kingdom.settings.maximumFamePoints)
            postChatMessage(t("kingdom.gaining1Fame"))
            actor.setKingdom(kingdom)
        }
    },
    ChatButton("km-resolve-event") { game, actor, event, button ->
        actor.getKingdom()?.let { kingdom ->
            val eventIndex = button.dataset["eventIndex"]?.toInt()!!
            val eventId = button.dataset["eventId"]!!
            val event = kingdom.getOngoingEvents()
                .getOrNull(eventIndex)
                ?.takeIf { it.event.id == eventId }
            checkNotNull(event) {
                "Could not find event with index $eventIndex"
            }
            postChatMessage(t("kingdom.resolvedEvent", recordOf("name" to event.event.name)))
            val resolvedSceneId = event.settlementSceneId
            kingdom.ongoingEvents = kingdom.ongoingEvents
                .filterIndexed { index, _ -> index != eventIndex }
                .toTypedArray()
            actor.setKingdom(kingdom)
            // Damage hook #3: an id in HOLDING_DAMAGING_EVENT_IDS strikes the holdings at the
            // event's settlement -- or, when the event carries no location, EVERY holding (the
            // plan's explicit fallback; most entries in the map are location-less events, so a
            // location test alone would silently skip them). The id set is the selector.
            HOLDING_DAMAGING_EVENT_IDS[eventId]?.let { severity ->
                val struck = if (resolvedSceneId != null) {
                    holdingsAt(kingdom.personalHoldings, hexKey = null, sceneId = resolvedSceneId)
                } else {
                    (kingdom.personalHoldings ?: emptyArray()).toList()
                }
                for (holding in struck) {
                    postHoldingDamageOffer(
                        game = game,
                        actorUuid = actor.uuid,
                        holding = holding,
                        severity = severity,
                        cause = event.event.name,
                    )
                }
            }
        }
    },
    ChatButton("km-set-structure-hp") { game, actor, event, button ->
        val selected = game.canvas.tokens.controlled
            .mapNotNull { it.actor }
            .filterIsInstance<StructureActor>()
        val first = selected.firstOrNull()
        val hp = button.dataset["hp"]?.toInt() ?: 0
        if (first == null) {
            ui.notifications.error(t("kingdom.selectAtLeastOneStructure"))
        } else {
            first.typeSafeUpdate {
                system.attributes.hp.value = hp
            }
            postChatMessage(t("kingdom.setStructureHp", recordOf("hp" to hp)))
        }
    },
    ChatButton("km-add-ongoing-event") { game, actor, event, button ->
        val id = button.dataset["eventId"]
        if (id != null) {
            actor.getKingdom()?.let { kingdom ->
                val event = kingdom.getEvent(id)
                if (event != null) {
                    val ongoingEvent = if (KingdomEventTrait.SETTLEMENT.value in event.traits) {
                        val settlements = kingdom.getAllSettlements(game).allSettlements
                        val pick = pickEventSettlement(settlements)
                        RawOngoingKingdomEvent(
                            stage = 0,
                            id = id,
                            settlementSceneId = pick.settlementId,
                            secretLocation = pick.secretLocation,
                        )
                    } else {
                        RawOngoingKingdomEvent(
                            stage = 0,
                            id = id,
                        )
                    }
                    kingdom.ongoingEvents = kingdom.ongoingEvents + ongoingEvent
                    actor.setKingdom(kingdom)
                }
            }
        }
    },
    ChatButton("km-offer-xp-confirm") { game, actor, event, button ->
        // The ONLY path that grants party XP from the ledger. The amount comes from the row's
        // input, so a default that did not fit the beat costs one edit, not a wrong award --
        // and the ledger records what was granted, never merely what was proposed.
        if (!game.user.isGM) return@ChatButton
        val entryId = button.dataset["entryId"] ?: return@ChatButton
        val typed = (button.closest(".km-xp-digest-row")
            ?.querySelector("input.km-xp-amount") as? org.w3c.dom.HTMLInputElement)
            ?.value?.toIntOrNull()
        grantXpLedgerEntry(game, entryId, typed)
        markXpRowDone(button)
    },
    ChatButton("km-offer-xp-dismiss") { game, actor, event, button ->
        if (!game.user.isGM) return@ChatButton
        val entryId = button.dataset["entryId"] ?: return@ChatButton
        val party = game.xpLedgerActor() ?: return@ChatButton
        party.updateXpLedger { answerEntry(it, entryId, granted = null) }
        markXpRowDone(button)
    },
    ChatButton("km-offer-xp-confirm-all") { game, actor, event, button ->
        // A session of exploration can queue a dozen offers; confirming them one by one is how a
        // GM learns to ignore the digest. Each row keeps its own (possibly edited) amount.
        if (!game.user.isGM) return@ChatButton
        val ids = button.dataset["allIds"]?.split(",")?.filter { it.isNotBlank() } ?: return@ChatButton
        val card = button.closest(".chat-message")
        ids.forEach { id ->
            val typed = (card?.querySelector("input.km-xp-amount[data-entry-id='$id']")
                as? org.w3c.dom.HTMLInputElement)?.value?.toIntOrNull()
            grantXpLedgerEntry(game, id, typed)
        }
        markXpCardDone(button)
    },
    ChatButton("km-offer-xp-dismiss-all") { game, actor, event, button ->
        if (!game.user.isGM) return@ChatButton
        val ids = button.dataset["allIds"]?.split(",")?.filter { it.isNotBlank() } ?: return@ChatButton
        val party = game.xpLedgerActor() ?: return@ChatButton
        party.updateXpLedger { existing ->
            ids.fold(existing) { acc, id -> answerEntry(acc, id, granted = null) }
        }
        markXpCardDone(button)
    },
    ChatButton("km-offer-npc-encounter") { game, actor, event, button ->
        // NPC attitude crossing (npc-memory plan section 7): rolls a random encounter through
        // the existing camping pipeline; the card stays for the other options.
        if (!game.user.isGM) return@ChatButton
        val campingActor = game.getCampingActors().firstOrNull()
        if (campingActor == null) {
            ui.notifications.warn(t("kingdom.npcMemory.offer.noCampingActor"))
            return@ChatButton
        }
        rollRandomEncounter(game, campingActor, false)
    },
    ChatButton("km-offer-rival-reached-target") { game, actor, _, button ->
        // rival-charter 5.2: the band beat the players to a prize. Every outcome is a GM click; the
        // arrival itself was stamped by the tick, so a re-run of the turn cannot re-offer it.
        if (!game.user.isGM) return@ChatButton
        val bandId = button.dataset["bandId"] ?: return@ChatButton
        val hexKey = button.dataset["hexKey"] ?: return@ChatButton
        val kingdom = actor.getKingdom() ?: return@ChatButton
        val band = kingdom.rivalCharterParties?.find { it.id == bandId }
        if (band == null) {
            // the band was deleted or the turn undone since this card was posted
            ui.notifications.info(t("kingdom.rivalCharter.bandGone"))
            markRivalRowDone(button)
            return@ChatButton
        }
        val currentTurn = kingdom.currentTurn ?: 0
        markRivalRowDone(button)
        // the arrival this card describes must still stand: an End Turn undo restores a band that
        // never got here, and a discovery row for a reverted arrival would be fiction
        if (band.lastArrivalHexKey != hexKey) {
            ui.notifications.info(t("kingdom.rivalCharter.arrivalGone"))
            return@ChatButton
        }
        when (button.dataset["choice"]) {
            // the two outcomes where the band keeps the ground leave a discovery for the players
            "cede", "dismiss" -> {
                applyRivalDiscovery(kingdom, band, hexKey, currentTurn, kind = button.dataset["kind"])
                actor.setKingdom(kingdom)
            }
            "race" -> AddQuest(
                prefillTitle = t("kingdom.rivalCharter.raceQuestTitle", recordOf("band" to band.name, "place" to hexDisplayLabel(hexKey))),
                prefillGiver = band.name,
                settlements = kingdom.getAllSettlements(game).allSettlements.map { it.id to it.name },
            ) { quest ->
                actor.getKingdom()?.let { fresh ->
                    fresh.quests = (fresh.quests ?: emptyArray()) + quest
                    actor.setKingdom(fresh)
                }
            }.launch()
            "confront" -> {
                // escalate: the confrontation card, without waiting for aggression to cross --
                // unless this turn's digest already carries one for the same peak
                if (band.confrontationOffered == true) {
                    ui.notifications.info(t("kingdom.rivalCharter.confrontationPending"))
                    return@ChatButton
                }
                band.confrontationOffered = true
                actor.setKingdom(kingdom)
                postRivalCharterDigest(game, actor.uuid, currentTurn,
                    moves = listOf(RivalPartyMove(band.id, band.name, band.factionRef, "", emptyMap(), null, true, null, band.currentHexKey, band.levelOffset)),
                    coLocated = emptyList(), lifecycleBands = emptyList())
            }
        }
    },
    ChatButton("km-offer-rival-confrontation") { game, actor, _, button ->
        if (!game.user.isGM) return@ChatButton
        val bandId = button.dataset["bandId"] ?: return@ChatButton
        val kingdom = actor.getKingdom() ?: return@ChatButton
        val band = kingdom.rivalCharterParties?.find { it.id == bandId }
        if (band == null) {
            // the band was deleted or the turn undone since this card was posted
            ui.notifications.info(t("kingdom.rivalCharter.bandGone"))
            markRivalRowDone(button)
            return@ChatButton
        }
        val currentTurn = kingdom.currentTurn ?: 0
        markRivalRowDone(button)
        // one peak, one resolution: a second live card for the same peak finds it already spent
        if (button.dataset["choice"] != "dismiss" && band.confrontationOffered != true) {
            ui.notifications.info(t("kingdom.rivalCharter.confrontationSpent"))
            return@ChatButton
        }
        when (button.dataset["choice"]) {
            "warThreat" -> AddWarThreat(
                prefillName = t("kingdom.rivalCharter.warThreatName", recordOf("band" to band.name)),
                prefillEnemyFaction = band.factionRef,
                factions = kingdom.groups.map { it.name },
            ) { threat ->
                // the peak is spent when the threat EXISTS, not when the dialog opens: a cancelled
                // dialog leaves the confrontation unresolved rather than silently consumed
                buildPromise {
                    actor.getKingdom()?.let { fresh ->
                        fresh.warThreats = (fresh.warThreats ?: emptyArray()) + threat
                        fresh.rivalCharterParties?.find { it.id == bandId }?.let { live ->
                            live.aggression = 0
                            live.confrontationOffered = false
                        }
                        actor.setKingdom(fresh)
                    }
                }
            }.launch()
            "encounter" -> {
                // no encounter is rolled or spawned here: a pending-encounter marker at the band's
                // hex carries the level budget for the GM to stage when the table gets there
                val hexKey = band.currentHexKey
                if (hexKey == null) {
                    ui.notifications.warn(t("kingdom.rivalCharter.noPosition"))
                    return@ChatButton
                }
                band.aggression = 0
                band.confrontationOffered = false
                applyRivalColocation(kingdom, band, hexKey, currentTurn, queueEncounter = true)
                actor.setKingdom(kingdom)
                postChatMessage(t("kingdom.rivalCharter.encounterQueued", recordOf("band" to band.name, "level" to band.effectiveLevel(partyLevelFor(game)).toString())))
            }
            else -> Unit
        }
    },
    ChatButton("km-offer-rival-rumor") { game, actor, _, button ->
        if (!game.user.isGM) return@ChatButton
        val bandId = button.dataset["bandId"] ?: return@ChatButton
        val hexKey = button.dataset["hexKey"] ?: return@ChatButton
        val kingdom = actor.getKingdom() ?: return@ChatButton
        val band = kingdom.rivalCharterParties?.find { it.id == bandId }
        if (band == null) {
            // the band was deleted or the turn undone since this card was posted
            ui.notifications.info(t("kingdom.rivalCharter.bandGone"))
            markRivalRowDone(button)
            return@ChatButton
        }
        markRivalRowDone(button)
        if (button.dataset["choice"] != "plant") return@ChatButton
        val campingActor = game.getCampingActors().firstOrNull()
        if (campingActor == null) {
            ui.notifications.warn(t("kingdom.petitions.noCampingActor"))
            return@ChatButton
        }
        val place = hexDisplayLabel(hexKey)
        val rumorId = "rumor-rival-${band.id}-$hexKey"
        campingActor.updateRumors { existing ->
            // a second live copy of the card must not plant the same rumor twice
            if (existing.any { it.id == rumorId }) return@updateRumors existing
            existing + Rumor(
                text = t("kingdom.rivalCharter.rumor.sighted", recordOf("faction" to (band.factionRef ?: band.name), "place" to place)),
                location = place,
                id = rumorId,
                bornDay = currentWorldDay(game),
            )
        }
    },
    ChatButton("km-offer-rival-encounter") { game, actor, _, button ->
        if (!game.user.isGM) return@ChatButton
        val bandId = button.dataset["bandId"] ?: return@ChatButton
        val hexKey = button.dataset["hexKey"] ?: return@ChatButton
        val kingdom = actor.getKingdom() ?: return@ChatButton
        val band = kingdom.rivalCharterParties?.find { it.id == bandId }
        if (band == null) {
            // the band was deleted or the turn undone since this card was posted
            ui.notifications.info(t("kingdom.rivalCharter.bandGone"))
            markRivalRowDone(button)
            return@ChatButton
        }
        markRivalRowDone(button)
        when (button.dataset["choice"]) {
            "queue" -> { applyRivalColocation(kingdom, band, hexKey, kingdom.currentTurn ?: 0, queueEncounter = true); actor.setKingdom(kingdom) }
            "sighting" -> { applyRivalColocation(kingdom, band, hexKey, kingdom.currentTurn ?: 0, queueEncounter = false); actor.setKingdom(kingdom) }
            else -> Unit
        }
    },
    ChatButton("km-offer-rival-lifecycle") { game, actor, _, button ->
        // plan 2.4: a lifecycle change is a GM click, never something the tick writes
        if (!game.user.isGM) return@ChatButton
        val bandId = button.dataset["bandId"] ?: return@ChatButton
        val status = rivalLifecycleStatus(button.dataset["choice"]) ?: return@ChatButton
        val kingdom = actor.getKingdom() ?: return@ChatButton
        val band = kingdom.rivalCharterParties?.find { it.id == bandId }
        if (band == null) {
            // the band was deleted or the turn undone since this card was posted
            ui.notifications.info(t("kingdom.rivalCharter.bandGone"))
            markRivalRowDone(button)
            return@ChatButton
        }
        markRivalRowDone(button)
        // "Leave Be" does nothing -- in particular it must not un-defect a defected band, which is
        // still active and so still receives lifecycle rows
        if (button.dataset["choice"] == "keep" || band.status == status) return@ChatButton
        band.status = status
        // a band that stops competing has no objective; a defector starts its new charter calm
        band.objectiveHexKey = null; band.objectiveKind = null; band.distanceToObjective = null
        if (status == RIVAL_STATUS_DEFECTED) { band.aggression = 0; band.confrontationOffered = false }
        actor.setKingdom(kingdom)
        when (status) {
            RIVAL_STATUS_RETIRED -> postChatMessage(t("kingdom.rivalCharter.lifecycle.retired", recordOf("band" to band.name)))
            RIVAL_STATUS_JOINED -> postChatMessage(t("kingdom.rivalCharter.lifecycle.joined", recordOf("band" to band.name)))
            // the new banner is the GM's to pick: the line names the faction they SAVE, never the one they left
            RIVAL_STATUS_DEFECTED -> ModifyRivalCharterParty(existing = band, factions = kingdom.groups.map { it.name }) { edited ->
                buildPromise {
                    actor.getKingdom()?.let { fresh ->
                        val live = mergeRivalFormFields(fresh.rivalCharterParties?.find { it.id == edited.id }, edited)
                        if (fresh.rivalCharterParties?.none { it.id == live.id } != false) fresh.rivalCharterParties = (fresh.rivalCharterParties ?: emptyArray()) + live
                        actor.setKingdom(fresh)
                        postChatMessage(t("kingdom.rivalCharter.lifecycle.defected", recordOf("band" to live.name, "faction" to (live.factionRef ?: live.name))))
                    }
                }
            }.launch()
            else -> Unit
        }
    },
    ChatButton("km-offer-seasonal-flood") { game, actor, _, button ->
        // seasonal-economy 5.1: spawn the spring-flood kingdom event. Idempotent through the
        // ongoing-event list rather than the year marker -- the marker is stamped when the card is
        // POSTED (once per spring), so it cannot also be the guard for the click
        if (!game.user.isGM) return@ChatButton
        val kingdom = actor.getKingdom() ?: return@ChatButton
        if (kingdom.ongoingEvents.any { it.id == SPRING_FLOOD_EVENT_ID }) {
            ui.notifications.info(t("kingdom.seasonalEconomy.flood.alreadyTriggered"))
            markPetitionCardDone(button)
            return@ChatButton
        }
        if (kingdom.getEvent(SPRING_FLOOD_EVENT_ID) == null) {
            // the event is a data asset the plan leaves to authoring; say so instead of failing silently
            ui.notifications.error(t("kingdom.seasonalEconomy.flood.eventMissing"))
            return@ChatButton
        }
        kingdom.ongoingEvents = kingdom.ongoingEvents + RawOngoingKingdomEvent(stage = 0, id = SPRING_FLOOD_EVENT_ID)
        actor.setKingdom(kingdom)
        postChatMessage(t("kingdom.seasonalEconomy.flood.triggered"))
        markPetitionCardDone(button)
    },
    ChatButton("km-offer-seasonal-flood-dismiss") { game, _, _, button ->
        // "Hold back the waters": no mechanical effect; the year marker was stamped at posting, so
        // dismissing needs no write to keep the card from re-offering this spring
        if (!game.user.isGM) return@ChatButton
        markPetitionCardDone(button)
    },
    ChatButton("km-offer-life-event") { game, actor, _, button ->
        // plan section 5.1 verbatim: GM gate -> locate (settlement, record) -> idempotency on
        // hookApplied -> apply the closed hook -> persist -> announce
        if (!game.user.isGM) return@ChatButton
        val settlementId = button.dataset["settlementId"] ?: return@ChatButton
        val recordId = button.dataset["recordId"] ?: return@ChatButton
        val hookKind = button.dataset["hookKind"] ?: return@ChatButton
        val kingdom = actor.getKingdom() ?: return@ChatButton
        // grey this row before any await: the idempotency guard reads the actor flag, which is
        // only updated once the server acknowledges the write, so a second click inside that
        // window would pass the guard on the stale flag and run the hook again
        val row = button.closest(".km-life-row") as? HTMLElement
        fun settleRow() {
            row?.querySelectorAll("button")?.asList()?.filterIsInstance<HTMLElement>()
                ?.forEach { it.setAttribute("disabled", "disabled") }
            row?.classList?.add("km-card-resolved")
        }
        val settlement = kingdom.settlements.find { it.sceneId == settlementId }
        val record = settlement?.lifeEventHistory?.find { it.recordId == recordId }
        if (record == null) {
            // an End Turn undo restores the pre-roll history but leaves this digest in scrollback
            ui.notifications.info(t("settlementLife.recordGone"))
            settleRow()
            return@ChatButton
        }
        if (record.hookApplied == true) {
            ui.notifications.info(t("settlementLife.alreadyApplied"))
            settleRow()
            return@ChatButton
        }
        settleRow()
        val settlementName = game.scenes.get(settlementId)?.name ?: settlementId
        val gazette = lifeEventGazetteLine(settlementName, record)
        when (hookKind) {
            "dismiss" -> Unit
            // the magnitude is clamped to one point either way where the catalog is parsed
            // (settlementLifeTemplates); clamping the RESULT at zero is the rule every unrest
            // write follows
            "unrest-delta" -> kingdom.unrest = (kingdom.unrest + (record.hookMagnitude ?: 0)).coerceAtLeast(0)
            "rp-delta" -> kingdom.resourcePoints.now =
                (kingdom.resourcePoints.now + (record.hookMagnitude ?: 0)).coerceAtLeast(0)
            "quest-spawn" -> {
                // plan 5.1: "returns; marks applied on save". launch() is a non-suspending render,
                // so consuming the offer here would spend it the moment the form OPENS -- a GM who
                // cancels would find the row resolved and no quest anywhere. The callback re-reads
                // the kingdom (this handler's copy is stale by then), appends the quest, marks the
                // record, and persists, all in one write.
                AddQuest(
                    prefillTitle = gazette,
                    prefillGiver = record.castNames.firstOrNull() ?: settlementName,
                    settlements = kingdom.getAllSettlements(game).allSettlements.map { it.id to it.name },
                ) { quest ->
                    actor.getKingdom()?.let { fresh ->
                        val freshRecord = fresh.settlements.find { it.sceneId == settlementId }
                            ?.lifeEventHistory?.find { it.recordId == recordId }
                        if (freshRecord?.hookApplied == true) return@AddQuest
                        fresh.quests = (fresh.quests ?: emptyArray()) + quest
                        freshRecord?.hookApplied = true
                        actor.setKingdom(fresh)
                        postChatMessage(t("settlementLife.applied", recordOf("settlement" to settlementName)))
                    }
                }.launch()
                return@ChatButton
            }
            // a hidden rumor quest -- the plan's default target, reusing the quest surface rather
            // than a new subsystem; a GM promotes it by un-hiding it
            "rumor-spawn" -> kingdom.quests = (kingdom.quests ?: emptyArray()) + RawQuest(
                id = "quest-life-$recordId",
                title = t("settlementLife.rumorTitle", recordOf("settlement" to settlementName)),
                description = gazette,
                giver = record.castNames.firstOrNull() ?: settlementName,
                status = "active",
                type = "other",
                category = "side",
                level = null,
                target = null,
                rewards = RawQuestRewards(),
                flavorTextCompleted = "",
                notes = null,
                hidden = true,
                source = null,
                createdAt = js("Date.now()") as Double,
                updatedAt = null,
                completionSnapshot = null,
            )
            else -> return@ChatButton
        }
        record.hookApplied = true
        actor.setKingdom(kingdom)
        if (hookKind != "dismiss") {
            postChatMessage(t("settlementLife.applied", recordOf("settlement" to settlementName)))
        }
    },
    ChatButton("km-petition-confirm") { game, actor, _, button ->
        if (!game.user.isGM) return@ChatButton
        val petitionId = button.dataset["petitionId"] ?: return@ChatButton
        val optionId = button.dataset["optionId"] ?: return@ChatButton
        // the faction select exists only when a standing consequence named none; reading it from
        // THIS card rather than the document keeps two open offers from stealing each other's pick
        val faction = (button.closest(".km-petition-answer")
            ?.querySelector("select.km-petition-faction") as? org.w3c.dom.HTMLSelectElement)?.value
        if (applyPetitionAnswer(game, actor, petitionId, optionId, faction)) {
            markPetitionCardDone(button)
        }
    },
    ChatButton("km-petition-dismiss") { game, actor, _, button ->
        // dismissing applies nothing and closes nothing: the petition stays OPEN and the role can
        // choose again, because a GM waving off a card is not the office withdrawing its answer
        if (!game.user.isGM) return@ChatButton
        markPetitionCardDone(button)
    },
    ChatButton("km-petition-overdue-apply") { game, actor, _, button ->
        if (!game.user.isGM) return@ChatButton
        val petitionId = button.dataset["petitionId"] ?: return@ChatButton
        if (applyPetitionOverdue(game, actor, petitionId)) markPetitionCardDone(button)
    },
    ChatButton("km-petition-overdue-dismiss") { game, _, _, button ->
        if (!game.user.isGM) return@ChatButton
        markPetitionCardDone(button)
    },
    ChatButton("km-offer-npc-quest") { game, actor, event, button ->
        if (!game.user.isGM) return@ChatButton
        val npcName = button.dataset["npcName"] ?: ""
        AddQuest(
            prefillTitle = "",
            prefillGiver = npcName,
            settlements = actor.getKingdom()
                ?.let { k -> k.getAllSettlements(game).allSettlements.map { it.id to it.name } }
                ?: emptyList(),
        ) { quest ->
            actor.getKingdom()?.let { kingdom ->
                kingdom.quests = (kingdom.quests ?: emptyArray()) + quest
                actor.setKingdom(kingdom)
            }
        }.launch()
    },
    ChatButton("km-offer-npc-note") { game, actor, event, button ->
        // writes one line into the GM notes; idempotent per card via a verbatim-line check
        if (!game.user.isGM) return@ChatButton
        val npcName = button.dataset["npcName"] ?: return@ChatButton
        val band = button.dataset["band"] ?: ""
        val settlement = button.dataset["settlement"] ?: ""
        actor.getKingdom()?.let { kingdom ->
            val line = t(
                "kingdom.npcMemory.offer.noteLine",
                recordOf("name" to npcName, "band" to band, "settlement" to settlement),
            )
            if (kingdom.notes.gm.contains(line)) {
                ui.notifications.info(t("kingdom.npcMemory.offer.alreadyNoted"))
                return@let
            }
            kingdom.notes.gm = if (kingdom.notes.gm.isBlank()) line else kingdom.notes.gm + "\n" + line
            actor.setKingdom(kingdom)
            ui.notifications.info(t("kingdom.npcMemory.offer.noted"))
        }
    },
    ChatButton("km-offer-faction-standing-shift") { game, actor, event, button ->
        // GM-confirmed faction-agenda standing shift (faction-agenda plan 6.1): the tick only
        // EMITS the intent; this click is the sole writer. Idempotency is the standing log
        // itself -- an identical (turn, delta, reason) entry means the shift already landed.
        if (!game.user.isGM) return@ChatButton
        if (button.dataset["action"] == "dismiss") {
            markFactionMoveRowDone(button)
            return@ChatButton
        }
        val target = button.dataset["target"] ?: return@ChatButton
        val delta = button.dataset["delta"]?.toIntOrNull() ?: return@ChatButton
        val turn = button.dataset["turn"]?.toIntOrNull() ?: return@ChatButton
        val reason = button.dataset["reason"] ?: return@ChatButton
        val source = button.dataset["source"]
        actor.getKingdom()?.let { kingdom ->
            val groups = kingdom.groups ?: return@let
            val index = groups.indexOfFirst { it.name == target }
            if (index < 0) {
                // the GM renamed or deleted the faction after the card posted: stale, not wrong
                ui.notifications.warn(t("kingdom.factionAgenda.targetGone", recordOf("name" to target)))
                return@let
            }
            val group = groups[index]
            val already = group.standingLog
                ?.any { it.turn == turn && it.delta == delta && it.reason == reason && it.source == source } == true
            if (already) {
                ui.notifications.info(t("kingdom.factionAgenda.alreadyApplied"))
                markFactionMoveRowDone(button)
                return@let
            }
            val after = applyStandingDelta(group.standing, delta)
            groups[index] = RawGroup.copy(
                group,
                standing = after,
                standingLog = appendStandingEntry(
                    group.standingLog,
                    RawFactionStandingEntry(turn = turn, delta = delta, reason = reason, source = source),
                ),
            )
            kingdom.groups = groups
            actor.setKingdom(kingdom)
            markFactionMoveRowDone(button)
        }
    },
    ChatButton("km-offer-war-threat") { game, actor, event, button ->
        // GM-confirmed offer from a faction-standing threshold crossing (#1 → #12).
        // Opens the AddWarThreat dialog prefilled with the faction; nothing is created
        // until the GM saves.
        // Party actors are owner-permissioned to players, so without this any player who can see
        // the card's DOM could declare a war. {{#if isGM}} in a template is not a guard.
        if (!game.user.isGM) return@ChatButton
        val faction = button.dataset["faction"] ?: ""
        AddWarThreat(
            prefillName = t("chatMessages.endTurn.warThreatName", recordOf("group" to faction)),
            prefillEnemyFaction = faction.ifBlank { null },
            factions = actor.getKingdom()?.groups?.map { it.name } ?: emptyList(),
        ) { threat ->
            buildPromise {
                actor.getKingdom()?.let { kingdom ->
                    kingdom.warThreats = (kingdom.warThreats ?: emptyArray()) + threat
                    kingdom.warPressure = recalculateWarPressure(
                        kingdom.warThreats ?: emptyArray(),
                        kingdom.armyDeployments ?: emptyArray(),
                        kingdom.warPressure,
                    )
                    actor.setKingdom(kingdom)
                }
            }
        }.launch()
    },
    ChatButton("km-offer-holding-income") { game, actor, _, button ->
        // Award is what makes income REAL: the tick only stamps lastIncomeTurn, and this handler
        // is the sole writer of lifetimeIncomeGold -- the ledger records gold actually handed
        // over, and the GM may dismiss the card. Chat-award only (plan open question 1's
        // recommended default): the gold is announced, not silently pushed into an inventory.
        if (!game.user.isGM) return@ChatButton
        val holdingId = button.dataset["holdingId"] ?: return@ChatButton
        val gold = button.dataset["gold"]?.toIntOrNull() ?: return@ChatButton
        val turn = button.dataset["turn"]?.toIntOrNull() ?: return@ChatButton
        actor.getKingdom()?.let { kingdom ->
            val holding = kingdom.personalHoldings?.firstOrNull { it.id == holdingId }
                ?: return@ChatButton
            if (holding.incomeAwardedTurn == turn) {
                ui.notifications.info(t("kingdom.holdings.incomeOffer.alreadyAwarded"))
                return@ChatButton
            }
            kingdom.personalHoldings = kingdom.personalHoldings?.map {
                if (it.id == holdingId) {
                    RawPersonalHolding.copy(
                        it,
                        incomeAwardedTurn = turn,
                        lifetimeIncomeGold = (it.lifetimeIncomeGold ?: 0) + gold,
                    )
                } else it
            }?.toTypedArray()
            actor.setKingdom(kingdom)
            postChatMessage(
                t(
                    "kingdom.holdings.incomeOffer.awarded",
                    recordOf(
                        "owner" to (holding.ownerLabel ?: holding.name),
                        "holding" to holding.name,
                        "gold" to gold.toString(),
                    ),
                )
            )
        }
    },
    ChatButton("km-offer-holding-damage") { game, actor, _, button ->
        // Apply advances the ladder ONLY from the condition pinned at post time: a double-click
        // or a stale card whose holding already moved is a visible no-op, never a second blow.
        if (!game.user.isGM) return@ChatButton
        val holdingId = button.dataset["holdingId"] ?: return@ChatButton
        val fromCondition = button.dataset["fromCondition"] ?: return@ChatButton
        val severity = button.dataset["severity"]
            ?.let { runCatching { DamageSeverity.valueOf(it) }.getOrNull() }
            ?: return@ChatButton
        val cause = button.dataset["cause"] ?: ""
        actor.getKingdom()?.let { kingdom ->
            val holding = kingdom.personalHoldings?.firstOrNull { it.id == holdingId }
                ?: return@ChatButton
            if (holding.condition != fromCondition) {
                ui.notifications.info(t("kingdom.holdings.damageOffer.alreadyApplied"))
                return@ChatButton
            }
            kingdom.personalHoldings = kingdom.personalHoldings?.map {
                if (it.id == holdingId) applyHoldingDamage(it, severity, cause) else it
            }?.toTypedArray()
            actor.setKingdom(kingdom)
            postChatMessage(
                t(
                    "kingdom.holdings.damageOffer.applied",
                    recordOf("holding" to holding.name, "cause" to cause),
                )
            )
        }
    },
    ChatButton("km-waive-holding-damage") { game, _, _, _ ->
        // the plan's Waive records NOTHING; the ack exists because a silent button reads broken
        if (!game.user.isGM) return@ChatButton
        ui.notifications.info(t("kingdom.holdings.damageOffer.waived"))
    },
    ChatButton("km-offer-holding-repair") { game, actor, _, button ->
        // Repair is announced, not silently paid: the card names the cost, the click restores one
        // step, and the public line records who owes what -- the same chat-award discipline as
        // income (plan open question 2 leaves the payer open, so the table settles it).
        if (!game.user.isGM) return@ChatButton
        val holdingId = button.dataset["holdingId"] ?: return@ChatButton
        val fromCondition = button.dataset["fromCondition"] ?: return@ChatButton
        val cost = button.dataset["cost"]?.toIntOrNull() ?: return@ChatButton
        actor.getKingdom()?.let { kingdom ->
            val holding = kingdom.personalHoldings?.firstOrNull { it.id == holdingId }
                ?: return@ChatButton
            if (holding.condition != fromCondition) {
                ui.notifications.info(t("kingdom.holdings.repairOffer.alreadyRepaired"))
                return@ChatButton
            }
            val repaired = repairedCondition(holding.conditionEnum())
            kingdom.personalHoldings = kingdom.personalHoldings?.map {
                if (it.id == holdingId) {
                    RawPersonalHolding.copy(it, condition = repaired.value)
                } else it
            }?.toTypedArray()
            actor.setKingdom(kingdom)
            postChatMessage(
                t(
                    "kingdom.holdings.repairOffer.repaired",
                    recordOf("holding" to holding.name, "cost" to cost.toString()),
                )
            )
        }
    },
    ChatButton("km-offer-renown-epithet") { game, actor, _, button ->
        // Grants a PC the epithet they earned. Pure honour -- no rules effect -- but still
        // GM-confirmed, because it is the table's language about that character.
        if (!game.user.isGM) return@ChatButton
        val pcUuid = button.dataset["pcUuid"] ?: return@ChatButton
        val epithetId = button.dataset["epithetId"] ?: return@ChatButton
        actor.getKingdom()?.let { kingdom ->
            val row = kingdom.renown?.firstOrNull { it.actorUuid == pcUuid } ?: return@ChatButton
            val held = row.epithets ?: emptyArray()
            if (epithetId in held) return@ChatButton  // idempotent: a second click grants nothing
            kingdom.renown = kingdom.renown?.map {
                if (it.actorUuid == pcUuid) RawPcRenown.copy(it, epithets = held + epithetId) else it
            }?.toTypedArray()
            actor.setKingdom(kingdom)
            // public, deliberately: an epithet is what the realm CALLS them, so the table hears it
            postChatMessage(
                t(
                    "kingdom.renown.epithetGranted",
                    recordOf(
                        "name" to (row.actorName ?: t("kingdom.renown.unknownPc")),
                        "epithet" to t("kingdom.renown.epithet.$epithetId"),
                    ),
                )
            )
        }
    },
    ChatButton("km-offer-renown-perk-access") { game, actor, _, button ->
        // The one mechanical perk: the realm's shops stock better goods. Settlement-scoped, not
        // per-shopper -- InspectSettlement has no viewer, and the GM granted every tier by hand.
        if (!game.user.isGM) return@ChatButton
        val pcUuid = button.dataset["pcUuid"] ?: return@ChatButton
        val tier = button.dataset["perkTier"]?.toIntOrNull() ?: return@ChatButton
        actor.getKingdom()?.let { kingdom ->
            val row = kingdom.renown?.firstOrNull { it.actorUuid == pcUuid } ?: return@ChatButton
            // never DOWNGRADE an already-granted tier: a lower-tier epithet granted later must
            // not take away access the PC already has
            if ((row.purchaseAccessTier ?: 0) >= tier) {
                ui.notifications.info(t("kingdom.renown.perkAlreadyGranted"))
                return@ChatButton
            }
            kingdom.renown = kingdom.renown?.map {
                if (it.actorUuid == pcUuid) RawPcRenown.copy(it, purchaseAccessTier = tier) else it
            }?.toTypedArray()
            actor.setKingdom(kingdom)
            ui.notifications.info(t("kingdom.renown.perkGranted"))
        }
    },
    ChatButton("km-offer-renown-invitation") { game, actor, _, button ->
        // Opens the existing quest pipeline prefilled; nothing is created until the GM saves.
        if (!game.user.isGM) return@ChatButton
        val faction = button.dataset["faction"]?.takeIf { it.isNotBlank() }
        AddQuest(
            prefillTitle = t(
                "kingdom.renown.invitationTitle",
                recordOf("faction" to (faction ?: t("kingdom.renown.unknownFaction"))),
            ),
            prefillGiver = faction,
        ) { quest ->
            actor.getKingdom()?.let { kingdom ->
                kingdom.quests = (kingdom.quests ?: emptyArray()) + quest
                actor.setKingdom(kingdom)
            }
        }.launch()
    },
    ChatButton("km-offer-renown-dismiss") { game, _, _, _ ->
        if (!game.user.isGM) return@ChatButton
        ui.notifications.info(t("kingdom.renown.offerDismissed"))
    },
    ChatButton("km-council-vote-cast") { game, actor, _, button ->
        // ANY user, deliberately no isGM bail: casting is the one player-facing write, and it
        // rides the socket to the first-GM client -- the single writer that keeps three
        // same-second ballots from read-modify-writing the whole flag in parallel. The handler
        // keys the ballot on the socket senderId, so nothing identity-shaped is read here.
        val dispatcher = chatButtonDispatcher ?: return@ChatButton
        if (game.users.activeGM == null) {
            // no executor exists: the emit would vanish without feedback, and a ballot a player
            // believes they cast simply not existing is the worst version of that
            ui.notifications.warn(t("kingdom.councilVotes.noGmOnline"))
            return@ChatButton
        }
        val voteId = button.dataset["voteId"] ?: return@ChatButton
        val optionIdx = button.dataset["optionIdx"]?.toIntOrNull() ?: return@ChatButton
        dispatcher.dispatch(
            ActionMessage(
                action = "castCouncilVote",
                data = CastCouncilVoteData(
                    actorUuid = actor.uuid,
                    voteId = voteId,
                    optionIdx = optionIdx,
                ).unsafeCast<AnyObject>(),
            )
        )
    },
    ChatButton("km-council-vote-abstain") { game, actor, _, button ->
        // Explicit abstain is a recorded position, distinct from not having voted at all.
        val dispatcher = chatButtonDispatcher ?: return@ChatButton
        if (game.users.activeGM == null) {
            ui.notifications.warn(t("kingdom.councilVotes.noGmOnline"))
            return@ChatButton
        }
        val voteId = button.dataset["voteId"] ?: return@ChatButton
        dispatcher.dispatch(
            ActionMessage(
                action = "castCouncilVote",
                data = CastCouncilVoteData(
                    actorUuid = actor.uuid,
                    voteId = voteId,
                    optionIdx = ABSTAIN_OPTION,
                ).unsafeCast<AnyObject>(),
            )
        )
    },
    ChatButton("km-council-vote-close") { game, actor, _, button ->
        // GM only, and the bail is the ONLY authorization: the controls card is whispered, but
        // players own the party actor, so visibility is never the guard. The write itself rides
        // the dispatcher to the FIRST-GM client -- a second GM closing while a ballot is
        // mid-flight there would otherwise race the whole-flag write and either resurrect a
        // pre-ballot copy or reopen the vote its "frozen" result card just published.
        if (!game.user.isGM) return@ChatButton
        val dispatcher = chatButtonDispatcher ?: return@ChatButton
        val voteId = button.dataset["voteId"] ?: return@ChatButton
        dispatcher.dispatch(
            ActionMessage(
                action = "closeCouncilVote",
                data = CouncilVoteLifecycleData(actorUuid = actor.uuid, voteId = voteId)
                    .unsafeCast<AnyObject>(),
            )
        )
    },
    ChatButton("km-council-vote-reopen") { game, actor, _, button ->
        if (!game.user.isGM) return@ChatButton
        val dispatcher = chatButtonDispatcher ?: return@ChatButton
        val voteId = button.dataset["voteId"] ?: return@ChatButton
        dispatcher.dispatch(
            ActionMessage(
                action = "reopenCouncilVote",
                data = CouncilVoteLifecycleData(actorUuid = actor.uuid, voteId = voteId)
                    .unsafeCast<AnyObject>(),
            )
        )
    },
    ChatButton("km-offer-rival-war-threat") { game, actor, _, button ->
        // GM-confirmed offer from a rival realm massing at war (rival-realms plan section 5.2).
        // Reuses the war-threat creation body above: opens AddWarThreat prefilled, nothing exists
        // until the GM saves. Party actors are owner-permissioned to players, so the isGM bail is
        // the guard -- template visibility is not.
        if (!game.user.isGM) return@ChatButton
        val faction = button.dataset["faction"] ?: ""
        val realmId = button.dataset["realmId"]
        val offeredArmyCount = button.dataset["armyCount"]?.toIntOrNull()
        AddWarThreat(
            prefillName = t("chatMessages.endTurn.warThreatName", recordOf("group" to faction)),
            prefillEnemyFaction = faction.ifBlank { null },
            factions = actor.getKingdom()?.groups?.map { it.name } ?: emptyList(),
        ) { threat ->
            buildPromise {
                actor.getKingdom()?.let { kingdom ->
                    kingdom.warThreats = (kingdom.warThreats ?: emptyArray()) + threat
                    kingdom.warPressure = recalculateWarPressure(
                        kingdom.warThreats ?: emptyArray(),
                        kingdom.armyDeployments ?: emptyArray(),
                        kingdom.warPressure,
                    )
                    // acting on the offer answers it. maxOf, never a plain write: old digests
                    // stay clickable in chat history forever, and a stale card's pinned count
                    // would otherwise ROLL BACK the watermark and re-fire an already-answered
                    // offer at an unchanged army count
                    kingdom.rivalRealms = kingdom.rivalRealms?.map { realm ->
                        if (realm.id != null && realm.id == realmId && offeredArmyCount != null) {
                            RawRivalRealm.copy(
                                realm,
                                lastWarOfferArmyCount =
                                    maxOf(realm.lastWarOfferArmyCount ?: 0, offeredArmyCount),
                            )
                        } else {
                            realm
                        }
                    }?.toTypedArray()
                    actor.setKingdom(kingdom)
                }
            }
        }.launch()
    },
    ChatButton("km-offer-rival-war-dismiss") { game, actor, _, button ->
        // Dismiss re-asserts the post-time watermark (posting already stamped it, so this is
        // belt-and-braces for hand-edited worlds). maxOf so a stale card from an earlier turn
        // can never roll the watermark back and resurrect an answered offer.
        if (!game.user.isGM) return@ChatButton
        val realmId = button.dataset["realmId"] ?: return@ChatButton
        val offeredArmyCount = button.dataset["armyCount"]?.toIntOrNull() ?: return@ChatButton
        actor.getKingdom()?.let { kingdom ->
            kingdom.rivalRealms = kingdom.rivalRealms?.map { realm ->
                if (realm.id != null && realm.id == realmId) {
                    RawRivalRealm.copy(
                        realm,
                        lastWarOfferArmyCount =
                            maxOf(realm.lastWarOfferArmyCount ?: 0, offeredArmyCount),
                    )
                } else {
                    realm
                }
            }?.toTypedArray()
            actor.setKingdom(kingdom)
            ui.notifications.info(t("kingdom.rivalRealms.warOffer.dismissed"))
        }
    },
    ChatButton("km-offer-rival-standing-shift") { game, actor, _, button ->
        // Applies the border-tension standing nudge to the linked group. Idempotent on
        // (turn, group, reason) where the reason is the string PINNED ON THE CARD at post time:
        // resolving it at click time would key the dedup on the clicking client's locale, and a
        // second GM running a different language would compute a different string, miss the log
        // entry, and stack the penalty. Pinned, every client compares the same bytes -- and the
        // log prose stays displayable (localizeKM renders unknown keys as themselves).
        if (!game.user.isGM) return@ChatButton
        val faction = button.dataset["faction"] ?: return@ChatButton
        val offerTurn = button.dataset["turn"]?.toIntOrNull() ?: return@ChatButton
        val reason = button.dataset["reason"]?.takeIf { it.isNotBlank() } ?: return@ChatButton
        actor.getKingdom()?.let { kingdom ->
            val group = kingdom.groups.firstOrNull {
                it.name.trim().lowercase() == faction.trim().lowercase()
            } ?: run {
                ui.notifications.warn(t("kingdom.rivalRealms.unlinked"))
                return@ChatButton
            }
            val alreadyApplied = group.standingLog
                ?.any { it.turn == offerTurn && it.reason == reason } == true
            if (alreadyApplied) {
                // every idempotent apply button here reports its no-op; a silent return reads
                // as a broken button
                ui.notifications.warn(t("kingdom.rivalRealms.standingOffer.alreadyApplied"))
                return@ChatButton
            }
            group.standing = applyStandingDelta(group.standing, RIVAL_STANDING_SHIFT_DELTA)
            group.addStandingEntry(RawFactionStandingEntry(
                turn = offerTurn,
                delta = RIVAL_STANDING_SHIFT_DELTA,
                reason = reason,
            ))
            actor.setKingdom(kingdom)
            ui.notifications.info(t("kingdom.rivalRealms.standingOffer.applied"))
        }
    },
    ChatButton("km-offer-rival-standing-dismiss") { game, _, _, _ ->
        // The plan's Dismiss "posts nothing" -- but a button that visibly does nothing reads as
        // broken, so acknowledge without touching any state.
        if (!game.user.isGM) return@ChatButton
        ui.notifications.info(t("kingdom.rivalRealms.standingOffer.dismissed"))
    },
    ChatButton("km-offer-war-threat-arrival") { game, actor, event, button ->
        // GM-confirmed offer for a war threat that has arrived (triggered this turn).
        // Buttons: [Spawn kingdom event], [Queue encounter at linked hex], [Dismiss].
        // Idempotent: each button checks if the action was already taken via offerConsumed flag.
        if (!game.user.isGM) return@ChatButton
        val action = button.dataset["action"] ?: return@ChatButton
        val threatId = button.dataset["threatId"] ?: return@ChatButton
        actor.getKingdom()?.let { kingdom ->
            val threat = kingdom.warThreats?.find { it.id == threatId } ?: return@ChatButton
            if (threat.offerConsumed == true) return@ChatButton

            when (action) {
                "spawnEvent" -> {
                    // Spawn the war-threat-arrival kingdom event. Guarded through getEvent():
                    // pushing an id the event registry can't resolve would create an invisible
                    // ongoing entry that also desyncs km-resolve-event's index-based lookup.
                    val eventId = "war-threat-arrival"
                    val event = kingdom.getEvent(eventId)
                    if (event == null) {
                        ui.notifications.error(t("chatMessages.warThreatArrival.eventMissing"))
                        return@ChatButton
                    }
                    val ongoingEvent = if (KingdomEventTrait.SETTLEMENT.value in event.traits) {
                        val settlements = kingdom.getAllSettlements(game).allSettlements
                        val pick = pickEventSettlement(settlements)
                        RawOngoingKingdomEvent(
                            stage = 0,
                            id = eventId,
                            settlementSceneId = pick.settlementId,
                            secretLocation = pick.secretLocation,
                        )
                    } else {
                        RawOngoingKingdomEvent(stage = 0, id = eventId)
                    }
                    kingdom.ongoingEvents = kingdom.ongoingEvents + ongoingEvent
                    val updatedThreat = threat.copyWith(offerConsumed = true)
                    kingdom.warThreats = kingdom.warThreats?.map {
                        if (it.id == threatId) updatedThreat else it
                    }?.toTypedArray() ?: emptyArray()
                    actor.setKingdom(kingdom)
                    postChatMessage(t("chatMessages.warThreatArrival.spawnedEvent", recordOf("name" to threat.name)))
                }
                "queueEncounter" -> {
                    // Queue an encounter at the hex linked to this threat: persist a pending
                    // flag on the hex content so the offer actually leaves durable state behind
                    // (previously it only posted a chat line and consumed the offer).
                    val hexContent = kingdom.hexContents?.find { it.linkedWarThreatId == threatId }
                    if (hexContent != null) {
                        hexContent.pendingEncounter = true
                        val updatedThreat = threat.copyWith(offerConsumed = true)
                        kingdom.warThreats = kingdom.warThreats?.map {
                            if (it.id == threatId) updatedThreat else it
                        }?.toTypedArray() ?: emptyArray()
                        actor.setKingdom(kingdom)
                        postChatMessage(t("chatMessages.warThreatArrival.queuedEncounter", recordOf("name" to threat.name, "hexKey" to hexContent.hexKey)))
                    }
                }
                "sack" -> {
                    // Raze exactly the structures the GM was shown. Ruining records the token ids
                    // on the settlement: the structures stay on the map so the GM can see what was
                    // lost and rebuild later, but they stop contributing bonuses, storage and block
                    // occupancy immediately.
                    val sceneId = threat.targetSettlementSceneId
                    val settlement = sceneId?.let { id -> kingdom.settlements.find { it.sceneId == id } }
                    if (settlement == null) {
                        ui.notifications.warn(t("chatMessages.siege.noTargets"))
                        return@ChatButton
                    }
                    val tokenIds = button.dataset["tokenIds"]
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?: emptyList()
                    val unrest = button.dataset["unrest"]?.toIntOrNull() ?: 0
                    val names = tokenIds.mapNotNull { tokenId ->
                        siegeTargetsFor(game, kingdom, settlement.sceneId).find { it.tokenId == tokenId }?.name
                    }
                    settlement.destroyedStructureIds =
                        (settlement.destroyedStructureIds ?: emptyArray()) + tokenIds.toTypedArray()
                    // Damage hook #2: a sacked settlement strikes every structure-bound holding
                    // there -- the district burned around them. MAJOR; posted after this branch's
                    // setKingdom below.
                    val sackedHoldings = holdingsAt(kingdom.personalHoldings, hexKey = null, sceneId = sceneId)
                    kingdom.unrest += unrest
                    val settlementName = game.scenes.get(settlement.sceneId)?.name ?: threat.name
                    val updatedThreat = threat.copyWith(offerConsumed = true)
                    kingdom.warThreats = kingdom.warThreats?.map {
                        if (it.id == threatId) updatedThreat else it
                    }?.toTypedArray() ?: emptyArray()
                    // Gazette line. The threat arrived at End Turn and the GM sacks in its
                    // immediate aftermath, so the loss belongs to the turn just recorded; there is
                    // no open turn record to write to between turns.
                    if (names.isNotEmpty()) {
                        kingdom.turnHistory?.lastOrNull()?.let { record ->
                            val line = t("chatMessages.siege.gazette", recordOf("names" to names.joinToString(", ")))
                            record.notes = listOfNotNull(record.notes?.takeIf { it.isNotBlank() }, line)
                                .joinToString("\n")
                        }
                    }
                    actor.setKingdom(kingdom)
                    for (holding in sackedHoldings) {
                        postHoldingDamageOffer(
                            game = game,
                            actorUuid = actor.uuid,
                            holding = holding,
                            severity = DamageSeverity.MAJOR,
                            cause = t(
                                "kingdom.holdings.damageOffer.sacked",
                                recordOf("settlement" to (settlementName ?: "")),
                            ),
                        )
                    }
                    postChatMessage(
                        if (names.isEmpty()) {
                            t(
                                "chatMessages.siege.appliedNoStructures",
                                recordOf("settlement" to settlementName, "unrest" to unrest.toString()),
                            )
                        } else {
                            t(
                                "chatMessages.siege.applied",
                                recordOf(
                                    "settlement" to settlementName,
                                    "names" to names.joinToString(", "),
                                    "unrest" to unrest.toString(),
                                ),
                            )
                        }
                    )
                }
                "rerollSack" -> {
                    // Re-post the same offer with a freshly rolled selection. The offer is NOT
                    // consumed, so the GM can keep rerolling until they like the outcome.
                    postWarThreatArrivalOffer(actor, kingdom, threat)
                }
                "dismiss" -> {
                    // Mark the offer as consumed to prevent re-posting
                    val updatedThreat = threat.copyWith(offerConsumed = true)
                    kingdom.warThreats = kingdom.warThreats?.map {
                        if (it.id == threatId) updatedThreat else it
                    }?.toTypedArray() ?: emptyArray()
                    actor.setKingdom(kingdom)
                    postChatMessage(t("chatMessages.warThreatArrival.dismissed", recordOf("name" to threat.name)))
                }
            }
        }
    },
    ChatButton("km-offer-war-ruin") { game, actor, event, button ->
        // GM-confirmed offer posted when war pressure crosses its ruin threshold at End Turn:
        // the GM picks which Ruin absorbs the strain (+1 to its value) or dismisses.
        // Idempotent per crossing: warPressure.ruinOfferTurn records the answered turn.
        if (!game.user.isGM) return@ChatButton
        val choice = button.dataset["choice"] ?: return@ChatButton
        val cardTurn = button.dataset["turn"]?.toIntOrNull() ?: return@ChatButton
        actor.getKingdom()?.let { kingdom ->
            val pressure = kingdom.warPressure ?: return@ChatButton
            if (pressure.ruinOfferTurn == cardTurn) return@ChatButton  // already answered
            when (choice) {
                "corruption" -> kingdom.ruin.corruption.value += 1
                "crime" -> kingdom.ruin.crime.value += 1
                "decay" -> kingdom.ruin.decay.value += 1
                "strife" -> kingdom.ruin.strife.value += 1
                "dismiss" -> {}
                else -> return@ChatButton
            }
            pressure.ruinOfferTurn = cardTurn
            actor.setKingdom(kingdom)
            if (choice == "dismiss") {
                postChatMessage(t("chatMessages.warRuin.dismissed"))
            } else {
                postChatMessage(t("chatMessages.warRuin.applied", recordOf("ruin" to t("chatMessages.warRuin.$choice"))))
            }
        }
    },
    ChatButton("km-offer-loot-award") { game, actor, _, button ->
        if (!game.user.isGM) return@ChatButton
        val id = button.dataset["contentId"] ?: return@ChatButton
        if (awardLootManifest(game, actor, id)) markLootCardDone(button)
    },
    ChatButton("km-offer-loot-dismiss") { game, actor, _, button ->
        if (!game.user.isGM) return@ChatButton
        val id = button.dataset["contentId"] ?: return@ChatButton
        if (dismissLootManifest(actor, id)) markLootCardDone(button)
    },
    ChatButton("km-offer-loot-cleanse") { game, actor, _, button ->
        if (!game.user.isGM) return@ChatButton
        val uuid = button.dataset["itemUuid"] ?: return@ChatButton
        val kingdom = actor.getKingdom() ?: return@ChatButton
        // hand the dialog the item directly -- the GM never re-finds what the module just gave them
        val item = fromUuidOfTypes(uuid, PF2EItem::class)
        openCleanseItemDialog(
            kingdomLevel = kingdom.level,
            settlements = kingdom.getAllSettlements(game).allSettlements.map { settlement ->
                CleanseItemSettlement(
                    id = settlement.id,
                    name = settlement.name,
                    structureNames = settlement.constructedStructures.map { it.name }.toSet(),
                )
            },
            preselected = item,
        ) { preparation ->
            ui.notifications.info(
                t("chatMessages.lootAward.cleansePrepared", recordOf("dc" to preparation.dc.toString()))
            )
        }
    },
    ChatButton("km-offer-threat-advance") { game, actor, _, button ->
        if (!game.user.isGM) return@ChatButton
        val id = button.dataset["threatId"] ?: return@ChatButton
        val kingdom = actor.getKingdom() ?: return@ChatButton
        val threat = kingdom.warThreats?.find { it.id == id } ?: return@ChatButton
        val target = threat.targetHexLocation ?: return@ChatButton
        val currentTurn = kingdom.currentTurn ?: 0
        if (threat.migrationConsumedTurn == currentTurn) {
            ui.notifications.warn(t("kingdom.mapDynamism.alreadyResolved", recordOf("name" to threat.name)))
            return@ChatButton
        }
        val neighbors = kingmakerNeighbors()
        if (neighbors == null) {
            ui.notifications.warn(t("kingdom.mapDynamism.noRegion"))
            return@ChatButton
        }
        // speed = hexes per ACCEPTED step; the walk stops at the target regardless
        var position = threat.currentHexLocation ?: target
        val speed = (kingdom.settings.threatMigrationSpeed ?: 1).coerceAtLeast(1)
        repeat(speed) {
            position = nextThreatHex(position, target, neighbors) ?: return@repeat
        }
        threat.currentHexLocation = position
        threat.migrationConsumedTurn = currentTurn
        actor.setKingdom(kingdom)
        ui.notifications.info(
            t(
                "kingdom.mapDynamism.advanced",
                recordOf("name" to threat.name, "hex" to hexDisplayLabel(position)),
            )
        )
        markMapChangeRowDone(button)
    },
    ChatButton("km-offer-threat-hold") { game, actor, _, button ->
        if (!game.user.isGM) return@ChatButton
        val id = button.dataset["threatId"] ?: return@ChatButton
        val kingdom = actor.getKingdom() ?: return@ChatButton
        val threat = kingdom.warThreats?.find { it.id == id } ?: return@ChatButton
        // per-turn guard, NOT a permanent flag: a held threat re-offers next turn (plan SS5.1)
        threat.migrationConsumedTurn = kingdom.currentTurn ?: 0
        actor.setKingdom(kingdom)
        markMapChangeRowDone(button)
    },
    ChatButton("km-offer-hex-rewild") { game, actor, _, button ->
        if (!game.user.isGM) return@ChatButton
        val hexKey = button.dataset["hexKey"] ?: return@ChatButton
        val kingdom = actor.getKingdom() ?: return@ChatButton
        // the ONLY shared-map write in the feature, and it happens here, on the GM's click --
        // updateSource+save is the annexation-proven path (kingmaker.state has no .update())
        val applied = runCatching {
            val state = com.foundryvtt.kingmaker.kingmaker.state.asDynamic()
            val hexFlags = js("{}")
            hexFlags.cleared = false
            val hexes = js("{}")
            hexes[hexKey] = hexFlags
            val changes = js("{}")
            changes.hexes = hexes
            state.updateSource(changes)
            (state.save() as? kotlin.js.Promise<*>)?.await()
            true
        }.getOrElse {
            console.error("re-wild write failed for hex $hexKey", it)
            false
        }
        if (!applied) {
            ui.notifications.error(t("kingdom.mapDynamism.rewildFailed", recordOf("hex" to hexDisplayLabel(hexKey))))
            return@ChatButton
        }
        kingdom.rewildTrackers?.find { it.hexKey == hexKey }?.offerConsumed = true
        actor.setKingdom(kingdom)
        ui.notifications.info(t("kingdom.mapDynamism.rewilded", recordOf("hex" to hexDisplayLabel(hexKey))))
        markMapChangeRowDone(button)
    },
    ChatButton("km-offer-hex-keep") { game, actor, _, button ->
        if (!game.user.isGM) return@ChatButton
        val hexKey = button.dataset["hexKey"] ?: return@ChatButton
        val kingdom = actor.getKingdom() ?: return@ChatButton
        // quiet for the rest of this cycle; a re-clear after leaving the set starts a fresh one
        kingdom.rewildTrackers?.find { it.hexKey == hexKey }?.offerConsumed = true
        actor.setKingdom(kingdom)
        markMapChangeRowDone(button)
    },
    ChatButton("km-offer-downtime-complete") { game, actor, _, button ->
        if (!game.user.isGM) return@ChatButton
        val id = button.dataset["projectId"] ?: return@ChatButton
        val kingdom = actor.getKingdom() ?: return@ChatButton
        val raw = kingdom.downtimeProjects?.find { it.id == id } ?: return@ChatButton
        // the tick already marked it completed; confirm ACKNOWLEDGES and names the roll --
        // adjudicating the craft/retrain itself is explicitly the GM's (plan SS5, SS9)
        postChatMessage(
            t(
                "camping.downtime.offer.confirmed",
                recordOf(
                    "title" to raw.title,
                    "prompt" to downtimeRollPrompt(DowntimeKind.fromValue(raw.kind)),
                ),
            ),
            whisper = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray(),
        )
        (button.closest(".km-downtime-offer-buttons") as? HTMLElement)
            ?.querySelectorAll("button")?.asList()?.filterIsInstance<HTMLElement>()
            ?.forEach { it.setAttribute("disabled", "disabled") }
    },
    ChatButton("km-offer-downtime-extend") { game, actor, _, button ->
        if (!game.user.isGM) return@ChatButton
        val id = button.dataset["projectId"] ?: return@ChatButton
        val kingdom = actor.getKingdom() ?: return@ChatButton
        val raw = kingdom.downtimeProjects?.find { it.id == id } ?: return@ChatButton
        // "runs longer": back to in-progress with one day left (plan SS8)
        raw.status = "inProgress"
        raw.daysRemaining = 1
        raw.pauseReason = null
        actor.setKingdom(kingdom)
        ui.notifications.info(t("camping.downtime.offer.extended", recordOf("title" to raw.title)))
        (button.closest(".km-downtime-offer-buttons") as? HTMLElement)
            ?.querySelectorAll("button")?.asList()?.filterIsInstance<HTMLElement>()
            ?.forEach { it.setAttribute("disabled", "disabled") }
    },
    ChatButton("km-offer-pressure-fire") { game, actor, _, button ->
        // players are OWNERs of the party actor: the isGM check IS the authorization
        if (!game.user.isGM) return@ChatButton
        val id = button.dataset["scheduleId"] ?: return@ChatButton
        if (confirmPressureFiring(game, actor, id)) markPressureRowDone(button)
    },
    ChatButton("km-offer-pressure-dismiss") { game, actor, _, button ->
        if (!game.user.isGM) return@ChatButton
        val id = button.dataset["scheduleId"] ?: return@ChatButton
        if (dismissPressureFiring(actor, id)) markPressureRowDone(button)
    },
    ChatButton("km-offer-pressure-fire-all") { game, actor, _, button ->
        if (!game.user.isGM) return@ChatButton
        // driven from DATA (pending = fired > handled), never from card DOM
        val pending = pendingPressureRows(actor.getKingdom()?.scheduledPressures)
        var applied = 0
        for (row in pending) {
            if (confirmPressureFiring(game, actor, row.id)) applied++
        }
        ui.notifications.info(t("kingdom.deadlines.allConfirmed", recordOf("count" to applied.toString())))
        markPressureCardDone(button)
    },
    ChatButton("km-offer-pressure-dismiss-all") { game, actor, _, button ->
        if (!game.user.isGM) return@ChatButton
        val pending = pendingPressureRows(actor.getKingdom()?.scheduledPressures)
        var dismissed = 0
        for (row in pending) {
            if (dismissPressureFiring(actor, row.id)) dismissed++
        }
        ui.notifications.info(t("kingdom.deadlines.allDismissed", recordOf("count" to dismissed.toString())))
        markPressureCardDone(button)
    },
    ChatButton("km-ping-ready") { game, _, _, button ->
        // Player self-service, NOT an offer: writes only the clicking user's own flag (plan SS5.1).
        val turn = button.dataset["turn"]?.toIntOrNull() ?: return@ChatButton
        game.user.savePingsReady(turn)
        button.textContent = t("kingdom.pings.card.readyDone")
        ui.notifications.info(t("kingdom.pings.card.readyRecorded"))
    },
    ChatButton("km-ping-jump") { game, actor, _, button ->
        // Pure navigation (plan SS5.1): open the kingdom sheet on the line's tab.
        val dispatcher = chatButtonDispatcher ?: return@ChatButton
        val nav = button.dataset["jumpValue"]?.let { MainNavEntry.fromString(it) }
        KingdomSheet(game, actor, dispatcher, initialNavEntry = nav).launch()
    },
    ChatButton("km-offer-irrigation-plague") { game, actor, event, button ->
        // Adds the Plague event after a failed Irrigation flat check. Resolved through getEvent so
        // an id the registry cannot resolve never becomes an invisible ongoing entry -- the same
        // guard the war-threat arrival offer uses.
        if (!game.user.isGM) return@ChatButton
        actor.getKingdom()?.let { kingdom ->
            val plague = kingdom.getEvent(PLAGUE_EVENT_ID)
            if (plague == null) {
                ui.notifications.error(t("kingdom.irrigation.plagueMissing"))
                return@ChatButton
            }
            kingdom.ongoingEvents = kingdom.ongoingEvents + RawOngoingKingdomEvent(
                stage = 0,
                id = PLAGUE_EVENT_ID,
            )
            actor.setKingdom(kingdom)
            postChatMessage(t("kingdom.irrigation.plagueAdded"))
        }
    },
    ChatButton("km-offer-liquidate-resources") { game, actor, event, button ->
        // "...you may instead reduce your RP to 1 and treat the expense as if it were paid in full.
        // At the start of your next Kingdom turn, roll 4 fewer Resource Dice than normal."
        if (!game.user.isGM) return@ChatButton
        actor.getKingdom()?.let { kingdom ->
            if (kingdom.liquidateUsedThisTurn()) {
                ui.notifications.warn(t("kingdom.liquidate.alreadyUsed"))
                return@ChatButton
            }
            kingdom.resourcePoints.now = liquidatedRp()
            kingdom.liquidateResourcesPenaltyNextTurn = true
            actor.setKingdom(kingdom)
            postChatMessage(
                t(
                    "kingdom.liquidate.applied",
                    recordOf(
                        "rp" to liquidatedRp(),
                        "dice" to LIQUIDATE_RESOURCES_NEXT_TURN_RD_PENALTY,
                    ),
                ),
            )
        }
    },
    ChatButton("km-offer-pull-together") { game, actor, event, button ->
        // "Once per Kingdom turn when you roll a critical failure ... attempt a DC 11 flat check.
        // If this succeeds ... treat the Kingdom skill check result as failure instead."
        if (!game.user.isGM) return@ChatButton
        val meta = button.closest(".chat-message")
            ?.querySelector(".km-upgrade-result")
            ?.takeIfInstance<HTMLElement>()
            ?.let { parseUpgradeMeta(it) }
            ?: return@ChatButton
        val kingdom = actor.getKingdom() ?: return@ChatButton
        if (!canUsePullTogether(
                isCriticalFailure = true,
                alreadyUsedThisTurn = kingdom.pullTogetherUsedThisTurn == true,
            )
        ) {
            ui.notifications.warn(t("kingdom.pullTogether.alreadyUsed"))
            return@ChatButton
        }
        val dc = kingdom.pullTogetherDc()
        val succeeded = d20Check(dc = dc, flavor = t("kingdom.pullTogether.flavor")).degreeOfSuccess.succeeded()
        // The DC climbs on every USE, per the feat text, whether or not the flat check landed.
        kingdom.pullTogetherUsedThisTurn = true
        kingdom.pullTogetherCurrentDC = pullTogetherDcAfterUse(dc)
        kingdom.pullTogetherTurnsSinceLastUsed = 0
        actor.setKingdom(kingdom)
        if (succeeded) {
            postChatMessage(t("kingdom.pullTogether.succeeded"))
            postComplexDegreeOfSuccess(meta, DegreeOfSuccess.FAILURE)
        } else {
            postChatMessage(t("kingdom.pullTogether.failed"))
        }
    },
    ChatButton("km-offer-caravan-recall") { game, actor, event, button ->
        // War was declared on a partner while this shipment was on the road. Recall turns it around
        // and brings the cargo home -- the card's rule is that nothing is silently confiscated.
        if (!game.user.isGM) return@ChatButton
        val caravanId = button.dataset["caravanId"] ?: return@ChatButton
        actor.getKingdom()?.let { kingdom ->
            if (!kingdom.recallCaravan(caravanId)) {
                ui.notifications.warn(t("kingdom.caravans.warOfferGone"))
                return@ChatButton
            }
            actor.setKingdom(kingdom)
            postChatMessage(t("kingdom.caravans.warOfferRecalled"))
        }
    },
    ChatButton("km-offer-caravan-press-on") { game, actor, event, button ->
        // Press on: the shipment stays in transit and the war's raid DC penalty applies to it on
        // every tick, exactly as it does for any other shipment to a hostile partner.
        if (!game.user.isGM) return@ChatButton
        val caravanId = button.dataset["caravanId"] ?: return@ChatButton
        val stillRunning = actor.getKingdom()
            ?.caravans
            ?.any { it.id == caravanId && it.status == "inTransit" } == true
        if (!stillRunning) {
            ui.notifications.warn(t("kingdom.caravans.warOfferGone"))
            return@ChatButton
        }
        postChatMessage(t("kingdom.caravans.warOfferPressedOn"))
    },
    ChatButton("km-spend-banked-aid") { game, actor, event, button ->
        // RAW, Request Foreign Aid's bonus is applied to a check you have already seen fail. This
        // spends one banked bonus against THIS card's roll and reposts the corrected degree.
        if (!game.user.isGM) return@ChatButton
        val bonusId = button.dataset["bonusId"] ?: return@ChatButton
        val meta = button.closest(".chat-message")
            ?.querySelector(".km-upgrade-result")
            ?.takeIfInstance<HTMLElement>()
            ?.let { parseUpgradeMeta(it) }
            ?: return@ChatButton
        val dc = meta.dc ?: return@ChatButton
        val total = meta.total ?: return@ChatButton
        val dieValue = meta.dieValue ?: return@ChatButton
        val kingdom = actor.getKingdom() ?: return@ChatButton
        val bonus = kingdom.bankedBonusIds().zip(kingdom.bankedBonusList())
            .firstOrNull { (id, _) -> id == bonusId }
            ?.second
        if (bonus == null) {
            // Another card already spent it: these offers sit in chat and the bank is shared.
            ui.notifications.warn(t("kingdom.bankedAid.gone"))
            return@ChatButton
        }
        // Re-checked at confirm time, not just when the card was posted.
        if (!aidWouldImprove(dc = dc, total = total, dieValue = dieValue, bonus = bonus.value)) {
            ui.notifications.warn(t("kingdom.bankedAid.noLongerHelps"))
            return@ChatButton
        }
        val improved = degreeAfterSpendingAid(dc = dc, total = total, dieValue = dieValue, bonus = bonus.value)
        kingdom.bankedBonuses = kingdom.withoutBankedBonus(bonusId)
        actor.setKingdom(kingdom)
        postChatMessage(
            t(
                "kingdom.bankedAid.spent",
                recordOf("value" to bonus.value, "degree" to t(improved)),
            ),
        )
        postComplexDegreeOfSuccess(meta, improved)
    },
    ChatButton("km-offer-deploy-army") { game, actor, event, button ->
        // GM-confirmed apply buttons for a resolved Deploy Army activity. The activity's own text
        // used to end "HP and Conditions need to be managed by hand"; these replace that.
        if (!game.user.isGM) return@ChatButton
        val choice = button.dataset["choice"] ?: return@ChatButton
        val armyActorUuid = button.dataset["armyActorUuid"] ?: return@ChatButton
        val cardId = button.dataset["cardId"] ?: return@ChatButton
        val amount = button.dataset["amount"]?.toIntOrNull() ?: 0
        if (choice == "dismiss") {
            postChatMessage(t("chatMessages.deployArmy.dismissed"))
            return@ChatButton
        }
        val army = fromUuidOfTypes(armyActorUuid, PF2EArmy::class)
        if (army == null) {
            ui.notifications.warn(t("chatMessages.deployArmy.noArmy"))
            return@ChatButton
        }
        if (choice in army.appliedDeployKeys(cardId)) {
            ui.notifications.warn(t("chatMessages.deployArmy.alreadyApplied"))
            return@ChatButton
        }
        val summary = when (choice) {
            DEPLOY_OFFER_FLAT_CHECK -> {
                // Rolled at confirm time, not when the card was posted, so the GM sees the die.
                val passed = d20Check(
                    dc = amount,
                    flavor = t("chatMessages.deployArmy.flatCheckFlavor", recordOf("army" to army.name)),
                ).degreeOfSuccess.succeeded()
                if (passed) {
                    t("chatMessages.deployArmy.flatCheckPassed", recordOf("army" to army.name))
                } else {
                    val hp = army.system.attributes.hp
                    val damaged = (hp.value - DEPLOY_ARMY_FLAT_CHECK_DAMAGE).coerceAtLeast(0)
                    val update = js("{}")
                    update["system.attributes.hp.value"] = damaged
                    army.update(update.unsafeCast<AnyObject>()).await()
                    t(
                        "chatMessages.deployArmy.flatCheckFailed",
                        recordOf("army" to army.name, "hp" to DEPLOY_ARMY_FLAT_CHECK_DAMAGE),
                    )
                }
            }

            DEPLOY_OFFER_UNREST -> {
                val gained = roll("1d4", flavor = t("chatMessages.deployArmy.unrestFlavor"))
                actor.getKingdom()?.let { kingdom ->
                    kingdom.unrest += gained
                    actor.setKingdom(kingdom)
                }
                t("chatMessages.deployArmy.unrestGained", recordOf("amount" to gained))
            }

            else -> {
                val effect = DeployArmyEffect.entries.find { it.slug == choice } ?: return@ChatButton
                if (!army.applyDeployEffect(effect)) {
                    ui.notifications.error(
                        t("chatMessages.deployArmy.effectMissing", recordOf("condition" to effect.slug)),
                    )
                    return@ChatButton
                }
                t(
                    "chatMessages.deployArmy.conditionApplied",
                    recordOf("condition" to t("armyConditionSlug.${effect.slug}"), "army" to army.name),
                )
            }
        }
        army.recordDeployKeyApplied(cardId, choice)
        postChatMessage(summary)
    },
    ChatButton("km-offer-war-victory") { game, actor, event, button ->
        // GM-confirmed offer posted when a war battle against a faction-linked threat resolves as
        // VICTORY. Winning previously moved nothing diplomatic: standing never shifted and atWar
        // stayed set with no way to clear it, so a won war never actually ended.
        if (!game.user.isGM) return@ChatButton
        val choice = button.dataset["choice"] ?: return@ChatButton
        val battleId = button.dataset["battleId"] ?: return@ChatButton
        val amount = button.dataset["amount"]?.toIntOrNull() ?: 0
        if (choice == "dismiss") {
            postChatMessage(t("chatMessages.warVictory.dismissed"))
            return@ChatButton
        }
        actor.getKingdom()?.let { kingdom ->
            val battle = kingdom.activeBattles?.find { it.id == battleId }
            if (battle == null) {
                ui.notifications.warn(t("chatMessages.warVictory.noBattle"))
                return@ChatButton
            }
            val applied = battle.victoryConsequencesApplied ?: emptyArray()
            if (choice in applied) {
                ui.notifications.warn(t("chatMessages.warVictory.alreadyApplied"))
                return@ChatButton
            }
            val threat = kingdom.warThreats?.find { it.id == battle.threatId }
            val faction = threat?.enemyFactionName
            if (faction == null) {
                ui.notifications.warn(t("chatMessages.warVictory.noFaction"))
                return@ChatButton
            }
            val group = kingdom.groups.find { it.name == faction }
            if (group == null) {
                ui.notifications.warn(t("chatMessages.warStanding.factionMissing", recordOf("faction" to faction)))
                return@ChatButton
            }
            val summary = when (choice) {
                VICTORY_OFFER_STANDING -> {
                    kingdom.applyWarStanding(faction, amount, WarStandingReason.VICTORY)
                    t("chatMessages.warVictory.standing", recordOf("faction" to faction, "amount" to amount))
                }

                VICTORY_OFFER_SIGN_PEACE, VICTORY_OFFER_DEMAND_TRIBUTE -> {
                    // Peace terms are only offerable while every linked threat is down. Re-check at
                    // confirm time: these cards sit in chat, and a new threat can arrive from the
                    // same faction before the GM clicks.
                    if (!peaceEligible(kingdom.threatStates(), faction)) {
                        ui.notifications.warn(t("chatMessages.warVictory.warStillOn", recordOf("faction" to faction)))
                        return@ChatButton
                    }
                    val peaceChoice = if (choice == VICTORY_OFFER_SIGN_PEACE) {
                        PeaceChoice.SIGN_PEACE
                    } else {
                        PeaceChoice.DEMAND_TRIBUTE
                    }
                    // Read standing NOW, not when the card was posted: another battle may have
                    // moved it since, and a delta from a stale snapshot would miss the floor.
                    val outcome = peaceOutcome(
                        choice = peaceChoice,
                        currentStanding = group.standing ?: 0,
                        standingFloor = kingdom.settings.peaceStandingFloorOrDefault(),
                        tributeRp = kingdom.settings.peaceTributeRpOrDefault(),
                    )
                    val reason = if (peaceChoice == PeaceChoice.SIGN_PEACE) {
                        WarStandingReason.PEACE
                    } else {
                        WarStandingReason.TRIBUTE
                    }
                    kingdom.applyWarStanding(faction, outcome.standingDelta, reason)
                    if (outcome.clearsFactionAtWar) {
                        group.atWar = false
                        // Stamp the war closed BEFORE deriving the kingdom flag, so this faction's
                        // threats no longer count as live, and so no second card can re-conclude it.
                        kingdom.settlePeaceWith(faction)
                        // The kingdom-wide flag carries +1 unrest per turn. Only ever CLEAR it, and
                        // only when nothing else is running: a GM may have ticked that box for a war
                        // this subsystem cannot see, and peace with one enemy must not cancel it.
                        if (kingdom.atWar && !kingdom.kingdomStillAtWarWithout(faction)) {
                            kingdom.atWar = false
                        }
                    }
                    if (outcome.rpGain > 0) {
                        kingdom.resourcePoints.now += outcome.rpGain
                    }
                    if (peaceChoice == PeaceChoice.SIGN_PEACE) {
                        t("chatMessages.warVictory.peaceSigned", recordOf("faction" to faction))
                    } else {
                        t(
                            "chatMessages.warVictory.tributeTaken",
                            recordOf("faction" to faction, "rp" to outcome.rpGain),
                        )
                    }
                }

                else -> return@ChatButton
            }
            battle.victoryConsequencesApplied = applied + choice
            kingdom.activeBattles = kingdom.activeBattles
                ?.map { if (it.id == battleId) battle else it }
                ?.toTypedArray() ?: emptyArray()
            actor.setKingdom(kingdom)
            postChatMessage(summary)
        }
    },
    ChatButton("km-offer-battle-defeat") { game, actor, event, button ->
        // GM-confirmed offer posted when a war battle resolves as DEFEAT. Each button applies
        // exactly the one delta it was labelled with — never the whole set — and records its key
        // in the battle's defeatConsequencesApplied so re-clicking it (or a re-posted card) is a
        // no-op. Without this the DEFEAT branch was a pure no-op: losing every army to an
        // invasion left the threat active and unchanged.
        if (!game.user.isGM) return@ChatButton
        val choice = button.dataset["choice"] ?: return@ChatButton
        val battleId = button.dataset["battleId"] ?: return@ChatButton
        val amount = button.dataset["amount"]?.toIntOrNull() ?: 0
        actor.getKingdom()?.let { kingdom ->
            val battle = kingdom.activeBattles?.find { it.id == battleId }
            if (battle == null) {
                ui.notifications.warn(t("chatMessages.battleDefeat.noBattle"))
                return@ChatButton
            }
            val applied = battle.defeatConsequencesApplied ?: emptyArray()
            if (choice == "dismiss") {
                postChatMessage(t("chatMessages.battleDefeat.dismissed"))
                return@ChatButton
            }
            if (choice in applied) {
                ui.notifications.warn(t("chatMessages.battleDefeat.alreadyApplied"))
                return@ChatButton
            }
            val threat = kingdom.warThreats?.find { it.id == battle.threatId }
            when (choice) {
                DEFEAT_OFFER_UNREST -> kingdom.unrest += amount
                DEFEAT_OFFER_PRESSURE -> {
                    val pressure = kingdom.warPressure ?: return@ChatButton
                    pressure.currentPressure += amount
                }
                DEFEAT_OFFER_ESCALATION -> {
                    if (threat == null) {
                        ui.notifications.warn(t("chatMessages.battleDefeat.noThreat"))
                        return@ChatButton
                    }
                    val raised = (threat.escalationLevel + amount).coerceAtMost(threat.maxEscalation)
                    val escalated = threat.copyWith(escalationLevel = raised)
                    kingdom.warThreats = kingdom.warThreats
                        ?.map { if (it.id == threat.id) escalated else it }
                        ?.toTypedArray() ?: emptyArray()
                }
                DEFEAT_OFFER_STANDING -> {
                    val faction = threat?.enemyFactionName
                    if (faction == null) {
                        ui.notifications.warn(t("chatMessages.battleDefeat.noThreat"))
                        return@ChatButton
                    }
                    if (!kingdom.applyWarStanding(faction, amount, WarStandingReason.DEFEAT)) {
                        ui.notifications.warn(
                            t("chatMessages.warStanding.factionMissing", recordOf("faction" to faction)),
                        )
                        return@ChatButton
                    }
                }
                DEFEAT_OFFER_ARRIVAL -> {
                    if (threat == null) {
                        ui.notifications.warn(t("chatMessages.battleDefeat.noThreat"))
                        return@ChatButton
                    }
                    // Same spawn path as km-offer-war-threat-arrival: resolve through getEvent()
                    // so an id the registry cannot resolve never becomes an invisible ongoing entry.
                    val eventId = "war-threat-arrival"
                    val kingdomEvent = kingdom.getEvent(eventId)
                    if (kingdomEvent == null) {
                        ui.notifications.error(t("chatMessages.warThreatArrival.eventMissing"))
                        return@ChatButton
                    }
                    val ongoingEvent = if (KingdomEventTrait.SETTLEMENT.value in kingdomEvent.traits) {
                        val pick = pickEventSettlement(kingdom.getAllSettlements(game).allSettlements)
                        RawOngoingKingdomEvent(
                            stage = 0,
                            id = eventId,
                            settlementSceneId = pick.settlementId,
                            secretLocation = pick.secretLocation,
                        )
                    } else {
                        RawOngoingKingdomEvent(stage = 0, id = eventId)
                    }
                    kingdom.ongoingEvents = kingdom.ongoingEvents + ongoingEvent
                }
                else -> return@ChatButton
            }
            battle.defeatConsequencesApplied = applied + choice
            kingdom.activeBattles = kingdom.activeBattles
                ?.map { if (it.id == battleId) battle else it }
                ?.toTypedArray() ?: emptyArray()
            actor.setKingdom(kingdom)
            postChatMessage(
                t(
                    "chatMessages.battleDefeat.applied",
                    // The standing line also needs `faction`. Omitting it made ICU throw, and
                    // i18next-icu's default error handler returns the RAW pattern -- the GM saw a
                    // literal "Standing with {faction}: {amount}", losing the number too.
                    recordOf(
                        "consequence" to t(
                            "chatMessages.battleDefeat.$choice",
                            recordOf("amount" to amount, "faction" to (threat?.enemyFactionName ?: "")),
                        ),
                    ),
                )
            )
        }
    },
    ChatButton("km-offer-diplomacy-quest") { game, actor, event, button ->
        // GM-confirmed offer from a faction-standing threshold crossing (#1 → #2).
        // Opens the AddQuest dialog prefilled with the faction as giver.
        // Party actors are owner-permissioned to players, so the isGM check is the real guard.
        if (!game.user.isGM) return@ChatButton
        val faction = button.dataset["faction"] ?: ""
        AddQuest(
            prefillTitle = t("chatMessages.endTurn.diplomacyQuestTitle", recordOf("group" to faction)),
            prefillGiver = faction,
            settlements = actor.getKingdom()
                ?.let { k -> k.getAllSettlements(game).allSettlements.map { it.id to it.name } }
                ?: emptyList(),
        ) { quest ->
            actor.getKingdom()?.let { kingdom ->
                kingdom.quests = (kingdom.quests ?: emptyArray()) + quest
                actor.setKingdom(kingdom)
            }
        }.launch()
    },
    ChatButton("km-offer-expedition-reward") { game, actor, event, button ->
        // GM-confirmed: apply expedition reward (XP, influence, loot, mark resolved).
        // Guards double-apply by checking rewardApplied + status.
        // GM-confirmed: apply the accrued expedition reward + mark resolved (idempotent).
        if (!game.user.isGM) return@ChatButton
        // Every failure below used to return silently — the button simply did nothing and the GM
        // had no way to tell why. Surface each case instead.
        val expeditionId = button.dataset["expeditionId"]
        if (expeditionId.isNullOrBlank()) {
            ui.notifications.warn(t("kingdom.expeditionRewardMissing"))
            return@ChatButton
        }
        val kingdomData = actor.getKingdom()
        if (kingdomData == null) {
            ui.notifications.warn(t("kingdom.expeditionRewardMissing"))
            return@ChatButton
        }
        kingdomData.let { kingdom ->
            val expedition = kingdom.companionExpeditions?.find { it.id == expeditionId }
            if (expedition == null) {
                ui.notifications.warn(t("kingdom.expeditionRewardMissing"))
                return@ChatButton
            }
            if (applyExpeditionRewardToKingdom(kingdom, expedition)) {
                actor.setKingdom(kingdom)
                game.proposeXpOffer(
                    kind = at.posselt.pfrpg2e.kingdom.xp.XpSourceKind.EXPEDITION_RESOLVED,
                    sourceRef = expedition.id,
                    turn = kingdom.currentTurn ?: 0,
                    note = expedition.title,
                )
                postChatMessage(t("kingdom.expeditionRewardApplied", recordOf("name" to expedition.title)))
            } else {
                ui.notifications.warn(
                    t("kingdom.expeditionRewardAlreadyApplied", recordOf("name" to expedition.title))
                )
            }
        }
    },
    ChatButton("km-offer-companion-levelup") { game, actor, event, button ->
        // GM-confirmed, separate offer: advance the linked PF2e actor's REAL level.
        // The shadow companion.level is advanced by Apply Reward (applyCompanionXp);
        // this button is the explicit, never-silent offer to bump the real actor.
        if (!game.user.isGM) return@ChatButton
        // data-companion-id carries the companion's actorUuid (blank when unlinked).
        val companionActorUuid = button.dataset["companionId"]?.takeIf { it.isNotBlank() } ?: return@ChatButton
        val targetLevel = button.dataset["targetLevel"]?.toIntOrNull()?.let { min(20, it) } ?: return@ChatButton
        if (!Pfrpg2eKingdomCampingWeatherSettings.getEnableCompanionLeveling()) return@ChatButton

        val linkedActor = fromUuidOfTypes(companionActorUuid, PF2ECharacter::class) ?: return@ChatButton
        // Expedition rewards now add XP to the real sheet, so a level-up must SPEND it the way a
        // normal PF2e level-up does — consume one threshold and carry the remainder — otherwise
        // the character would level while the XP bar stayed full and looked ready to level again.
        val threshold = linkedActor.system.details.xp.max
        val carriedXp = (linkedActor.system.details.xp.value - threshold).coerceAtLeast(0)
        linkedActor.typeSafeUpdate {
            system.details.level.value = targetLevel
            system.details.xp.value = carriedXp
        }
        postChatMessage(t("kingdom.companionLeveledUp", recordOf("name" to linkedActor.name, "level" to targetLevel)))
    },
    ChatButton("km-offer-injury") { game, actor, event, button ->
        // GM-confirmed: apply injury conditions to the companion (actor-linked only).
        if (!game.user.isGM) return@ChatButton
        val expeditionId = button.dataset["expeditionId"] ?: return@ChatButton
        // data-companion-id carries the companion's actorUuid; blank when unlinked (injury is actor-linked only).
        val companionId = button.dataset["companionId"]?.takeIf { it.isNotBlank() } ?: return@ChatButton

        actor.getKingdom()?.let { kingdom ->
            val expedition = kingdom.companionExpeditions?.find { it.id == expeditionId } ?: return@ChatButton
            val companion = kingdom.companions?.find { it.actorUuid == companionId } ?: return@ChatButton

            // Apply the rolled injury conditions to the linked PF2e actor. The engine rolls these
            // (fatigued + wounded on a critical failure) and the offer card displays them, but
            // nothing ever put them on the character sheet — the injury was module-side downtime
            // only, so "Apply Injury" left the actor untouched.
            // Valued conditions (wounded) increase; binary ones (fatigued) toggle once — applying
            // a binary condition through increaseCondition is the misuse fixed in bd3738f1.
            val injuredActor = fromUuidOfTypes(companionId, PF2ECharacter::class, PF2ENpc::class)
            if (injuredActor != null) {
                expedition.accruedInjuries.forEach { condition ->
                    if (isValuedCondition(condition)) {
                        injuredActor.increaseCondition(condition)
                    } else if (!injuredActor.hasCondition(condition)) {
                        injuredActor.toggleCondition(condition)
                    }
                }
            }

            // Time-boxed downtime: recovery days depend on the expedition tier
            // (routine 2 / standard 3 / perilous 5), decremented by the daily tick.
            companion.injuryDaysRemaining = when (expedition.tier) {
                "routine" -> 2
                "perilous" -> 5
                else -> 3
            }
            companion.expeditionStatus = "unavailable"
            companion.campAvailable = false

            // Release the OTHER participants so they can't be stranded "onExpedition" if the GM
            // never applies the reward. Only the injured companion goes into downtime.
            expedition.companionIds
                .filter { it != companionId }
                .forEach { participantId ->
                    kingdom.companions
                        ?.find { (it.actorUuid ?: it.name) == participantId }
                        ?.expeditionStatus = "available"
                }

            // A scar is recorded when an injury is actually APPLIED — only for the injured
            // companion (the career ledger deliberately does not count waived offers).
            companion.careerScars = (companion.careerScars ?: 0) + 1

            // Injury and reward are INDEPENDENT offers on the same card (see
            // applyExpeditionRewardToKingdom's KDoc). This path deliberately does NOT mark the
            // expedition resolved/rewardApplied: doing so made Apply Reward a permanent SILENT
            // no-op whenever the GM clicked Apply Injury first, forfeiting all XP/influence/loot.
            // Apply Reward stays available and owns the durable history record; it preserves this
            // companion's downtime rather than flipping them back to "available".

            kingdom.companionExpeditions = kingdom.companionExpeditions?.map {
                if (it.id == expeditionId) expedition else it
            }?.toTypedArray()
            actor.setKingdom(kingdom)

            postChatMessage(t("kingdom.companionInjured", recordOf("name" to companion.name)))
        }
    },
    ChatButton("km-apply-modifier-effect") { game, actor, event, button ->
        val mod = deserializeB64Json<RawModifier>(button.dataset["data"] ?: "")
        val jsonMod = JSON.stringify(mod)
        val results = validateUsingSchema(parsedModifierSchema, parseToJsonElement(jsonMod))
        if (results.isNotEmpty()) {
            ui.notifications.error(t("kingdom.modifierValidationFailed"))
            console.error(results.toTypedArray())
        } else {
            val parsedMod = if (mod.rollOptions.orEmpty().contains("focused-attention")) {
                val leader = pickLeader()
                RawModifier.copy(mod, applyIf = mod.applyIf.orEmpty() + RawEq(eq = tupleOf("@leader", leader.value)))
            } else {
                mod
            }
            actor.getKingdom()?.let { kingdom ->
                kingdom.modifiers = kingdom.modifiers +
                        RawModifier.copy(parsedMod, id = v4())
                actor.setKingdom(kingdom)
                parsedMod.buttonLabel?.let { key ->
                    postChatMessage(t("kingdom.addedModifier", recordOf("name" to t(key))))
                }
            }
        }
    },
    ChatButton("km-offer-companion-autonomy") { game, actor, event, button ->
        if (!game.user.isGM) return@ChatButton
        val approve = button.dataset["approve"] == "true"
        val sendElsewhere = button.dataset["sendElsewhere"] == "true"
        val decline = button.dataset["decline"] == "true"

        if (approve) {
            // Approve: assign the top-ranked volunteer to a new expedition with a concrete proposal
            val kingdom = actor.getKingdom() ?: return@ChatButton
            val companions = kingdom.companions ?: return@ChatButton
            // Honor the PITCHED identity embedded in the offer card: recomputing at click time
            // can resolve a different volunteer/quest than the one the GM read and approved.
            val pinnedKey = button.dataset["volunteerKey"]?.takeIf { it.isNotBlank() }
            val topPick = pinnedKey?.let { pk -> companions.find { (it.actorUuid ?: it.name) == pk } }
                ?: CompanionAutonomy.selectAutonomousCompanions(companions.toList()).firstOrNull()
            if (topPick != null) {
                val key = topPick.actorUuid ?: topPick.name
                val proposal = computeAutonomousProposal(topPick, kingdom.companionPersonalQuests ?: emptyArray())
                val pinnedActivityId = button.dataset["activityId"]?.takeIf { it.isNotBlank() } ?: proposal.activityId
                val pinnedQuestId = button.dataset["questId"]?.takeIf { it.isNotBlank() } ?: proposal.targetQuestId
                AddExpeditionDialog(
                    companions = companions,
                    preselectedId = key,
                    preselectedActivityId = pinnedActivityId,
                    preselectedQuestId = pinnedQuestId,
                    quests = kingdom.companionPersonalQuests ?: emptyArray(),
                    factions = kingdom.groups,
                    destinations = buildExpeditionDestinationOptions(kingdom),
                ) { expedition ->
                    val current = actor.getKingdom() ?: return@AddExpeditionDialog false
                    val updatedComps = (current.companions ?: emptyArray()).copyOf()
                    if (launchExpedition(current, expedition, updatedComps)) {
                        current.companions = updatedComps
                        actor.setKingdom(current)
                        logExpeditionLaunched(expedition, updatedComps)
                        postChatTemplate(
                            templatePath = "chatmessages/companion-autonomy-approved.hbs",
                            templateContext = js.objects.recordOf(
                                "name" to topPick.name,
                            )
                        )
                        true
                    } else {
                        ui.notifications.warn(t("kingdom.expeditions.tooMany"))
                        false
                    }
                }.launch()
            }
        } else if (decline) {
            postChatMessage(t("chatMessages.companionAutonomy.declinedMessage"))
        } else if (sendElsewhere) {
            val kingdom = actor.getKingdom() ?: return@ChatButton
            val companions = kingdom.companions ?: return@ChatButton
            val volunteers = CompanionAutonomy.selectAutonomousCompanions(companions.toList())
            val topPick = volunteers.firstOrNull()
            val proposal = topPick?.let { computeAutonomousProposal(it, kingdom.companionPersonalQuests ?: emptyArray()) }
            AddExpeditionDialog(
                companions = companions,
                preselectedId = topPick?.let { it.actorUuid ?: it.name },
                preselectedActivityId = proposal?.activityId,
                preselectedQuestId = proposal?.targetQuestId,
                quests = kingdom.companionPersonalQuests ?: emptyArray(),
                factions = kingdom.groups,
                destinations = buildExpeditionDestinationOptions(kingdom),
            ) { expedition ->
                val current = actor.getKingdom() ?: return@AddExpeditionDialog false
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
    },
    ChatButton("km-offer-quest-deadline") { game, actor, event, button ->
        // GM-confirmed offer for a quest that has reached its deadline.
        // Buttons: [Fail Now] (data-action="fail") or [Extend N Turns] (data-action="extend" + data-extend-turns).
        if (!game.user.isGM) return@ChatButton
        val action = button.dataset["action"] ?: return@ChatButton
        val questId = button.dataset["questId"] ?: return@ChatButton
        val extendTurns = button.dataset["extendTurns"]?.toIntOrNull() ?: DEFAULT_QUEST_EXTEND_TURNS

        actor.getKingdom()?.let { kingdom ->
            val quest = kingdom.campaignQuests?.find { it.id == questId } ?: return@ChatButton
            when (action) {
                "fail" -> {
                    // GM chose to fail the quest — mark as failed
                    quest.status = "failed"
                    quest.turnsRemaining = 0
                    actor.setKingdom(kingdom)
                    postChatMessage(t("chatMessages.questDeadline.failed", recordOf("name" to quest.title)))
                }
                "extend" -> {
                    // GM chose to extend the deadline
                    quest.turnsRemaining = extendTurns
                    actor.setKingdom(kingdom)
                    postChatMessage(t("chatMessages.questDeadline.extended", recordOf("name" to quest.title, "turns" to extendTurns.toString())))
                }
            }
        }
    },
    ChatButton("km-offer-army-levelup") { game, actor, event, button ->
        // GM-confirmed: apply level-up to the PF2EArmy actor.
        if (!game.user.isGM) return@ChatButton
        val approve = button.dataset["approve"] == "true"
        val decline = button.dataset["decline"] == "true"
        val armyActorUuid = button.dataset["armyActorUuid"] ?: return@ChatButton
        val armyName = button.dataset["armyName"] ?: ""
        val targetLevel = button.dataset["targetLevel"]?.toIntOrNull() ?: return@ChatButton

        if (decline) {
            postChatMessage(t("warBattle.levelUpOffer.dismissed", recordOf("name" to armyName)))
            return@ChatButton
        }
        if (!approve) return@ChatButton

        val armyActor = fromUuidOfTypes(armyActorUuid, PF2EArmy::class) ?: return@ChatButton

        // Apply level-up: increase level, HP, and carry over excess XP
        val currentLevel = armyActor.system.details.level.value
        val currentXp = armyActor.getAppFlag<PF2EArmy, Int>("xp") ?: 0
        val threshold = xpThresholdForLevel(currentLevel)
        if (currentXp < threshold || currentLevel >= 20) return@ChatButton

        val (leveledUp, remainingXp) = applyLevelUp(
            BattleArmyState(
                name = armyActor.name,
                level = currentLevel,
                currentHp = armyActor.system.attributes.hp.value,
                maxHp = armyActor.system.attributes.hp.max,
                conditions = emptySet(),
                attackBonus = 0,
                // Army AC is the TOP-LEVEL system.ac (the old attributes.ac path never existed
                // and would have thrown on click).
                ac = armyActor.system.ac.value + armyActor.system.ac.potency,
                routThreshold = 0,
                xp = currentXp,
            ),
            currentXp,
            threshold,
        )

        // Update actor level
        armyActor.typeSafeUpdate {
            system.details.level.value = leveledUp.level
            system.attributes.hp.value = leveledUp.currentHp
            system.attributes.hp.max = leveledUp.maxHp
        }

        // Persist remaining XP
        armyActor.setAppFlag("xp", remainingXp)

        postChatMessage(t("warBattle.levelUpOffer.leveledUp", recordOf("name" to armyName, "level" to leveledUp.level)))
    },
    ChatButton("km-undo-end-turn") { game, actor, event, button ->
        // GM-only: revert the most recent End Turn via the shared undoEndTurn (restores kingdom +
        // turn-wizard-state + deletes delivered shipment items, so undo is exact and re-running
        // End Turn cannot double-deliver).
        if (!game.user.isGM) return@ChatButton
        val snap = actor.getAppFlag<KingdomActor, Any?>("lastTurnSnapshot")?.unsafeCast<EndTurnSnapshot>()
        if (snap == null) {
            ui.notifications.warn(t("chatMessages.endTurn.undoNothing"))
            return@ChatButton
        }
        // A stale card (from an older End Turn) carries a different snapshot-turn than the live
        // snapshot; clicking it would revert the LATEST turn, not the one the card shows — refuse.
        val cardTurn = button.dataset["snapshotTurn"]?.toIntOrNull()
        if (cardTurn != null && cardTurn != snap.snapshotTurn) {
            ui.notifications.warn(t("chatMessages.endTurn.undoStale"))
            return@ChatButton
        }
        if (!undoEndTurn(game, actor)) {
            ui.notifications.warn(t("chatMessages.endTurn.undoStale"))
        }
    },
    ChatButton("km-offer-milestone") { game, actor, _, button ->
        // GM-confirmed award for an auto-detected milestone (road-to-capital / region-claimed).
        // Awarding flips its MilestoneChoice to completed — seeding a completed=false entry first for
        // kingdoms that never carried the choice — then runs beforeKingdomUpdate so the milestone-XP
        // delta and level-threshold handling apply exactly the same way the sheet does. Idempotent:
        // an already-completed milestone is a no-op, so the offer can be clicked only once to effect.
        if (!game.user.isGM) return@ChatButton
        val action = button.dataset["action"] ?: return@ChatButton
        val milestoneId = button.dataset["milestoneId"] ?: return@ChatButton
        val kingdom = actor.getKingdom() ?: return@ChatButton
        val milestone = kingdom.getMilestones().find { it.id == milestoneId } ?: return@ChatButton
        if (action == "dismiss") {
            // Record the refusal. Detection is level-triggered on standing world state, so without
            // persisting this the identical offer re-posts on every subsequent End Turn forever.
            val existingChoice = kingdom.milestones.find { it.id == milestoneId }
            if (existingChoice?.offerDismissed != true) {
                kingdom.milestones = if (existingChoice == null) {
                    kingdom.milestones + MilestoneChoice(
                        id = milestoneId,
                        completed = false,
                        enabled = true,
                        offerDismissed = true,
                    )
                } else {
                    kingdom.milestones.map {
                        if (it.id == milestoneId) MilestoneChoice.copy(it, offerDismissed = true) else it
                    }.toTypedArray()
                }
                actor.setKingdom(kingdom)
            }
            postChatMessage(t("chatMessages.milestone.dismissed", recordOf("name" to milestone.name)))
            return@ChatButton
        }
        if (action != "award") return@ChatButton
        // The deeds digest presents a whole column of Award buttons, and setKingdom suspends: two
        // quick clicks each read the kingdom BEFORE the other's write landed, so the second
        // silently discarded the first award. Serialize the read-modify-write.
        // withLock is INLINE, so return@withLock leaves the lambda and falls straight through to
        // whatever follows -- it does not leave the handler. The award message therefore has to
        // be gated on what the locked block actually did, not merely placed after it.
        var awarded = false
        councilVoteMutex.withLock {
        val kingdom = actor.getKingdom() ?: return@withLock
        val existing = kingdom.milestones.find { it.id == milestoneId }
        if (existing?.completed == true) {
            // already ticked, here or on the sheet: say so rather than announcing it twice
            ui.notifications.info(
                t("chatMessages.milestone.alreadyAwarded", recordOf("name" to milestone.name))
            )
            return@withLock
        }
        if (existing == null) {
            kingdom.milestones = kingdom.milestones + MilestoneChoice(
                id = milestoneId,
                completed = false,
                enabled = true,
                offerDismissed = false,
            )
        }
        val previous = deepClone(kingdom)
        // the turn the deed was earned on: the record this End Turn just wrote, so the Chronicle
        // can date it. Hand-ticked milestones keep a null turn rather than claiming a wrong one.
        val awardTurn = kingdom.turnHistory?.lastOrNull()?.turn ?: kingdom.currentTurn
        kingdom.milestones = kingdom.milestones.map {
            if (it.id == milestoneId) {
                MilestoneChoice.copy(it, completed = true, enabled = true, awardedOnTurn = awardTurn)
            } else it
        }.toTypedArray()
        // the players' celebration beat: only AWARDED deeds reach the gazette, so a GM who
        // declines one does not announce it to the table anyway (plan 7)
        appendDeedGazetteLine(kingdom, milestone.name)
        beforeKingdomUpdate(previous, kingdom)
        actor.setKingdom(kingdom)
        awarded = true
        }
        if (awarded) {
            postChatMessage(
                t("chatMessages.milestone.awarded", recordOf("name" to milestone.name, "xp" to milestone.xp)),
            )
        }
    },
    ChatButton("km-offer-milestone-dismiss-all") { game, actor, _, button ->
        // Adopting the module mid-campaign fires a dozen true deeds at once; without this the GM
        // faces a dozen individual dismissals to silence them (plan 7).
        if (!game.user.isGM) return@ChatButton
        val ids = button.dataset["allIds"]?.split(",")?.filter { it.isNotBlank() } ?: return@ChatButton
        var dismissed = false
        councilVoteMutex.withLock {
        val kingdom = actor.getKingdom() ?: return@withLock
        val existing = kingdom.milestones.associateBy { it.id }
        val missing = ids.filter { it !in existing }
            .map { MilestoneChoice(id = it, completed = false, enabled = true, offerDismissed = true) }
        kingdom.milestones = (kingdom.milestones.map {
            // an ALREADY AWARDED deed is left alone: dismiss-all silences offers, it never
            // un-awards XP the GM already granted
            if (it.id in ids && !it.completed) MilestoneChoice.copy(it, offerDismissed = true) else it
        } + missing).toTypedArray()
        actor.setKingdom(kingdom)
        dismissed = true
        }
        if (dismissed) {
            postChatMessage(t("chatMessages.milestone.dismissedAll", recordOf("count" to ids.size)))
        }
    },
    // Jump-to-settlement on the pacing-alert CHAT card. It MUST be a ChatButton (bound to #chat by
    // CSS class) — the sheet-panel copy uses data-action/_onClickAction, but that only fires inside
    // the sheet DOM, so a chat-card button needs its own class-based handler here. data-id is the
    // settlement's scene id (Settlement.id == sceneId), matching the sheet's game.scenes.get(id).view().
    // NOTE: "mark pending encounter as run" is handled by the kingdom sheet's _onClickAction
    // ("mark-pending-encounter-run"), because the button only ever renders in the Session Prep
    // sheet DOM — a ChatButton (bound to the #chat sidebar) never receives its click.
)

/**
 * Actor-independent chat buttons (plan section 5.3): the subsystem store is world-scoped, so
 * these cards have no [data-kingdom-actor-uuid] for findKingdomActor to read -- putting them in
 * [buttons] would mean they silently never fire in a Kingdom-less campaign.
 */
private data class WorldChatButton(
    val buttonClass: String,
    val callback: suspend (game: Game, event: Event, button: HTMLElement) -> Unit,
)

private val worldButtons = listOf(
    WorldChatButton("km-offer-influence-threshold") { game, _, button ->
        handleSubsystemThresholdOffer(game, button)
    },
    WorldChatButton("km-offer-research-threshold") { game, _, button ->
        handleSubsystemThresholdOffer(game, button)
    },
    // Jumping to a scene needs no kingdom. It was actor-bound, and the only card that renders it
    // (pacing-alert.hbs) carries no data-kingdom-actor-uuid, so findKingdomActor returned null and
    // the button warned "no kingdom" instead of viewing the scene. The handler never touched the
    // actor -- the dependency was false, not merely unsatisfied.
    WorldChatButton("km-view-settlement") { game, _, button ->
        val id = button.dataset["id"] ?: return@WorldChatButton
        game.scenes.get(id)?.view()
    },
)

/** More than one kingdom in the world: the GM says which one receives the quest. */
private suspend fun pickKingdomActor(actors: List<KingdomActor>): KingdomActor? =
    awaitablePrompt<PickKingdomActorData, KingdomActor?>(
        title = t("subsystems.offer.pickKingdom"),
        templatePath = "components/forms/form.hbs",
        templateContext = recordOf(
            "formRows" to formContext(
                Select(
                    name = "index",
                    label = t("subsystems.offer.pickKingdom"),
                    value = "0",
                    options = actors.mapIndexed { index, actor ->
                        SelectOption(value = index.toString(), label = actor.name)
                    },
                )
            )
        ),
    ) { data, _ -> actors.getOrNull(data.index.toIntOrNull() ?: -1) }

private external interface PickKingdomActorData {
    val index: String
}

/**
 * Grant / Convert to Quest / Dismiss on a threshold offer card. Grant posts the PINNED effect
 * text publicly (locale- and edit-safe) and consumes the offer; Dismiss consumes silently;
 * Convert opens AddQuest against a GM-chosen kingdom and deliberately does NOT consume -- the
 * plan only specifies consumption for Grant and Dismiss.
 */
private suspend fun handleSubsystemThresholdOffer(game: Game, button: HTMLElement) {
    if (!game.user.isGM) return
    val storeKind = button.dataset["store"] ?: return
    val entryId = button.dataset["entryId"] ?: return
    val index = button.dataset["thresholdIndex"]?.toIntOrNull() ?: return
    val points = button.dataset["thresholdPoints"]?.toIntOrNull() ?: return
    val effect = button.dataset["effect"] ?: ""
    val entryName = button.dataset["entryName"] ?: ""
    when (button.dataset["action"]) {
        "grant", "dismiss" -> {
            val store = game.getSubsystemStore()
            val rows = if (storeKind == "influence") {
                store.influenceEncounters?.firstOrNull { it.id == entryId }?.thresholds
            } else {
                store.researchProjects?.firstOrNull { it.id == entryId }?.thresholds
            }
            // identity is the row index the card was minted for; the points cross-check makes a
            // card stale (not misdirected) when the GM re-authors thresholds under it
            val row = rows?.getOrNull(index)
            val open = row != null && row.points == points && row.offerConsumed != true
            if (!open) {
                // answered from another card or another GM: a consumed threshold is a no-op
                ui.notifications.info(t("subsystems.offer.alreadyAnswered"))
                return
            }
            consumeSubsystemThreshold(game, storeKind, entryId, index, points)
            if (button.dataset["action"] == "grant") {
                postChatMessage(
                    t(
                        "subsystems.offer.granted",
                        recordOf("name" to entryName, "points" to points, "effect" to effect),
                    )
                )
            }
        }

        "convert" -> {
            val actors = game.getKingdomActors()
            val actor = when {
                actors.isEmpty() -> {
                    ui.notifications.warn(t("kingdom.chatButtonNoKingdom"))
                    return
                }
                actors.size == 1 -> actors.first()
                else -> pickKingdomActor(actors) ?: return
            }
            AddQuest(
                prefillTitle = effect,
                prefillGiver = entryName,
                settlements = actor.getKingdom()
                    ?.let { k -> k.getAllSettlements(game).allSettlements.map { it.id to it.name } }
                    ?: emptyList(),
            ) { quest ->
                actor.getKingdom()?.let { kingdom ->
                    kingdom.quests = (kingdom.quests ?: emptyArray()) + quest
                    actor.setKingdom(kingdom)
                }
            }.launch()
        }
    }
}

/** Consumes exactly the row the card was minted for; a points mismatch means a stale card. */
private suspend fun consumeSubsystemThreshold(game: Game, storeKind: String, entryId: String, index: Int, points: Int) {
    fun consume(rows: Array<RawSubsystemThreshold>?): Array<RawSubsystemThreshold>? =
        rows?.mapIndexed { i, row ->
            if (i == index && row.points == points && row.offerConsumed != true) {
                RawSubsystemThreshold.copy(row, offerConsumed = true)
            } else row
        }?.toTypedArray()
    game.updateSubsystemStore { store ->
        if (storeKind == "influence") {
            store.influenceEncounters = store.influenceEncounters?.map {
                if (it.id == entryId) at.posselt.pfrpg2e.kingdom.data.RawInfluenceEncounter.copy(it, thresholds = consume(it.thresholds)) else it
            }?.toTypedArray()
        } else {
            store.researchProjects = store.researchProjects?.map {
                if (it.id == entryId) at.posselt.pfrpg2e.kingdom.data.RawResearchProject.copy(it, thresholds = consume(it.thresholds)) else it
            }?.toTypedArray()
        }
        store
    }
}

/**
 * Set by [bindChatButtons]; the km-ping-jump handler needs it to construct a KingdomSheet.
 * ChatButton callbacks run on the CLICKING user's client, so this is that client's dispatcher.
 */
private var chatButtonDispatcher: ActionDispatcher? = null

fun bindChatButtons(game: Game, dispatcher: ActionDispatcher? = null) {
    chatButtonDispatcher = dispatcher
    TypedHooks.onRenderChatLog { application, _, data ->
        buttons.forEach { data ->
            bindChatClick(".${data.buttonClass}") { ev, target, parent ->
                buildPromise {
                    val kingdomActor = parent.findKingdomActor(game)
                    if (kingdomActor == null) {
                        // The uuid attribute failed to resolve to a kingdom actor. Previously the
                        // callback simply never ran and nothing anywhere reported it.
                        ui.notifications.warn(t("kingdom.chatButtonNoKingdom"))
                    } else {
                        data.callback(game, kingdomActor, ev, target)
                    }
                }.catch { e ->
                    // buildPromise's result was discarded, so a throw inside any offer handler was
                    // an unhandled rejection visible only in the console — another dead button.
                    console.error("kingdom chat button '${data.buttonClass}' failed", e)
                    ui.notifications.error(t("kingdom.chatButtonFailed"))
                    null
                }
            }
        }
        worldButtons.forEach { data ->
            // deliberately NO findKingdomActor gate: these cards are world-scoped (plan 5.3)
            bindChatClick(".${data.buttonClass}") { ev, target, _ ->
                buildPromise {
                    data.callback(game, ev, target)
                }.catch { e ->
                    console.error("kingdom chat button '${data.buttonClass}' failed", e)
                    ui.notifications.error(t("kingdom.chatButtonFailed"))
                    null
                }
            }
        }
    }
}
/** Cosmetic, per-client: fades one faction-move row; the real guard is the standing log. */
private fun markFactionMoveRowDone(button: HTMLElement) {
    // Disable ONLY the standing-shift pair. A row can also carry a war-threat and a
    // diplomacy-quest offer, and answering the shift is not answering those -- greying the whole
    // row silently threw away two offers the GM never saw a chance to take.
    val row = button.closest(".km-faction-move-row") as? HTMLElement ?: return
    row.querySelectorAll(".km-offer-faction-standing-shift").asList()
        .filterIsInstance<HTMLElement>()
        .forEach {
            it.setAttribute("disabled", "disabled")
            it.classList.add("km-pressure-row-done")
        }
}

/** Cosmetic, per-client: fades the handled row. The real double-apply guard is lastHandledDay. */
private fun markPressureRowDone(button: HTMLElement) {
    val row = button.closest(".km-pressure-row") as? HTMLElement ?: return
    row.classList.add("km-pressure-row-done")
    row.querySelectorAll("button").asList().filterIsInstance<HTMLElement>()
        .forEach { it.setAttribute("disabled", "disabled") }
}

private fun markPressureCardDone(button: HTMLElement) {
    val card = button.closest(".km-pressure-digest") as? HTMLElement ?: return
    card.querySelectorAll(".km-pressure-row").asList().filterIsInstance<HTMLElement>()
        .forEach { it.classList.add("km-pressure-row-done") }
    card.querySelectorAll("button").asList().filterIsInstance<HTMLElement>()
        .forEach { it.setAttribute("disabled", "disabled") }
}

private fun markMapChangeRowDone(button: HTMLElement) {
    val row = button.closest(".km-map-change-row") as? HTMLElement ?: return
    row.classList.add("km-map-change-done")
    row.querySelectorAll("button").asList().filterIsInstance<HTMLElement>()
        .forEach { it.setAttribute("disabled", "disabled") }
}

private fun markLootCardDone(button: HTMLElement) {
    val card = button.closest(".km-loot-award-card") as? HTMLElement ?: return
    card.querySelectorAll("button").asList().filterIsInstance<HTMLElement>()
        .forEach { it.setAttribute("disabled", "disabled") }
}

/**
 * Appends one deed line to the most recent turn record's notes, GM and player copies alike.
 *
 * The deed is public news -- a village founded, a region claimed -- so unlike campaign clocks
 * there is nothing to strip from the player gazette. Idempotent on the verbatim line, because a
 * GM may award a deed, undo, and award it again.
 */
private fun appendDeedGazetteLine(kingdom: KingdomData, milestoneName: String) {
    val history = kingdom.turnHistory ?: return
    val record = history.lastOrNull() ?: return
    val line = t("kingdom.turnGazette.deed", recordOf("name" to milestoneName))
    // formatTurnGazette joins its segments with " | " into ONE string, and the journal exporter
    // escapes that string into HTML where a newline collapses to whitespace -- appending with \n
    // ran the deed straight into the previous segment with no separator at all.
    fun append(existing: String?): String =
        if (existing.isNullOrBlank()) line else if (existing.contains(line)) existing else "$existing | $line"
    record.notes = append(record.notes)
    record.playerNotes = append(record.playerNotes)
}

/**
 * Confirm one ledger offer: grant the party the amount, then record it.
 *
 * The grant happens BEFORE the ledger write so a failure to reach the characters cannot leave a
 * row claiming XP nobody received. answerEntry only touches an OFFERED row, so a second click --
 * or a stale card in scrollback -- grants nothing.
 */
private suspend fun grantXpLedgerEntry(game: Game, entryId: String, typedAmount: Int?) {
    val party = game.xpLedgerActor() ?: return
    val entry = party.xpLedger().find { it.id == entryId && it.status == XpOfferStatus.OFFERED }
    if (entry == null) {
        ui.notifications.info(t("kingdom.xpLedger.alreadyAnswered"))
        return
    }
    val amount = (typedAmount ?: entry.proposedAmount).coerceAtLeast(0)
    if (amount > 0) {
        updateXP(party.partyMembers(), amount)
    }
    party.updateXpLedger { answerEntry(it, entryId, granted = amount) }
}

private fun markXpRowDone(button: HTMLElement) {
    val row = button.closest(".km-xp-digest-row") as? HTMLElement ?: return
    row.classList.add("km-pressure-row-done")
    row.querySelectorAll("button, input").asList().filterIsInstance<HTMLElement>()
        .forEach { it.setAttribute("disabled", "disabled") }
}

/** Grey out a petition card once answered, so scrollback cannot be clicked a second time. */
/** Grey out one digest row; the rival digest carries several independent offers. */
private fun markRivalRowDone(button: HTMLElement) {
    val row = button.closest(".km-rival-row") as? HTMLElement ?: return
    row.querySelectorAll("button").asList().filterIsInstance<HTMLElement>().forEach { it.setAttribute("disabled", "disabled") }
    row.classList.add("km-card-resolved")
}

/** The party's level for a confrontation budget: the highest PC level, or 1 with no party. */
private fun partyLevelFor(game: Game): Int =
    runCatching { game.xpLedgerActor()?.partyMembers()?.maxOfOrNull { it.system.details.level.value } }.getOrNull() ?: 1

private fun markPetitionCardDone(button: HTMLElement) {
    val card = button.closest(".km-chat-card") as? HTMLElement ?: return
    card.querySelectorAll("button").asList().filterIsInstance<HTMLElement>().forEach {
        it.setAttribute("disabled", "disabled")
    }
    card.classList.add("km-card-resolved")
}

private fun markXpCardDone(button: HTMLElement) {
    val card = button.closest(".chat-message") as? HTMLElement ?: return
    card.querySelectorAll(".km-xp-digest-row").asList().filterIsInstance<HTMLElement>()
        .forEach { it.classList.add("km-pressure-row-done") }
    card.querySelectorAll("button[class*='km-offer-xp'], input.km-xp-amount").asList()
        .filterIsInstance<HTMLElement>()
        .forEach { it.setAttribute("disabled", "disabled") }
}
