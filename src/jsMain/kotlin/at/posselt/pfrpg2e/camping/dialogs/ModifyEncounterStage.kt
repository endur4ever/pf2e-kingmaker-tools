package at.posselt.pfrpg2e.camping.dialogs

import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.SimpleApp
import at.posselt.pfrpg2e.actor.partyMembers
import at.posselt.pfrpg2e.camping.CampingActor
import at.posselt.pfrpg2e.camping.RawEncounterCreature
import at.posselt.pfrpg2e.camping.RawEncounterManifest
import at.posselt.pfrpg2e.camping.EncounterThreat
import at.posselt.pfrpg2e.camping.creatureXpContribution
import at.posselt.pfrpg2e.camping.creatureList
import at.posselt.pfrpg2e.camping.encounterBudget
import at.posselt.pfrpg2e.camping.spawnCount
import at.posselt.pfrpg2e.camping.stageEncounter
import at.posselt.pfrpg2e.camping.threatForXp
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.ui
import com.foundryvtt.pf2e.actor.PF2EActor
import js.objects.recordOf
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

@Suppress("unused")
@JsPlainObject
external interface StageCreatureRowContext {
    val index: Int
    val uuid: String
    val name: String
    val count: Int
    val adjustment: String?
    /** Per-row XP contribution at the party's level; informational in v1. */
    val xpNote: String?
}

@Suppress("unused")
@JsPlainObject
external interface EncounterStageContext : ValidatedHandlebarsContext {
    val creatures: Array<StageCreatureRowContext>
    val hasCreatures: Boolean
    val startDistanceFt: Int
    val spawnHidden: Boolean
    val xpBudgetNote: String?
    val totalCount: Int
    val partyLevel: Int
    val partySize: Int
    /** Running total vs the party's bands, so the GM sees the difficulty before spawning. */
    val totalXp: Int
    val threatLabel: String
    val moderateBudget: Int
    val severeBudget: Int
}

/**
 * The GM's curation step before a spawn (encounter-stager plan 4.3): edit the manifest, override
 * the opening distance, toggle hidden, then Stage.
 *
 * Elite/Weak is RECORDED but never applied in v1 (plan section 7): applying it means editing the
 * spawned token's actor delta, not the shared compendium source, or every future draw of that
 * bestiary entry inherits the template. The dialog shows the XP arithmetic instead so the GM can
 * apply an adjustment by hand where it matters.
 */
class ModifyEncounterStage(
    private val game: Game,
    private val partyActor: CampingActor,
    initial: RawEncounterManifest?,
    private var startDistanceFt: Int,
    private var spawnHidden: Boolean,
    private val onSaved: (RawEncounterManifest) -> Unit,
) : SimpleApp<EncounterStageContext>(
    title = t("camping.encounterStageTitle"),
    template = "applications/camping/encounter-stage.hbs",
    id = "kmEncounterStage",
    width = 560,
) {
    private var creatures: MutableList<RawEncounterCreature> =
        initial.creatureList().map {
            RawEncounterCreature(
                uuid = it.uuid,
                count = it.count,
                adjustment = it.adjustment,
                displayName = it.displayName,
            )
        }.toMutableList()
    private var xpBudgetNote: String? = initial?.xpBudgetNote
    /** uuid -> (name, level), resolved once per render so the list does not re-fetch per row. */
    private var resolved: Map<String, Pair<String, Int>> = emptyMap()

    private fun manifest(): RawEncounterManifest = RawEncounterManifest(
        creatures = creatures.toTypedArray(),
        startDistanceFt = startDistanceFt,
        xpBudgetNote = xpBudgetNote,
    )

    private fun readDistance() {
        val input = element.querySelector("input[name='startDistanceFt']") as? HTMLInputElement
        input?.value?.toIntOrNull()?.takeIf { it >= 0 }?.let { startDistanceFt = it }
        val hiddenBox = element.querySelector("input[name='spawnHidden']") as? HTMLInputElement
        hiddenBox?.let { spawnHidden = it.checked }
        val note = element.querySelector("input[name='xpBudgetNote']") as? HTMLInputElement
        xpBudgetNote = note?.value?.takeIf { it.isNotBlank() }
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-stage-add" -> buildPromise {
                readDistance()
                // a UUID box, not drag-drop: SimpleApp has no drop target wired, and the GM can
                // copy a uuid off any actor's sheet header. Drag-drop is a v2 nicety.
                val uuid = askCreatureUuid() ?: return@buildPromise
                val actor = fromUuidTypeSafe<PF2EActor>(uuid)
                if (actor == null) {
                    ui.notifications.error(t("camping.encounterStageBadUuid", recordOf("uuid" to uuid)))
                    return@buildPromise
                }
                creatures.add(
                    RawEncounterCreature(
                        uuid = uuid,
                        count = 1,
                        adjustment = null,
                        displayName = actor.name,
                    )
                )
                render()
            }

            "km-stage-remove" -> {
                readDistance()
                val index = target.dataset["index"]?.toIntOrNull() ?: return
                if (index in creatures.indices) {
                    creatures.removeAt(index)
                    render()
                }
            }

            "km-stage-inc", "km-stage-dec" -> {
                readDistance()
                val index = target.dataset["index"]?.toIntOrNull() ?: return
                val row = creatures.getOrNull(index) ?: return
                val delta = if (target.dataset["action"] == "km-stage-inc") 1 else -1
                val next = (row.count + delta).coerceIn(1, 20)
                creatures[index] = RawEncounterCreature.copy(row, count = next)
                render()
            }

            "km-stage-confirm" -> buildPromise {
                readDistance()
                if (manifest().spawnCount() <= 0) {
                    ui.notifications.warn(t("camping.encounterStageNoCreatures"))
                    return@buildPromise
                }
                val saved = manifest()
                onSaved(saved)
                close()
                stageEncounter(
                    game = game,
                    partyActor = partyActor,
                    manifest = saved,
                    startDistanceFt = startDistanceFt,
                    hidden = spawnHidden,
                )
            }

            "km-stage-save" -> {
                readDistance()
                onSaved(manifest())
                close()
            }

            "km-stage-cancel" -> close()
        }
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions,
    ): Promise<EncounterStageContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        // resolve names and levels once; an unresolvable uuid still lists, so the GM can remove it
        resolved = creatures.associate { row ->
            val actor = fromUuidTypeSafe<PF2EActor>(row.uuid)
            row.uuid to ((actor?.name ?: row.displayName ?: row.uuid) to (actor?.level ?: 0))
        }
        val members = runCatching { partyActor.partyMembers() }.getOrNull() ?: emptyArray()
        val partySize = members.size.coerceAtLeast(1)
        val partyLevel = members.maxOfOrNull { it.level } ?: 1
        val totalXp = creatures.sumOf { row ->
            val level = resolved[row.uuid]?.second ?: 0
            creatureXpContribution(partyLevel, level) * row.count.coerceAtLeast(0)
        }
        EncounterStageContext(
            partId = parent.partId,
            isFormValid = true,
            creatures = creatures.mapIndexed { index, row ->
                val (name, level) = resolved[row.uuid] ?: (row.uuid to 0)
                StageCreatureRowContext(
                    index = index,
                    uuid = row.uuid,
                    name = name,
                    count = row.count,
                    adjustment = row.adjustment,
                    xpNote = t(
                        "camping.encounterStageRowXp",
                        recordOf(
                            "level" to level,
                            "xp" to creatureXpContribution(partyLevel, level),
                        ),
                    ),
                )
            }.toTypedArray(),
            hasCreatures = creatures.isNotEmpty(),
            startDistanceFt = startDistanceFt,
            spawnHidden = spawnHidden,
            xpBudgetNote = xpBudgetNote,
            totalCount = manifest().spawnCount(),
            partyLevel = partyLevel,
            partySize = partySize,
            totalXp = totalXp,
            threatLabel = localizeThreat(threatForXp(partySize, totalXp)),
            moderateBudget = encounterBudget(partySize, EncounterThreat.MODERATE),
            severeBudget = encounterBudget(partySize, EncounterThreat.SEVERE),
        )
    }
}

/** Literal keys; a composed "threat.$value" would be invisible to the i18n scan. */
private fun localizeThreat(threat: EncounterThreat): String = when (threat) {
    EncounterThreat.TRIVIAL -> t("camping.encounterThreat.trivial")
    EncounterThreat.LOW -> t("camping.encounterThreat.low")
    EncounterThreat.MODERATE -> t("camping.encounterThreat.moderate")
    EncounterThreat.SEVERE -> t("camping.encounterThreat.severe")
    EncounterThreat.EXTREME -> t("camping.encounterThreat.extreme")
}

@JsPlainObject
external interface CreatureUuidData {
    val uuid: String
}

/** One-field prompt for a creature UUID; null when the GM cancels or leaves it blank. */
private suspend fun askCreatureUuid(): String? =
    at.posselt.pfrpg2e.app.awaitablePrompt<CreatureUuidData, String?>(
        title = t("camping.encounterStageAddCreature"),
        templatePath = "components/forms/form.hbs",
        templateContext = recordOf(
            "formRows" to at.posselt.pfrpg2e.app.forms.formContext(
                at.posselt.pfrpg2e.app.forms.TextInput(
                    name = "uuid",
                    label = t("camping.encounterStageCreatureUuid"),
                    value = "",
                    help = t("camping.encounterStageCreatureUuidHelp"),
                )
            )
        ),
    ) { data, _ -> data.uuid.trim().takeIf { it.isNotBlank() } }
