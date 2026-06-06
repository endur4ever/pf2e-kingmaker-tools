package at.posselt.pfrpg2e.companion

import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.forms.SimpleApp
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.kingdom.sheet.contexts.CompanionProfileContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.buildCompanionProfileContext
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.launch
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.game
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
                mutate { companion, quests ->
                    quests.find { it.id == questId && it.status == "active" }?.let { quest ->
                        quest.status = "completed"
                        if (quest.influenceReward != 0) {
                            companion.influence = clampInfluence(companion.influence + quest.influenceReward)
                        }
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

            "influence-increase" -> mutate { c, _ -> c.influence = clampInfluence(c.influence + 1) }
            "influence-decrease" -> mutate { c, _ -> c.influence = clampInfluence(c.influence - 1) }

            "toggle-camp-available" -> mutate { c, _ -> c.campAvailable = !c.campAvailable }

            "set-discovery" -> {
                if (!game.user.isGM) return
                val select = element.querySelector("select[name='discoveryStatus']") as? HTMLSelectElement
                val value = select?.value ?: return
                if (value in companionDiscoveryStages) {
                    mutate { c, _ -> c.discoveryStatus = value }
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
        if (companion == null) {
            buildCompanionProfileContext(
                partId = parent.partId,
                companion = RawCharacter(name = "?"),
                quests = emptyList(),
                isGM = game.user.isGM,
                localize = { t(it) },
            )
        } else {
            buildCompanionProfileContext(
                partId = parent.partId,
                companion = companion,
                quests = questsFor(companion, kingdom.companionPersonalQuests ?: emptyArray()),
                isGM = game.user.isGM,
                localize = { t(it) },
            )
        }
    }
}
