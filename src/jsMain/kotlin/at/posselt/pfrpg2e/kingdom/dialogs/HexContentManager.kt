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
import at.posselt.pfrpg2e.kingdom.data.carryHexEngineState
import at.posselt.pfrpg2e.kingdom.data.RawLootManifestEntry
import at.posselt.pfrpg2e.kingdom.data.toModel
import at.posselt.pfrpg2e.kingdom.loot.manifestTotals
import at.posselt.pfrpg2e.kingdom.loot.postLootAwardOffer
import at.posselt.pfrpg2e.kingdom.xp.proposeXpOffer
import com.foundryvtt.core.game
import js.objects.recordOf
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

    /** Loot manifest summary for the list row (loot-manifests SS4.1). */
    val hasManifest: Boolean
    val lootSummary: String
    val manifestAwarded: Boolean
}

/** One editable manifest row; collected back from the DOM on Save, never through the scalar schema. */
@JsPlainObject
external interface HexLootRowContext {
    val uuid: String
    val link: String
    val name: String
    val quantity: Int
    val gpValue: Double
    val cursed: Boolean
    val note: String
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
    val lootRows: Array<HexLootRowContext>
    val lootSummary: String
    val manifestAwarded: Boolean
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
        // A DEDICATED zone so item drops cannot mix with reference-link drops (plan SS4.1).
        on(".km-hex-loot-zone", "dragover") { event ->
            event.asDynamic().preventDefault()
            Unit
        }
        on(".km-hex-loot-zone", "drop") { event ->
            event.asDynamic().preventDefault()
            val raw = event.asDynamic().dataTransfer?.getData("text/plain") as? String
            raw?.let { toGenericRef(it) }?.let { ref ->
                buildPromise { addDroppedLootItem(ref.uuid) }
            }
            Unit
        }
        on(".km-hex-loot-list", "click") { event ->
            val target = event.asDynamic().target
            val remove = if (target != null && target != undefined) target.closest(".km-hex-loot-remove") else null
            if (remove != null && remove != undefined) {
                event.asDynamic().preventDefault()
                val row = remove.closest(".km-hex-loot-row")
                if (row != null && row != undefined) {
                    row.remove()
                    refreshLootSummary()
                }
            }
            Unit
        }
        on(".km-hex-loot-list", "change") { _ ->
            refreshLootSummary()
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

    /**
     * Append a manifest row for a dropped item, pre-filling price and the cursed trait from the
     * document so the GM edits rather than types. Duplicates are allowed: two of the same item at
     * different notes/prices is a legitimate manifest, unlike a duplicate reference link.
     */
    private suspend fun addDroppedLootItem(uuid: String) {
        val root = element ?: return
        val list = root.querySelector(".km-hex-loot-list") ?: return
        val doc = fromUuid(uuid).await()
        val name = (doc?.asDynamic()?.name as? String) ?: uuid
        val gp = runCatching {
            (doc?.asDynamic()?.system?.price?.value?.gp as? Number)?.toDouble()
        }.getOrNull() ?: 0.0
        val cursed = runCatching {
            val traits = doc?.asDynamic()?.system?.traits?.value
            traits != null && traits != undefined && (traits.includes("cursed") as? Boolean) == true
        }.getOrDefault(false)
        val link = TextEditor.enrichHTML(buildUuid(uuid, name)).await()
        val row = document.createElement("div")
        row.className = "km-hex-loot-row"
        row.setAttribute("data-uuid", uuid)
        row.setAttribute("data-name", name)
        row.innerHTML = buildLootRowHtml(link, quantity = 1, gpValue = gp, cursed = cursed, note = "")
        list.appendChild(row)
        refreshLootSummary()
    }

    private fun buildLootRowHtml(
        link: String,
        quantity: Int,
        gpValue: Double,
        cursed: Boolean,
        note: String,
    ): String =
        """<span class="km-hex-loot-link">$link</span>
        <input type="number" class="km-hex-loot-qty" min="0" value="$quantity" title="${t("kingdom.hexContent.loot.quantity")}">
        <input type="number" class="km-hex-loot-gp" min="0" step="0.01" value="$gpValue" title="${t("kingdom.hexContent.loot.gpValue")}">
        <label class="km-hex-loot-cursed" title="${t("kingdom.hexContent.loot.cursed")}">
            <input type="checkbox" ${if (cursed) "checked" else ""}> ${t("kingdom.hexContent.loot.cursedShort")}
        </label>
        <input type="text" class="km-hex-loot-note" value="$note" placeholder="${t("kingdom.hexContent.loot.note")}">
        <a class="km-hex-loot-remove" title="${t("kingdom.hexContent.loot.remove")}">×</a>"""

    /** Reads the rows exactly as the collection on Save will, so the line can never disagree. */
    private fun refreshLootSummary() {
        val root = element ?: return
        val target = root.querySelector(".km-hex-loot-summary") ?: return
        val items = collectLootManifest().mapNotNull { it.toModel() }
        val totals = manifestTotals(items)
        target.textContent = t(
            "kingdom.hexContent.loot.summary",
            recordOf(
                "count" to items.size.toString(),
                "gp" to totals.totalGp.toString(),
                "cursed" to totals.cursedCount.toString(),
            ),
        )
    }

    /**
     * The manifest is a VARIABLE-LENGTH array, so it is read back from the DOM like the link
     * chips rather than through the scalar HexContentManagerModel schema (plan SS4.1).
     */
    private fun collectLootManifest(): Array<RawLootManifestEntry> {
        val root = element ?: return emptyArray()
        val rows = root.querySelectorAll(".km-hex-loot-list .km-hex-loot-row")
        val result = mutableListOf<RawLootManifestEntry>()
        for (i in 0 until rows.length) {
            val row = rows[i] as? Element ?: continue
            val entry = js("{}").unsafeCast<RawLootManifestEntry>()
            entry.itemUuid = row.getAttribute("data-uuid")
            entry.name = row.getAttribute("data-name")
            entry.quantity = (row.querySelector(".km-hex-loot-qty") as? HTMLInputElement)
                ?.value?.toIntOrNull() ?: 1
            entry.gpValue = (row.querySelector(".km-hex-loot-gp") as? HTMLInputElement)
                ?.value?.toDoubleOrNull() ?: 0.0
            entry.cursed = (row.querySelector(".km-hex-loot-cursed input") as? HTMLInputElement)?.checked == true
            entry.note = (row.querySelector(".km-hex-loot-note") as? HTMLInputElement)
                ?.value?.takeIf { it.isNotBlank() }
            result.add(entry)
        }
        return result.toTypedArray()
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
                    // Clearing SURFACES the treasure offer; it never moves items (plan SS5.1).
                    val kingdom = currentKingdom()
                    kingdom?.hexContents?.find { it.id == contentId }
                        ?.let { content ->
                            postLootAwardOffer(game, actor, content)
                            // party XP for the site, recorded silently and answered in the End
                            // Turn digest (xp-ledger plan 6)
                            game.proposeXpOffer(
                                kind = at.posselt.pfrpg2e.kingdom.xp.XpSourceKind.SITE_CLEARED,
                                sourceRef = content.id,
                                turn = kingdom.currentTurn ?: 0,
                                note = content.name,
                            )
                        }
                    onContentChanged()
                    render()
                }
            }

            "award-loot" -> {
                val contentId = target.dataset["contentId"] ?: return
                buildPromise {
                    // same offer without re-clearing, for a hex cleared before it was prepped
                    currentKingdom()?.hexContents?.find { it.id == contentId }
                        ?.let { postLootAwardOffer(game, actor, it) }
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
            ).let { carryHexEngineState(it, current) }
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
        val manifest = collectLootManifest().takeIf { it.isNotEmpty() }

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
                ).let { rebuilt ->
                    // carry engine-owned state first, then let the dialog's edited loot manifest
                    // win -- the form DOES render that one
                    carryHexEngineState(rebuilt, existing).also { it.lootManifest = manifest }
                }
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
            val manifestItems = (content.lootManifest ?: emptyArray()).mapNotNull { it.toModel() }
            val manifestTotalsForRow = manifestTotals(manifestItems)
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
                hasManifest = manifestItems.isNotEmpty(),
                lootSummary = t(
                    "kingdom.hexContent.loot.summary",
                    recordOf(
                        "count" to manifestItems.size.toString(),
                        "gp" to manifestTotalsForRow.totalGp.toString(),
                        "cursed" to manifestTotalsForRow.cursedCount.toString(),
                    ),
                ),
                manifestAwarded = content.manifestAwarded == true,
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

        // Manifest rows for the editor, resolved to clickable links like the reference chips.
        val editingManifest = (editingContent?.lootManifest ?: emptyArray()).mapNotNull { it.toModel() }
        val editingTotals = manifestTotals(editingManifest)
        val lootRows = (editingContent?.lootManifest ?: emptyArray()).mapNotNull { raw ->
            val model = raw.toModel() ?: return@mapNotNull null
            val uuid = raw.itemUuid.orEmpty()
            HexLootRowContext(
                uuid = uuid,
                link = if (uuid.isNotBlank()) {
                    TextEditor.enrichHTML(buildUuid(uuid, model.name)).await()
                } else {
                    model.name
                },
                name = model.name,
                quantity = model.qty,
                gpValue = model.gpValue,
                cursed = model.cursed,
                note = model.note.orEmpty(),
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
            lootRows = lootRows,
            lootSummary = t(
                "kingdom.hexContent.loot.summary",
                recordOf(
                    "count" to editingManifest.size.toString(),
                    "gp" to editingTotals.totalGp.toString(),
                    "cursed" to editingTotals.cursedCount.toString(),
                ),
            ),
            manifestAwarded = editingContent?.manifestAwarded == true,
            isEditing = editingId != null,
            isAdding = isAdding,
            editId = editingId,
        )
    }

    override fun onParsedSubmit(value: HexContentManagerData): Promise<Void> = buildPromise {
        null
    }
}
