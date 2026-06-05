package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.forms.SimpleApp
import at.posselt.pfrpg2e.kingdom.structures.RawNpcEntry
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

/**
 * Dialog for adding a new NPC to the settlement population roster.
 */
@JsPlainObject
external interface PopulationAddContext : HandlebarsRenderContext {
    val npcName: String
    val occupation: String
    val notes: String
    val occupations: Array<String>
}

class PopulationAddDialog(
    private val occupations: Array<String>,
    private val onAdd: suspend (RawNpcEntry) -> Unit,
) : SimpleApp<PopulationAddContext>(
    title = t("kingdom.population.addNpc"),
    template = "applications/kingdom/population-add.hbs",
    id = "kmPopulationAddDialog",
    classes = setOf("km-population-add-dialog"),
) {
    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "save" -> buildPromise {
                val name = element.querySelector("input[name='npcName']")
                    ?.let { it as? org.w3c.dom.HTMLInputElement }
                    ?.value?.takeIf { it.isNotBlank() } ?: ""
                if (name.isNotBlank()) {
                    val occupation = element.querySelector("select[name='npcOccupation']")
                        ?.let { it as? org.w3c.dom.HTMLSelectElement }
                        ?.value ?: ""
                    val notes = element.querySelector("textarea[name='npcNotes']")
                        ?.let { it as? org.w3c.dom.HTMLTextAreaElement }
                        ?.value ?: ""

                    val npc = RawNpcEntry(
                        id = "npc-${kotlin.random.Random.nextLong()}",
                        name = name,
                        occupation = occupation,
                    ).also {
                        it.notes = notes.ifBlank { null }
                    }
                    onAdd(npc)
                    close()
                }
            }

            "cancel" -> close()
        }
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions,
    ): Promise<PopulationAddContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        PopulationAddContext(
            partId = parent.partId,
            npcName = "",
            occupation = occupations.firstOrNull() ?: "",
            notes = "",
            occupations = occupations,
        )
    }
}

/**
 * Dialog for editing an existing NPC in the settlement population roster.
 */
@JsPlainObject
external interface PopulationEditContext : HandlebarsRenderContext {
    val npcName: String
    val occupation: String
    val notes: String
    val occupations: Array<String>
}

class PopulationEditDialog(
    private val occupations: Array<String>,
    private val existing: RawNpcEntry,
    private val onSave: suspend (RawNpcEntry) -> Unit,
    private val onDelete: suspend () -> Unit,
) : SimpleApp<PopulationEditContext>(
    title = t("kingdom.population.editNpc"),
    template = "applications/kingdom/population-edit.hbs",
    id = "kmPopulationEditDialog",
    classes = setOf("km-population-edit-dialog"),
) {
    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "save" -> buildPromise {
                val name = element.querySelector("input[name='npcName']")
                    ?.let { it as? org.w3c.dom.HTMLInputElement }
                    ?.value?.takeIf { it.isNotBlank() } ?: existing.name
                val occupation = element.querySelector("select[name='npcOccupation']")
                    ?.let { it as? org.w3c.dom.HTMLSelectElement }
                    ?.value ?: existing.occupation
                val notes = element.querySelector("textarea[name='npcNotes']")
                    ?.let { it as? org.w3c.dom.HTMLTextAreaElement }
                    ?.value ?: (existing.notes ?: "")

                val updated = RawNpcEntry(
                    id = existing.id,
                    name = name,
                    occupation = occupation,
                ).also {
                    it.notes = notes.ifBlank { null }
                }
                onSave(updated)
                close()
            }

            "delete" -> buildPromise {
                onDelete()
                close()
            }

            "cancel" -> close()
        }
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions,
    ): Promise<PopulationEditContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        PopulationEditContext(
            partId = parent.partId,
            npcName = existing.name,
            occupation = existing.occupation,
            notes = existing.notes ?: "",
            occupations = occupations,
        )
    }
}
