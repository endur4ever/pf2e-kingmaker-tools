package at.posselt.pfrpg2e.companion

import at.posselt.pfrpg2e.companion.applyCompanionXp
import at.posselt.pfrpg2e.camping.getCampingActors
import at.posselt.pfrpg2e.camping.getCamping
import at.posselt.pfrpg2e.settings.Pfrpg2eKingdomCampingWeatherSettings
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.forms.SimpleApp
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.buildExpeditionDestinationOptions
import at.posselt.pfrpg2e.kingdom.launchExpedition
import at.posselt.pfrpg2e.kingdom.logExpeditionLaunched
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.kingdom.sheet.contexts.CompanionProfileContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.buildCompanionProfileContext
import at.posselt.pfrpg2e.kingdom.dialogs.AddExpeditionDialog
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.launch
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.t
import js.objects.recordOf
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.game
import com.foundryvtt.core.ui
import kotlinx.coroutines.await
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

/**
 * Per-companion profile dialog: influence, discovery status, camp availability, and personal quests.
 * GMs get full controls; players see a read-only view with only player-visible quests (Decision 5).
 */
class CompanionProfileDialog(
    private val kingdomActor: KingdomActor,
    private val companionIndex: Int,
) : SimpleApp<CompanionProfileContext>(
    title = t("kingdom.companion.profile"),
    template = "applications/kingdom/companion-profile.hbs",
    id = "kmCompanionProfile-${kingdomActor.uuid}-$companionIndex",
    classes = setOf("km-companion-profile-dialog"),
    width = 520,
) {
    private fun companionKey(companion: RawCharacter): String =
        companion.actorUuid ?: companion.name

    private fun questsFor(
        companion: RawCharacter,
        all: Array<CompanionPersonalQuest>,
    ): List<CompanionPersonalQuest> {
        val ids = companion.personalQuestIds.toSet()
        val key = companionKey(companion)
        return all.filter { it.id in ids || it.companionId == key }
    }

    /**
     * Durable id of the current camping session, or null when there is no camp. Reuses the camping
     * system's own per-session marker (dailyPrepsAtTime, the world-time stamp of the last daily
     * preparations) so the once-per-session cap and the camping oncePerSession reset can't drift.
     */
    private fun currentCampingSessionId(): String? =
        game.getCampingActors().firstOrNull()?.getCamping()?.dailyPrepsAtTime?.toString()

    /**
     * House-rule once-per-camping-session gate for a companion Influence/Discover attempt. Blocks
     * (with a warning) when [getLast] already equals the current session id; otherwise applies
     * [block] and records the current session id via [setLast]. GM only, like [mutate].
     */
    private fun attemptOncePerSession(
        getLast: (RawCharacter) -> String?,
        setLast: (RawCharacter, String?) -> Unit,
        warnKey: String,
        block: (RawCharacter) -> Unit,
    ) {
        if (!game.user.isGM) return
        val sessionId = currentCampingSessionId()
        buildPromise {
            val kingdom = kingdomActor.getKingdom() ?: return@buildPromise
            val companion = kingdom.companions?.getOrNull(companionIndex) ?: return@buildPromise
            if (!canAttemptCompanionInteraction(getLast(companion), sessionId)) {
                ui.notifications.warn(t(warnKey))
                return@buildPromise
            }
            block(companion)
            setLast(companion, sessionId)
            kingdomActor.setKingdom(kingdom)
            render()
        }
    }

    /** Re-read kingdom, mutate companion + quests via [block], persist, and re-render. GM only. */
    private fun mutate(block: (companion: RawCharacter, quests: MutableList<CompanionPersonalQuest>) -> Unit) {
        if (!game.user.isGM) return
        buildPromise {
            val kingdom = kingdomActor.getKingdom() ?: return@buildPromise
            val companion = kingdom.companions?.getOrNull(companionIndex) ?: return@buildPromise
            val quests = (kingdom.companionPersonalQuests ?: emptyArray()).toMutableList()
            block(companion, quests)
            kingdom.companionPersonalQuests = quests.toTypedArray()
            kingdomActor.setKingdom(kingdom)
            render()
        }
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        val action = target.dataset["action"]
        val questId = target.dataset["questId"]
        when (action) {
            "add-personal-quest" -> {
                if (!game.user.isGM) return
                buildPromise {
                    val kingdom = kingdomActor.getKingdom() ?: return@buildPromise
                    val companion = kingdom.companions?.getOrNull(companionIndex) ?: return@buildPromise
                    AddPersonalQuest(
                        companionId = companionKey(companion),
                        onSave = { quest ->
                            mutate { c, quests ->
                                quests.add(quest)
                                if (quest.id !in c.personalQuestIds) {
                                    c.personalQuestIds = c.personalQuestIds + quest.id
                                }
                            }
                        },
                    ).launch()
                }
            }

            "edit-personal-quest" -> {
                if (!game.user.isGM || questId == null) return
                buildPromise {
                    val kingdom = kingdomActor.getKingdom() ?: return@buildPromise
                    val companion = kingdom.companions?.getOrNull(companionIndex) ?: return@buildPromise
                    val existing = (kingdom.companionPersonalQuests ?: emptyArray()).find { it.id == questId }
                        ?: return@buildPromise
                    AddPersonalQuest(
                        companionId = companionKey(companion),
                        existing = existing,
                        onSave = { quest ->
                            mutate { _, quests ->
                                val idx = quests.indexOfFirst { it.id == quest.id }
                                if (idx >= 0) quests[idx] = quest
                            }
                        },
                    ).launch()
                }
            }

            "toggle-quest-visibility" -> {
                if (questId == null) return
                mutate { _, quests ->
                    quests.find { it.id == questId }?.let { it.visibleToPlayers = !it.visibleToPlayers }
                }
            }

            "complete-quest" -> {
                if (questId == null) return
                // Auto-apply the influence reward on completion (Decision 3), clamped to [0, 12].
                // Also auto-apply XP reward from CompanionQuestRewards.leveling gate.
                var leveledCompanionName: String? = null
                var leveledNewLevel = 0
                mutate { companion, quests ->
                    quests.find { it.id == questId && it.status == "active" }?.let { quest ->
                        val priorStatus = quest.status
                        val beforeInfluence = companion.influence
                        val beforeLevel = companion.level
                        val beforeXp = companion.xp
                        quest.status = "completed"
                        if (quest.influenceReward != 0) {
                            companion.influence = clampInfluence(companion.influence + quest.influenceReward)
                        }
                        val xpToAward = quest.rewards?.xp
                        if (xpToAward != null && xpToAward > 0) {
                            val levelingEnabled = Pfrpg2eKingdomCampingWeatherSettings.getEnableCompanionLeveling()
                            if (levelingEnabled) {
                                val levelResult = applyCompanionXp(
                                    currentLevel = companion.level,
                                    currentXp = companion.xp,
                                    gainedXp = xpToAward,
                                )
                                companion.level = levelResult.newLevel
                                companion.xp = levelResult.newXp
                                if (levelResult.levelsGained > 0) {
                                    leveledCompanionName = companion.name
                                    leveledNewLevel = levelResult.newLevel
                                }
                            }
                        }
                        // Capture the exact applied deltas so a "reopen" restores the companion precisely.
                        quest.completionSnapshot = personalQuestCompletionSnapshot(
                            priorStatus = priorStatus,
                            beforeInfluence = beforeInfluence,
                            afterInfluence = companion.influence,
                            beforeLevel = beforeLevel,
                            beforeXp = beforeXp,
                            afterLevel = companion.level,
                            afterXp = companion.xp,
                        )
                    }
                }
                if (leveledCompanionName != null) {
                    buildPromise {
                        postChatMessage(
                            t("kingdom.companionLeveledUp", recordOf("name" to leveledCompanionName, "level" to leveledNewLevel))
                        )
                    }
                }
            }

            "reopen-quest" -> {
                if (questId == null) return
                var reopenedTitle: String? = null
                mutate { companion, quests ->
                    quests.find { it.id == questId && it.status == "completed" }?.let { quest ->
                        val snap = quest.completionSnapshot
                        if (snap != null) {
                            // Reverse the applied influence + XP (total-XP space handles level-downs).
                            val outcome = reversePersonalQuestReward(
                                snapshot = snap,
                                currentInfluence = companion.influence,
                                currentLevel = companion.level,
                                currentXp = companion.xp,
                            )
                            companion.influence = outcome.newInfluence
                            companion.level = outcome.newLevel
                            companion.xp = outcome.newXp
                            quest.status = outcome.newStatus
                            quest.completionSnapshot = null
                        } else {
                            // Legacy completion with no snapshot: best-effort — just reopen the status.
                            quest.status = "active"
                        }
                        reopenedTitle = quest.title
                    }
                }
                reopenedTitle?.let { title ->
                    buildPromise {
                        postChatMessage(t("kingdom.quests.reopened", recordOf("name" to title)))
                    }
                }
            }

            "fail-quest" -> {
                if (questId == null) return
                mutate { _, quests -> quests.find { it.id == questId }?.let { it.status = "failed" } }
            }

            "abandon-quest" -> {
                if (questId == null) return
                mutate { _, quests -> quests.find { it.id == questId }?.let { it.status = "abandoned" } }
            }

            "delete-quest" -> {
                if (questId == null) return
                mutate { companion, quests ->
                    quests.removeAll { it.id == questId }
                    companion.personalQuestIds = companion.personalQuestIds.filter { it != questId }.toTypedArray()
                }
            }

            // Influencing a companion is the once-per-camping-session "Influence attempt" (house rule).
            "influence-increase" -> attemptOncePerSession(
                getLast = { it.lastInfluenceAttemptSessionId },
                setLast = { c, s -> c.lastInfluenceAttemptSessionId = s },
                warnKey = "kingdom.companion.influenceAttemptUsed",
            ) { c -> c.influence = clampInfluence(c.influence + 1) }
            // Decrease is an uncapped correction, not an attempt.
            "influence-decrease" -> mutate { c, _ -> c.influence = clampInfluence(c.influence - 1) }
            // GM override: clear the recorded attempt so another Influence attempt is allowed this session.
            "reset-influence-attempt" -> mutate { c, _ -> c.lastInfluenceAttemptSessionId = null }
            "reset-discovery-attempt" -> mutate { c, _ -> c.lastDiscoveryAttemptSessionId = null }

            "toggle-camp-available" -> mutate { c, _ -> c.campAvailable = !c.campAvailable }

            "send-on-expedition" -> {
                if (!game.user.isGM) return
                buildPromise {
                    val kingdom = kingdomActor.getKingdom() ?: return@buildPromise
                    val comps = kingdom.companions ?: emptyArray()
                    val companion = comps.getOrNull(companionIndex) ?: return@buildPromise
                    val key = companionKey(companion)
                    AddExpeditionDialog(
                        companions = comps,
                        preselectedId = key,
                        quests = kingdom.companionPersonalQuests ?: emptyArray(),
                        factions = kingdom.groups,
                        destinations = buildExpeditionDestinationOptions(kingdom),
                    ) { expedition ->
                        val current = kingdomActor.getKingdom() ?: return@AddExpeditionDialog false
                        val updatedComps = (current.companions ?: emptyArray()).copyOf()
                        if (launchExpedition(current, expedition, updatedComps)) {
                            current.companions = updatedComps
                            kingdomActor.setKingdom(current)
                            logExpeditionLaunched(expedition, updatedComps)
                            render()
                            true
                        } else {
                            ui.notifications.warn(t("kingdom.expeditions.tooMany"))
                            false
                        }
                    }.launch()
                }
            }

            "set-discovery" -> {
                if (!game.user.isGM) return
                val select = element.querySelector("select[name='discoveryStatus']") as? HTMLSelectElement
                val value = select?.value ?: return
                if (value in companionDiscoveryStages) {
                    // Changing discovery status is the once-per-camping-session "Discover attempt".
                    attemptOncePerSession(
                        getLast = { it.lastDiscoveryAttemptSessionId },
                        setLast = { c, s -> c.lastDiscoveryAttemptSessionId = s },
                        warnKey = "kingdom.companion.discoveryAttemptUsed",
                    ) { c -> c.discoveryStatus = value }
                }
            }
        }
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions,
    ): Promise<CompanionProfileContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        val kingdom = kingdomActor.getKingdom()
        val companion = kingdom?.companions?.getOrNull(companionIndex)
        val expeditions = kingdom?.companionExpeditions?.toList() ?: emptyList()
        if (companion == null) {
            buildCompanionProfileContext(
                partId = parent.partId,
                companion = RawCharacter(name = "?"),
                quests = emptyList(),
                isGM = game.user.isGM,
                expeditions = expeditions,
                localize = { t(it) },
            )
        } else {
            buildCompanionProfileContext(
                partId = parent.partId,
                companion = companion,
                quests = questsFor(companion, kingdom.companionPersonalQuests ?: emptyArray()),
                isGM = game.user.isGM,
                expeditions = expeditions,
                localize = { t(it) },
                currentSessionId = currentCampingSessionId(),
            )
        }
    }
}
