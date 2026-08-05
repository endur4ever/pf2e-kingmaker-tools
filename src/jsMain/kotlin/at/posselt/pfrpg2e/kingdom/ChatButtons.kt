package at.posselt.pfrpg2e.kingdom

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
                    recordOf("consequence" to t("chatMessages.battleDefeat.$choice", recordOf("amount" to amount))),
                )
            )
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
            postChatMessage(t("chatMessages.milestone.dismissed", recordOf("name" to milestone.name)))
            return@ChatButton
        }
        if (action != "award") return@ChatButton
        val existing = kingdom.milestones.find { it.id == milestoneId }
        if (existing?.completed == true) return@ChatButton
        if (existing == null) {
            kingdom.milestones = kingdom.milestones + MilestoneChoice(
                id = milestoneId,
                completed = false,
                enabled = true,
            )
        }
        val previous = deepClone(kingdom)
        kingdom.milestones = kingdom.milestones.map {
            if (it.id == milestoneId) MilestoneChoice.copy(it, completed = true, enabled = true) else it
        }.toTypedArray()
        beforeKingdomUpdate(previous, kingdom)
        actor.setKingdom(kingdom)
        postChatMessage(
            t("chatMessages.milestone.awarded", recordOf("name" to milestone.name, "xp" to milestone.xp)),
        )
    },
    // Jump-to-settlement on the pacing-alert CHAT card. It MUST be a ChatButton (bound to #chat by
    // CSS class) — the sheet-panel copy uses data-action/_onClickAction, but that only fires inside
    // the sheet DOM, so a chat-card button needs its own class-based handler here. data-id is the
    // settlement's scene id (Settlement.id == sceneId), matching the sheet's game.scenes.get(id).view().
    ChatButton("km-view-settlement") { game, actor, event, button ->
        val id = button.dataset["id"] ?: return@ChatButton
        game.scenes.get(id)?.view()
    },
    // NOTE: "mark pending encounter as run" is handled by the kingdom sheet's _onClickAction
    // ("mark-pending-encounter-run"), because the button only ever renders in the Session Prep
    // sheet DOM — a ChatButton (bound to the #chat sidebar) never receives its click.
)

fun bindChatButtons(game: Game) {
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
    }
}