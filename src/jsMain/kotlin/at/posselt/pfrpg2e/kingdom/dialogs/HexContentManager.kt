package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.*
import at.posselt.pfrpg2e.app.toGenericRef
import at.posselt.pfrpg2e.data.hex.HexContentType
import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.kingdom.data.RawHexContent
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.buildUuid
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.applications.ux.TextEditor.TextEditor
import com.foundryvtt.core.data.dsl.buildSchema
import com.foundryvtt.core.utils.fromUuid
import com.foundryvtt.kingmaker.kingmaker
import js.core.Void
import kotlinx.browser.document
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
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
    val linkedQuestCount: Int
    val linkedDocCount: Int
    val hasWarThreat: Boolean
    val hasLinks: Boolean
}

// A kingdom quest the GM can toggle on/off as a hex reference.
@JsPlainObject
external interface HexQuestChoiceContext {
    val id: String
    val title: String
    val checked: Boolean
}

// A linked Foundry document, pre-enriched into a clickable content link.
@JsPlainObject
external interface HexLinkedDocContext {
    val uuid: String
    val link: String
}

@JsPlainObject
external interface HexContentManagerContext : ValidatedHandlebarsContext {
    val entries: Array<HexContentEntryContext>
    val formRows: Array<FormElementContext>
    val hexKeyOptions: Array<SelectOption>
    val typeOptions: Array<SelectOption>
    val visibilityOptions: Array<SelectOption>
    val linkedQuestChoices: Array<HexQuestChoiceContext>
    val linkedDocs: Array<HexLinkedDocContext>
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
    val linkedWarThreatId: String
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
            string("linkedWarThreatId", nullable = true)
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
    // This manager persists via an explicit Save button (saveContent + getFormData), not the
    // dataModel submit flow. With submitOnChange=true the form re-renders on every field change
    // and rebuilds the edit form from the still-unsaved entry, snapping selects (e.g. type) back
    // to their saved value — so changes could never "stick". Keep auto-submit off here.
    submitOnChange = false,
    width = 700,
    height = 600,
    id = "kmHexContentManager",
    dataModel = HexContentManagerModel::class.js,
) {
    private var editingId: String? = null
    private var isAdding: Boolean = false

    init {
        // Accept Foundry documents dropped onto the link zone (journals, actors, scenes, items).
        // Chips are added/removed via direct DOM updates (not a re-render) so unsaved edits to the
        // other fields aren't lost — everything is read back from the DOM on Save.
        on(".km-hex-drop-zone", "dragover") { event ->
            event.asDynamic().preventDefault()
            Unit
        }
        on(".km-hex-drop-zone", "drop") { event ->
            event.asDynamic().preventDefault()
            val raw = event.asDynamic().dataTransfer?.getData("text/plain") as? String
            raw?.let { toGenericRef(it) }?.let { ref ->
                buildPromise { addDroppedDoc(ref.uuid) }
            }
            Unit
        }
        on(".km-hex-link-list", "click") { event ->
            val target = event.asDynamic().target
            val remove = if (target != null && target != undefined) target.closest(".km-hex-link-remove") else null
            if (remove != null && remove != undefined) {
                event.asDynamic().preventDefault()
                val chip = remove.closest(".km-hex-link-chip")
                if (chip != null && chip != undefined) chip.remove()
            }
            Unit
        }
    }

    private fun currentKingdom() = actor.getKingdom()

    // Linked quests = new array plus the legacy single id (back-compat for content saved before this).
    private fun questIdsOf(content: RawHexContent?): Set<String> {
        if (content == null) return emptySet()
        val ids = (content.linkedQuestIds ?: emptyArray()).toMutableSet()
        content.linkedQuestId?.takeIf { it.isNotBlank() }?.let { ids.add(it) }
        return ids
    }

    private fun uuidsOf(content: RawHexContent?): List<String> {
        if (content == null) return emptyList()
        val uuids = (content.linkedUuids ?: emptyArray()).toMutableList()
        content.linkedUuid?.takeIf { it.isNotBlank() && it !in uuids }?.let { uuids.add(it) }
        return uuids
    }

    // Append a clickable link chip for a dropped document, resolving its name. No-op on duplicates.
    private suspend fun addDroppedDoc(uuid: String) {
        val root = element ?: return
        val list = root.querySelector(".km-hex-link-list") ?: return
        if (list.querySelector("[data-uuid=\"$uuid\"]") != null) return
        val doc = fromUuid(uuid).await()
        val name = (doc?.asDynamic()?.name as? String) ?: uuid
        val link = TextEditor.enrichHTML(buildUuid(uuid, name)).await()
        val chip = document.createElement("span")
        chip.className = "km-hex-link-chip"
        chip.setAttribute("data-uuid", uuid)
        chip.innerHTML = "$link <a class=\"km-hex-link-remove\" data-uuid=\"$uuid\" " +
            "title=\"${t("kingdom.hexContent.removeLink")}\">×</a>"
        list.appendChild(chip)
    }

    private fun collectLinkedQuestIds(): Array<String> {
        val root = element ?: return emptyArray()
        val boxes = root.querySelectorAll(".km-hex-quest-choices input[name=\"questLink\"]")
        val result = mutableListOf<String>()
        for (i in 0 until boxes.length) {
            val box = boxes[i] as? HTMLInputElement ?: continue
            if (box.checked) result.add(box.value)
        }
        return result.toTypedArray()
    }

    private fun collectLinkedUuids(): Array<String> {
        val root = element ?: return emptyArray()
        val chips = root.querySelectorAll(".km-hex-link-list .km-hex-link-chip")
        val result = mutableListOf<String>()
        for (i in 0 until chips.length) {
            val chip = chips[i] as? Element ?: continue
            chip.getAttribute("data-uuid")?.let { result.add(it) }
        }
        return result.toTypedArray()
    }

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
                linkedQuestIds = current.linkedQuestIds,
                linkedUuids = current.linkedUuids,
                linkedWarThreatId = current.linkedWarThreatId,
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

        // New multi-reference fields: quests from the checkboxes, documents from the chip DOM,
        // war threat from the select. Legacy single fields are cleared (migrated into the arrays).
        val questIds = collectLinkedQuestIds().takeIf { it.isNotEmpty() }
        val uuids = collectLinkedUuids().takeIf { it.isNotEmpty() }
        val warThreatId = (formData["linkedWarThreatId"] as? String)?.takeIf { it.isNotBlank() }

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
                    linkedQuestId = null,
                    linkedUuid = null,
                    linkedQuestIds = questIds,
                    linkedUuids = uuids,
                    linkedWarThreatId = warThreatId,
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
                    linkedQuestId = null,
                    linkedUuid = null,
                    linkedQuestIds = questIds,
                    linkedUuids = uuids,
                    linkedWarThreatId = warThreatId,
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
            val questCount = questIdsOf(content).size
            val docCount = uuidsOf(content).size
            val hasThreat = !content.linkedWarThreatId.isNullOrBlank()
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
                linkedQuestCount = questCount,
                linkedDocCount = docCount,
                hasWarThreat = hasThreat,
                hasLinks = questCount > 0 || docCount > 0 || hasThreat,
            )
        }.toTypedArray()

        val editingContent = if (editingId != null) contents.find { it.id == editingId } else null

        // War-threat dropdown options (a "none" entry plus each active threat).
        val warThreatOptions = listOf(
            SelectOption(value = "", label = t("kingdom.hexContent.noWarThreat")),
        ) + (kingdom?.warThreats ?: emptyArray()).map { threat ->
            SelectOption(value = threat.id, label = threat.name.ifBlank { threat.id })
        }

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
                Select(
                    name = "linkedWarThreatId",
                    label = t("kingdom.hexContent.linkedWarThreat"),
                    options = warThreatOptions,
                    value = editingContent?.linkedWarThreatId ?: "",
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

        // Quest reference checkboxes: every kingdom quest, pre-checked if already linked.
        val linkedQuestIds = questIdsOf(editingContent)
        val linkedQuestChoices = if (isAdding || editingId != null) {
            (kingdom?.quests ?: emptyArray()).map { quest ->
                HexQuestChoiceContext(
                    id = quest.id,
                    title = quest.title.ifBlank { quest.id },
                    checked = quest.id in linkedQuestIds,
                )
            }.toTypedArray()
        } else {
            emptyArray()
        }

        // Resolve already-linked documents into clickable content links for the chips.
        val linkedDocs = uuidsOf(editingContent).map { uuid ->
            val doc = fromUuid(uuid).await()
            val name = (doc?.asDynamic()?.name as? String) ?: uuid
            HexLinkedDocContext(
                uuid = uuid,
                link = TextEditor.enrichHTML(buildUuid(uuid, name)).await(),
            )
        }.toTypedArray()

        HexContentManagerContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            entries = entries,
            formRows = formRows,
            hexKeyOptions = hexKeyOptions.toTypedArray(),
            typeOptions = typeOptions.toTypedArray(),
            visibilityOptions = visibilityOptions.toTypedArray(),
            linkedQuestChoices = linkedQuestChoices,
            linkedDocs = linkedDocs,
            isEditing = editingId != null,
            isAdding = isAdding,
            editId = editingId,
        )
    }

    override fun onParsedSubmit(value: HexContentManagerData): Promise<Void> = buildPromise {
        null
    }
}
