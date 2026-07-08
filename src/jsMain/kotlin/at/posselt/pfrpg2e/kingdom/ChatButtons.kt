package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.companion.LevelUpResult
import at.posselt.pfrpg2e.companion.applyCompanionXp
import at.posselt.pfrpg2e.utils.fromUuidOfTypes
import com.foundryvtt.pf2e.actor.PF2ECharacter
import kotlin.math.min
import at.posselt.pfrpg2e.data.events.KingdomEventTrait
import at.posselt.pfrpg2e.kingdom.dialogs.AddExpeditionDialog
import at.posselt.pfrpg2e.kingdom.dialogs.AddQuest
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
import at.posselt.pfrpg2e.takeIfInstance
import at.posselt.pfrpg2e.utils.bindChatClick
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.deserializeB64Json
import at.posselt.pfrpg2e.utils.launch
import at.posselt.pfrpg2e.utils.getAppFlag
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.unsetAppFlag
import at.posselt.pfrpg2e.utils.typeSafeUpdate
import com.foundryvtt.core.Game
import com.foundryvtt.core.helpers.TypedHooks
import com.foundryvtt.core.helpers.onRenderChatLog
import com.foundryvtt.core.ui
import com.foundryvtt.core.utils.deepClone
import io.github.uuidjs.uuid.v4
import js.array.tupleOf
import js.objects.recordOf
import kotlinx.html.org.w3c.dom.events.Event
import kotlinx.js.JsPlainObject
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.get

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
            kingdom.ongoingEvents = kingdom.ongoingEvents
                .filterIndexed { index, _ -> index != eventIndex }
                .toTypedArray()
            actor.setKingdom(kingdom)
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
    ChatButton("km-offer-war-threat") { game, actor, event, button ->
        // GM-confirmed offer from a faction-standing threshold crossing (#1 → #12).
        // Opens the AddWarThreat dialog prefilled with the faction; nothing is created
        // until the GM saves.
        val faction = button.dataset["faction"] ?: ""
        AddWarThreat(
            prefillName = t("chatMessages.endTurn.warThreatName", recordOf("group" to faction)),
            prefillEnemyFaction = faction.ifBlank { null },
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
    ChatButton("km-offer-diplomacy-quest") { game, actor, event, button ->
        // GM-confirmed offer from a faction-standing threshold crossing (#1 → #2).
        // Opens the AddQuest dialog prefilled with the faction as giver.
        val faction = button.dataset["faction"] ?: ""
        AddQuest(
            prefillTitle = t("chatMessages.endTurn.diplomacyQuestTitle", recordOf("group" to faction)),
            prefillGiver = faction,
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
        val expeditionId = button.dataset["expeditionId"] ?: return@ChatButton
        actor.getKingdom()?.let { kingdom ->
            val expedition = kingdom.companionExpeditions?.find { it.id == expeditionId } ?: return@ChatButton
            if (applyExpeditionRewardToKingdom(kingdom, expedition)) {
                actor.setKingdom(kingdom)
                postChatMessage(t("kingdom.expeditionRewardApplied", recordOf("name" to expedition.title)))
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

        val linkedActor = fromUuidOfTypes<PF2ECharacter>(companionActorUuid) ?: return@ChatButton
        linkedActor.typeSafeUpdate { system.details.level.value = targetLevel }
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

            // Apply injury conditions (e.g., fatigued, wounded) to the linked actor.
            // Time-boxed downtime: recovery days depend on the expedition tier
            // (routine 2 / standard 3 / perilous 5), decremented by the daily tick.
            companion.injuryDaysRemaining = when (expedition.tier) {
                "routine" -> 2
                "perilous" -> 5
                else -> 3
            }
            companion.expeditionStatus = "unavailable"
            companion.campAvailable = false

            // Applying injury consumes the reward path (status=resolved + rewardApplied below),
            // so applyExpeditionRewardToKingdom will never run for this expedition — release the
            // OTHER participants here or they'd stay "onExpedition" forever, locked out of all
            // future launches. Only the injured companion goes into downtime.
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

            // This path consumes the reward flow, so record durable history here too —
            // unless Apply Reward already ran and recorded it (guard on rewardApplied).
            val alreadyRecorded = expedition.rewardApplied

            // Mark expedition as resolved since injury was applied.
            expedition.status = "resolved"
            expedition.rewardApplied = true
            if (!alreadyRecorded) {
                recordExpeditionInHistory(kingdom, expedition, lootRp = 0)
            }

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
    ChatButton("km-undo-end-turn") { game, actor, event, button ->
        // GM-only: revert the most recent End Turn using the exact snapshot.
        if (!game.user.isGM) return@ChatButton
        val snap = actor.getAppFlag<KingdomActor, dynamic>("lastTurnSnapshot") as? EndTurnSnapshot
        if (snap == null) return@ChatButton
        val kingdom = actor.getKingdom() ?: return@ChatButton
        // Only allow undo if exactly one End Turn has happened since the snapshot.
        val currentTurn = kingdom.currentTurn ?: 0
        if (currentTurn != snap.snapshotTurn) return@ChatButton

        // Restore the exact pre-End-Turn kingdom state.
        val restoredKingdom = deepClone(snap.kingdom)
        actor.setKingdom(restoredKingdom)
        actor.unsetAppFlag("lastTurnSnapshot")

        // Post a chat notice (GM-only whisper so players don't see the undo button).
        val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
        if (gmUserIds.isNotEmpty()) {
            val context = js("{}")
            context.turn = currentTurn
            context.kingdomName = restoredKingdom.name
            postChatTemplate(
                templatePath = "chatmessages/end-turn-undo.hbs",
                templateContext = context,
                whisper = gmUserIds,
            )
        } else {
            postChatMessage(t("chatMessages.endTurn.endTurnUndone", recordOf("turn" to currentTurn.toString())))
        }
    },
)

fun bindChatButtons(game: Game) {
    TypedHooks.onRenderChatLog { application, _, data ->
        buttons.forEach { data ->
            bindChatClick(".${data.buttonClass}") { ev, target, parent ->
                buildPromise {
                    parent.findKingdomActor(game)
                        ?.let { data.callback(game, it, ev, target) }
                }
            }
        }
    }
}