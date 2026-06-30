package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.companion.LevelUpResult
import at.posselt.pfrpg2e.companion.applyCompanionXp
import at.posselt.pfrpg2e.companion.applyPersonalQuestReward
import at.posselt.pfrpg2e.companion.canApplyExpeditionReward
import at.posselt.pfrpg2e.companion.clampInfluence
import at.posselt.pfrpg2e.companion.selectRewardQuest
import at.posselt.pfrpg2e.utils.fromUuidOfTypes
import com.foundryvtt.pf2e.actor.PF2ECharacter
import kotlin.math.min
import at.posselt.pfrpg2e.data.events.KingdomEventTrait
import at.posselt.pfrpg2e.kingdom.dialogs.AddExpeditionDialog
import at.posselt.pfrpg2e.kingdom.dialogs.AddQuest
import at.posselt.pfrpg2e.kingdom.dialogs.AddWarThreat
import at.posselt.pfrpg2e.settings.Pfrpg2eKingdomCampingWeatherSettings
import at.posselt.pfrpg2e.kingdom.dialogs.pickEventSettlement
import at.posselt.pfrpg2e.kingdom.dialogs.pickLeader
import at.posselt.pfrpg2e.kingdom.sheet.executeResourceButton
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import at.posselt.pfrpg2e.kingdom.structures.StructureActor
import at.posselt.pfrpg2e.kingdom.structures.validateUsingSchema
import at.posselt.pfrpg2e.takeIfInstance
import at.posselt.pfrpg2e.utils.bindChatClick
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.deserializeB64Json
import at.posselt.pfrpg2e.utils.launch
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.typeSafeUpdate
import com.foundryvtt.core.Game
import com.foundryvtt.core.helpers.TypedHooks
import com.foundryvtt.core.helpers.onRenderChatLog
import com.foundryvtt.core.ui
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
        if (!game.user.isGM) return@ChatButton
        val expeditionId = button.dataset["expeditionId"] ?: return@ChatButton
        actor.getKingdom()?.let { kingdom ->
            val expedition = kingdom.companionExpeditions?.find { it.id == expeditionId }
            if (expedition == null || !canApplyExpeditionReward(expedition.rewardApplied, expedition.status)) return@ChatButton

            // Apply XP to the companion (first participant).
            val companionId = expedition.companionIds.firstOrNull()
            if (companionId != null) {
                val companion = kingdom.companions?.find { (it.actorUuid ?: it.name) == companionId }
                if (companion != null) {
                    val levelingEnabled = Pfrpg2eKingdomCampingWeatherSettings.getEnableCompanionLeveling()
                    val xpToApply = if (levelingEnabled) expedition.accruedXp else 0
                    val levelResult = applyCompanionXp(
                        currentLevel = companion.level,
                        currentXp = companion.xp,
                        gainedXp = xpToApply,
                    )
                    if (levelingEnabled) {
                        companion.level = levelResult.newLevel
                        companion.xp = levelResult.newXp
                    }
                    // Mark companion as available again
                    companion.expeditionStatus = "available"
                }
            }

            // Apply influence delta (clamped).
            if (expedition.accruedInfluenceDelta != 0) {
                val companion = kingdom.companions?.find { (it.actorUuid ?: it.name) == (expedition.companionIds.firstOrNull() ?: "") }
                if (companion != null) {
                    val currentInfluence = companion.influence
                    companion.influence = clampInfluence(currentInfluence + expedition.accruedInfluenceDelta)
                }
            }

            // Wire up personal quest completion and rewards
            val isSuccess = expedition.outcomeDegree == "success" || expedition.outcomeDegree == "criticalSuccess"
            if (expedition.activityId == "personal-quest" && isSuccess && companionId != null) {
                val companion = kingdom.companions?.find { (it.actorUuid ?: it.name) == companionId }
                val quests = kingdom.companionPersonalQuests ?: emptyArray()
                val activeQuest = selectRewardQuest(quests.toList(), companionId, expedition.targetQuestId)
                if (activeQuest != null) {
                    if (companion != null) {
                        val levelingEnabled = Pfrpg2eKingdomCampingWeatherSettings.getEnableCompanionLeveling()
                        val outcome = applyPersonalQuestReward(
                            status = activeQuest.status,
                            currentInfluence = companion.influence,
                            influenceReward = activeQuest.influenceReward,
                            currentLevel = companion.level,
                            currentXp = companion.xp,
                            questXp = activeQuest.rewards?.xp ?: 0,
                            levelingEnabled = levelingEnabled,
                        )
                        activeQuest.status = outcome.newStatus
                        companion.influence = outcome.newInfluence
                        companion.level = outcome.levelResult.newLevel
                        companion.xp = outcome.levelResult.newXp
                        if (outcome.levelResult.levelsGained > 0) {
                            postChatMessage(
                                t("kingdom.companionLeveledUp", recordOf("name" to companion.name, "level" to outcome.levelResult.newLevel))
                            )
                        }
                    } else {
                        activeQuest.status = "completed"
                    }
                    kingdom.companionPersonalQuests = quests
                }
            }

            // Mark expedition resolved.
            expedition.rewardApplied = true
            expedition.status = "resolved"

            // Persist
            kingdom.companionExpeditions = kingdom.companionExpeditions?.map {
                if (it.id == expeditionId) expedition else it
            }?.toTypedArray()
            actor.setKingdom(kingdom)

            postChatMessage(t("kingdom.expeditionRewardApplied", recordOf("name" to expedition.title)))
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

            // Mark expedition as resolved since injury was applied.
            expedition.status = "resolved"
            expedition.rewardApplied = true

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
            // Approve: assign the top-ranked volunteer to a new expedition
            val kingdom = actor.getKingdom() ?: return@ChatButton
            val companions = kingdom.companions ?: return@ChatButton
            val volunteers = at.posselt.pfrpg2e.kingdom.CompanionAutonomy.selectAutonomousCompanions(companions.toList())
            val topPick = volunteers.firstOrNull()
            if (topPick != null) {
                val key = topPick.actorUuid ?: topPick.name
                AddExpeditionDialog(
                    companions = companions,
                    preselectedId = key,
                    quests = kingdom.companionPersonalQuests ?: emptyArray(),
                ) { expedition ->
                    buildPromise {
                        val current = actor.getKingdom() ?: return@buildPromise
                        current.companionExpeditions = (current.companionExpeditions ?: emptyArray()) + expedition
                        val updatedComps = (current.companions ?: emptyArray()).copyOf()
                        expedition.companionIds.forEach { cid ->
                            updatedComps.find { (it.actorUuid ?: it.name) == cid }?.expeditionStatus = "onExpedition"
                        }
                        current.companions = updatedComps
                        actor.setKingdom(current)
                        postChatTemplate(
                            templatePath = "chatmessages/companion-autonomy-approved.hbs",
                            templateContext = js.objects.recordOf(
                                "name" to topPick.name,
                            )
                        )
                    }
                }.launch()
            }
        } else if (decline) {
            postChatMessage(t("chatMessages.companionAutonomy.declinedMessage"))
        } else if (sendElsewhere) {
            val kingdom = actor.getKingdom() ?: return@ChatButton
            val companions = kingdom.companions ?: return@ChatButton
            AddExpeditionDialog(
                companions = companions,
                quests = kingdom.companionPersonalQuests ?: emptyArray(),
            ) { expedition ->
                buildPromise {
                    val current = actor.getKingdom() ?: return@buildPromise
                    current.companionExpeditions = (current.companionExpeditions ?: emptyArray()) + expedition
                    val updatedComps = (current.companions ?: emptyArray()).copyOf()
                    expedition.companionIds.forEach { cid ->
                        updatedComps.find { (it.actorUuid ?: it.name) == cid }?.expeditionStatus = "onExpedition"
                    }
                    current.companions = updatedComps
                    actor.setKingdom(current)
                }
            }.launch()
        }
    }
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