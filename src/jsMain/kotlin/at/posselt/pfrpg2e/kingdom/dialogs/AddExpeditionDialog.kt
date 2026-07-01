package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.forms.SimpleApp
import at.posselt.pfrpg2e.companion.CompanionPersonalQuest
import at.posselt.pfrpg2e.expedition.ExpeditionActivityData
import at.posselt.pfrpg2e.expedition.getExpeditionActivities
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import at.posselt.pfrpg2e.kingdom.data.RawGroup
import at.posselt.pfrpg2e.kingdom.data.createRawCompanionExpedition
import at.posselt.pfrpg2e.utils.buildPromise
import io.github.uuidjs.uuid.v4
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.ui
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise
import kotlin.js.Date

@JsPlainObject
external interface AddExpeditionCompanionContext {
    val id: String
    val name: String
    val checked: Boolean
}

@JsPlainObject
external interface AddExpeditionQuestContext {
    val id: String
    val label: String
}

@JsPlainObject
external interface AddExpeditionFactionContext {
    val name: String
}

@JsPlainObject
external interface AddExpeditionContext : HandlebarsRenderContext {
    val activities: Array<ExpeditionActivityData>
    val companions: Array<AddExpeditionCompanionContext>
    val quests: Array<AddExpeditionQuestContext>
    val factions: Array<AddExpeditionFactionContext>
    val selectedActivityId: String
    val selectedTier: String
    val selectedCompanionIds: Array<String>
    val gmNotes: String
    val visibleToPlayers: Boolean
}

class AddExpeditionDialog(
    private val companions: Array<RawCharacter>,
    private val preselectedId: String? = null,
    private val quests: Array<CompanionPersonalQuest> = emptyArray(),
    private val factions: Array<RawGroup> = emptyArray(),
    private val onAdd: suspend (RawCompanionExpedition) -> Unit,
) : SimpleApp<AddExpeditionContext>(
    title = t("kingdom.expeditions.addExpedition"),
    template = "applications/kingdom/add-expedition.hbs",
    id = "kmAddExpeditionDialog",
    classes = setOf("km-add-expedition-dialog"),
) {
    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "save" -> buildPromise {
                val activityId = element.querySelector("select[name='expeditionActivity']")
                    ?.let { it as? org.w3c.dom.HTMLSelectElement }
                    ?.value?.takeIf { it.isNotBlank() } ?: return@buildPromise

                val activity = getExpeditionActivities().find { it.id == activityId } ?: return@buildPromise

                val tier = element.querySelector("select[name='expeditionTier']")
                    ?.let { it as? org.w3c.dom.HTMLSelectElement }
                    ?.value ?: "standard"

                val duration = when (tier) {
                    "routine" -> 2
                    "perilous" -> 5
                    else -> 3
                }

                val dc = when (tier) {
                    "routine" -> 14
                    "perilous" -> 22
                    else -> 18
                }

                val companionCheckboxes = element.querySelectorAll("input[name='expeditionCompanions']:checked")
                val selectedCompanionIds = mutableListOf<String>()
                for (i in 0 until companionCheckboxes.length) {
                    val cb = companionCheckboxes[i] as? org.w3c.dom.HTMLInputElement ?: continue
                    if (cb.checked) selectedCompanionIds.add(cb.value)
                }

                if (selectedCompanionIds.isEmpty()) return@buildPromise

                val gmNotes = element.querySelector("textarea[name='expeditionNotes']")
                    ?.let { it as? org.w3c.dom.HTMLTextAreaElement }
                    ?.value ?: ""

                val visibleToPlayers = element.querySelector("input[name='expeditionVisible']")
                    ?.let { it as? org.w3c.dom.HTMLInputElement }
                    ?.checked ?: false

                val targetQuestId = element.querySelector("select[name='expeditionQuest']")
                    ?.let { it as? org.w3c.dom.HTMLSelectElement }
                    ?.value?.takeIf { it.isNotBlank() }

                val targetFactionName = element.querySelector("select[name='expeditionFaction']")
                    ?.let { it as? org.w3c.dom.HTMLSelectElement }
                    ?.value?.takeIf { it.isNotBlank() }

                // A diplomacy expedition with no target faction can never move standing — block the
                // launch with a clear message rather than silently producing an inert expedition.
                if (activityId == "diplomacy" && targetFactionName == null) {
                    ui.notifications.warn(t("kingdom.expeditions.diplomacyRequiresFaction"))
                    return@buildPromise
                }

                val expedition = createRawCompanionExpedition(
                    id = v4(),
                    activityId = activityId,
                    title = activity.name,
                    companionIds = selectedCompanionIds.toTypedArray(),
                    totalDays = duration,
                    dc = dc,
                    tier = tier,
                    visibleToPlayers = visibleToPlayers,
                    createdAt = Date().toISOString(),
                    targetQuestId = targetQuestId,
                    targetFactionName = targetFactionName,
                ).also {
                    it.gmNotes = gmNotes
                }

                onAdd(expedition)
                close()
            }

            "cancel" -> close()
        }
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions,
    ): Promise<AddExpeditionContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        val companionContexts = companions
            .filter { (it.active && it.expeditionStatus == "available" && it.injuryDaysRemaining == null) || (it.actorUuid ?: it.name) == preselectedId }
            .map { c ->
                val key = c.actorUuid ?: c.name
                AddExpeditionCompanionContext(
                    id = key,
                    name = c.name,
                    checked = key == preselectedId,
                )
            }
            .toTypedArray()

        val questContexts = quests
            .filter { it.status == "active" }
            .map { q ->
                val companionName = companions.find { (it.actorUuid ?: it.name) == q.companionId }?.name ?: q.companionId
                AddExpeditionQuestContext(id = q.id, label = "$companionName: ${q.title}")
            }
            .toTypedArray()

        val factionContexts = factions
            .map { AddExpeditionFactionContext(name = it.name) }
            .toTypedArray()

        AddExpeditionContext(
            partId = parent.partId,
            activities = getExpeditionActivities(),
            companions = companionContexts,
            quests = questContexts,
            factions = factionContexts,
            selectedActivityId = "",
            selectedTier = "standard",
            selectedCompanionIds = if (preselectedId != null) arrayOf(preselectedId) else emptyArray(),
            gmNotes = "",
            visibleToPlayers = false,
        )
    }
}

