package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.*
import at.posselt.pfrpg2e.data.hex.HexContentType
import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.kingdom.data.RawHexContent
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import com.foundryvtt.kingmaker.kingmaker
import js.core.Void
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

// ── Data / Context ──

private data class HexOption(val key: String, val label: String)

@JsPlainObject
external interface HexContentEntryContext {
    val id: String
    val hexKey: String
    val hexLabel: String
    val type: String
    val typeName: String
    val name: String
    val visibility: String
    val visibilityName: String
    val hasGmNotes: Boolean
}

@JsPlainObject
external interface HexContentManagerContext : ValidatedHandlebarsContext {
    val entries: Array<HexContentEntryContext>
    val formRows: Array<FormElementContext>
    val hexKeyOptions: Array<SelectOption>
    val typeOptions: Array<SelectOption>
    val visibilityOptions: Array<SelectOption>
    val isEditing: Boolean
    val editId: String?
    val isAdding: Boolean
}

@JsPlainObject
external interface HexContentManagerData {
    val hexKey: String
    val type: String
    val name: String
    val gmNotes: String
    val playerText: String
    val visibility: String
    val suppressesEncounters: Boolean
    val travelModifier: Int
    val linkedQuestId: String
    val linkedUuid: String
    val icon: String
}

@JsExport
class HexContentManagerModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("hexKey")
            string("type")
            string("name")
            string("gmNotes")
            string("playerText")
            string("visibility")
            boolean("suppressesEncounters")
            int("travelModifier")
            string("linkedQuestId", nullable = true)
            string("linkedUuid", nullable = true)
            string("icon", nullable = true)
        }
    }
}

// ── Manager Dialog ──

class HexContentManager(
    private val actor: KingdomActor,
    private val onContentChanged: suspend () -> Unit = {},
) : FormApp<HexContentManagerContext, HexContentManagerData>(
    title = t("kingdom.hexContent.managerTitle"),
    template = "applications/kingdom/hex-content-manager.hbs",
    width = 700,
    height = 600,
    id = "kmHexContentManager",
    dataModel = HexContentManagerModel::class.js,
) {
    private var editingId: String? = null
    private var isAdding: Boolean = false

    private fun currentKingdom() = actor.getKingdom()

    // (native key string, human-readable label) for every hex in the region.
    // The native key is `1000*row + col`; the map labels hexes "row.col", so we
    // show that coordinate (plus the hex name when it differs, e.g. a journal page).
    private fun getHexOptions(): List<HexOption> =
        kingmaker.region.hexes.contents.map { hex ->
            val key = hex.key
            val coord = "${key / 1000}.${key % 1000}"
            val name = hex.name
            val label = if (name.isBlank() || name == coord) coord else "$coord — $name"
            HexOption(key = key.toString(), label = label)
        }

    private fun hexLabelFor(key: String, options: List<HexOption>): String =
        options.find { it.key == key }?.label
            ?: key.toIntOrNull()?.let { "${it / 1000}.${it % 1000}" }
            ?: key

    private fun getHexContents(): Array<RawHexContent> {
        val kingdom = currentKingdom() ?: return emptyArray()
        return kingdom.hexContents ?: emptyArray()
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        val action = target.dataset["action"] ?: return
        when (action) {
            "add-new" -> {
                editingId = null
                isAdding = true
                render()
            }
            "edit" -> {
                editingId = target.dataset["contentId"]
                isAdding = false
                render()
            }
            "delete" -> {
                val contentId = target.dataset["contentId"] ?: return
                buildPromise {
                    deleteContent(contentId)
                    onContentChanged()
                    render()
                }
            }
            "reveal" -> {
                val contentId = target.dataset["contentId"] ?: return
                buildPromise {
                    updateVisibility(contentId, at.posselt.pfrpg2e.kingdom.map.DiscoveryEvent.DISCOVER)
                    onContentChanged()
                    render()
                }
            }
            "hide" -> {
                val contentId = target.dataset["contentId"] ?: return
                buildPromise {
                    updateVisibility(contentId, at.posselt.pfrpg2e.kingdom.map.DiscoveryEvent.RESET)
                    onContentChanged()
                    render()
                }
            }
            "clear" -> {
                val contentId = target.dataset["contentId"] ?: return
                buildPromise {
                    updateVisibility(contentId, at.posselt.pfrpg2e.kingdom.map.DiscoveryEvent.CLEAR)
                    onContentChanged()
                    render()
                }
            }
            "cancel-edit" -> {
                editingId = null
                isAdding = false
                render()
            }
            "save" -> {
                buildPromise {
                    saveContent()
                    onContentChanged()
                    editingId = null
                    isAdding = false
                    render()
                }
            }
        }
    }

    private suspend fun deleteContent(contentId: String) {
        val kingdom = currentKingdom() ?: return
        val contents = getHexContents().toMutableList()
        contents.removeAll { it.id == contentId }
        kingdom.hexContents = contents.toTypedArray()
        actor.setKingdom(kingdom)
    }

    private suspend fun updateVisibility(contentId: String, event: at.posselt.pfrpg2e.kingdom.map.DiscoveryEvent) {
        val kingdom = currentKingdom() ?: return
        val contents = getHexContents().toMutableList()
        val idx = contents.indexOfFirst { it.id == contentId }
        if (idx >= 0) {
            val current = contents[idx]
            val currentVis = HexContentVisibility.fromString(current.visibility) ?: HexContentVisibility.HIDDEN
            val newVis = at.posselt.pfrpg2e.kingdom.map.nextVisibility(currentVis, event)
            contents[idx] = RawHexContent(
                id = current.id,
                hexKey = current.hexKey,
                type = current.type,
                name = current.name,
                visibility = newVis.value,
                gmNotes = current.gmNotes,
                playerText = current.playerText,
                suppressesEncounters = current.suppressesEncounters,
                travelModifier = current.travelModifier,
                linkedQuestId = current.linkedQuestId,
                linkedUuid = current.linkedUuid,
                icon = current.icon,
            )
            kingdom.hexContents = contents.toTypedArray()
            actor.setKingdom(kingdom)
        }
    }

    private suspend fun saveContent() {
        val kingdom = currentKingdom() ?: return
        val contents = getHexContents().toMutableList()
        val formData = getFormData()

        if (editingId != null) {
            val idx = contents.indexOfFirst { it.id == editingId }
            if (idx >= 0) {
                val existing = contents[idx]
                contents[idx] = RawHexContent(
                    id = existing.id,
                    hexKey = formData["hexKey"] as? String ?: existing.hexKey,
                    type = formData["type"] as? String ?: existing.type,
                    name = formData["name"] as? String ?: existing.name,
                    visibility = formData["visibility"] as? String ?: existing.visibility,
                    gmNotes = formData["gmNotes"] as? String ?: existing.gmNotes,
                    playerText = formData["playerText"] as? String ?: existing.playerText,
                    suppressesEncounters = formData["suppressesEncounters"] as? Boolean,
                    travelModifier = formData["travelModifier"] as? Int,
                    linkedQuestId = formData["linkedQuestId"] as? String,
                    linkedUuid = formData["linkedUuid"] as? String,
                    icon = formData["icon"] as? String,
                )
            }
        } else {
            val newId = "hexcontent-${kotlin.js.js("Date.now()")}"
            contents.add(
                RawHexContent(
                    id = newId,
                    hexKey = formData["hexKey"] as? String ?: "",
                    type = formData["type"] as? String ?: HexContentType.LANDMARK.value,
                    name = formData["name"] as? String ?: "",
                    visibility = formData["visibility"] as? String ?: HexContentVisibility.HIDDEN.value,
                    gmNotes = formData["gmNotes"] as? String ?: "",
                    playerText = formData["playerText"] as? String ?: "",
                    suppressesEncounters = formData["suppressesEncounters"] as? Boolean,
                    travelModifier = formData["travelModifier"] as? Int,
                    linkedQuestId = formData["linkedQuestId"] as? String,
                    linkedUuid = formData["linkedUuid"] as? String,
                    icon = formData["icon"] as? String,
                )
            )
        }
        kingdom.hexContents = contents.toTypedArray()
        actor.setKingdom(kingdom)
    }

    private fun getFormData(): Map<String, Any?> {
        val form = element?.querySelector("form") ?: return emptyMap()
        val data = mutableMapOf<String, Any?>()
        val elements = form.querySelectorAll("input, select, textarea")
        for (i in 0 until elements.length) {
            val el = elements[i] as? HTMLElement ?: continue
            val name = el.getAttribute("name") ?: continue
            when (el.tagName) {
                "SELECT" -> data[name] = (el as? org.w3c.dom.HTMLSelectElement)?.value
                "INPUT" -> {
                    val input = el as? org.w3c.dom.HTMLInputElement
                    when (input?.type) {
                        "checkbox" -> data[name] = input.checked
                        "number" -> data[name] = input.value.toIntOrNull()
                        else -> data[name] = input?.value
                    }
                }
                "TEXTAREA" -> data[name] = (el as? org.w3c.dom.HTMLTextAreaElement)?.value
            }
        }
        return data
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions,
    ): Promise<HexContentManagerContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        val kingdom = currentKingdom()
        val contents = getHexContents()

        val typeOptions = HexContentType.entries.map { type ->
            SelectOption(
                value = type.value,
                label = t("hexContentType.${type.value}")
            )
        }

        val visibilityOptions = HexContentVisibility.entries.map { vis ->
            SelectOption(
                value = vis.value,
                label = t("hexContentVisibility.${vis.value}")
            )
        }

        val hexOptions = getHexOptions()
        val existingHexKeys = contents.map { it.hexKey }.toSet()
        val availableHexOptions = hexOptions.filter { it.key !in existingHexKeys }
        val hexKeyOptions = availableHexOptions.map { option ->
            SelectOption(value = option.key, label = option.label)
        }
        // Pre-select the hex the GM last clicked on the Kingmaker map (if it's
        // still free), so adding content is a click-then-add flow.
        val defaultHexKey = at.posselt.pfrpg2e.kingdom.map.lastSelectedHexKey
            ?.takeIf { sel -> availableHexOptions.any { it.key == sel } }

        val entries = contents.map { content ->
            val type = HexContentType.fromString(content.type)
            val vis = HexContentVisibility.fromString(content.visibility)
            HexContentEntryContext(
                id = content.id,
                hexKey = content.hexKey,
                hexLabel = hexLabelFor(content.hexKey, hexOptions),
                type = content.type,
                typeName = if (type != null) t("hexContentType.${type.value}") else content.type,
                name = content.name,
                visibility = content.visibility,
                visibilityName = if (vis != null) t("hexContentVisibility.${vis.value}") else content.visibility,
                hasGmNotes = content.gmNotes.isNotEmpty(),
            )
        }.toTypedArray()

        val editingContent = if (editingId != null) contents.find { it.id == editingId } else null

        val formRows = if (isAdding || editingId != null) {
            formContext(
                Select(
                    name = "hexKey",
                    label = t("kingdom.hexContent.hexKey"),
                    options = if (editingContent != null) {
                        listOf(
                            SelectOption(
                                value = editingContent.hexKey,
                                label = hexLabelFor(editingContent.hexKey, hexOptions),
                            )
                        ) + hexKeyOptions
                    } else {
                        hexKeyOptions
                    },
                    value = editingContent?.hexKey ?: defaultHexKey,
                    stacked = false,
                ),
                Select(
                    name = "type",
                    label = t("kingdom.hexContent.type"),
                    options = typeOptions,
                    value = editingContent?.type ?: HexContentType.LANDMARK.value,
                    stacked = false,
                ),
                TextInput(
                    name = "name",
                    label = t("kingdom.hexContent.name"),
                    value = editingContent?.name ?: "",
                    stacked = false,
                ),
                Select(
                    name = "visibility",
                    label = t("kingdom.hexContent.visibility"),
                    options = visibilityOptions,
                    value = editingContent?.visibility ?: HexContentVisibility.HIDDEN.value,
                    stacked = false,
                ),
                TextArea(
                    name = "gmNotes",
                    label = t("kingdom.hexContent.gmNotes"),
                    value = editingContent?.gmNotes ?: "",
                    stacked = true,
                ),
                TextArea(
                    name = "playerText",
                    label = t("kingdom.hexContent.playerText"),
                    value = editingContent?.playerText ?: "",
                    stacked = true,
                ),
                CheckboxInput(
                    name = "suppressesEncounters",
                    label = t("kingdom.hexContent.suppressesEncounters"),
                    value = editingContent?.suppressesEncounters ?: false,
                ),
                NumberInput(
                    name = "travelModifier",
                    label = t("kingdom.hexContent.travelModifier"),
                    value = editingContent?.travelModifier ?: 0,
                    stacked = false,
                    required = false,
                ),
                TextInput(
                    name = "linkedQuestId",
                    label = t("kingdom.hexContent.linkedQuestId"),
                    value = editingContent?.linkedQuestId ?: "",
                    stacked = false,
                    required = false,
                ),
                TextInput(
                    name = "linkedUuid",
                    label = t("kingdom.hexContent.linkedUuid"),
                    value = editingContent?.linkedUuid ?: "",
                    stacked = false,
                    required = false,
                ),
                TextInput(
                    name = "icon",
                    label = t("kingdom.hexContent.icon"),
                    value = editingContent?.icon ?: "",
                    stacked = false,
                    required = false,
                ),
            )
        } else {
            emptyArray()
        }

        HexContentManagerContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            entries = entries,
            formRows = formRows,
            hexKeyOptions = hexKeyOptions.toTypedArray(),
            typeOptions = typeOptions.toTypedArray(),
            visibilityOptions = visibilityOptions.toTypedArray(),
            isEditing = editingId != null,
            isAdding = isAdding,
            editId = editingId,
        )
    }

    override fun onParsedSubmit(value: HexContentManagerData): Promise<Void> = buildPromise {
        null
    }
}
