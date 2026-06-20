package at.posselt.pfrpg2e.kingdom.map

import at.posselt.pfrpg2e.app.toGenericRef
import at.posselt.pfrpg2e.data.hex.HexContentType
import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.getKingdomActors
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.kingdom.data.RawHexContent
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.buildUuid
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.Game
import com.foundryvtt.core.applications.ux.TextEditor.TextEditor
import com.foundryvtt.core.helpers.TypedHooks
import com.foundryvtt.core.utils.fromUuid
import com.foundryvtt.kingmaker.onRenderHexEditor
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.get

/**
 * Injects a "Linked References" panel (quests / Foundry documents / war threat) into the
 * native pf2e-kingmaker hex editor (the `HexEditor` ApplicationV2 opened by double-clicking a
 * hex). The native editor's own Save validates + strips unknown fields, so this panel persists
 * independently: every change is applied immediately to this module's per-hex content entry
 * (`kingdom.hexContents`, keyed by hex key) — the same data the Hex Content Manager edits.
 */
fun registerHexEditorLinks(game: Game) {
    if (!game.user.isGM) return
    TypedHooks.onRenderHexEditor { app, html, _ ->
        buildPromise { injectHexLinksPanel(game, app, html) }
    }
}

private fun hexKeyOf(app: AnyObject): String? {
    val fromOptions = app.asDynamic().options?.hex?.key
    val key = (fromOptions as? Int)?.toString()
        ?: (fromOptions as? Double)?.toInt()?.toString()
        ?: (app.asDynamic().hex?.key as? Int)?.toString()
    return key ?: lastSelectedHexKey
}

private fun hexCoordLabel(hexKey: String): String {
    val k = hexKey.toIntOrNull() ?: return hexKey
    return "${k / 1000}.${k % 1000}"
}

private fun escapeHtml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

// Linked quests/uuids = the new arrays plus the legacy single fields (back-compat).
private fun questIdsOf(e: RawHexContent?): Set<String> {
    if (e == null) return emptySet()
    val ids = (e.linkedQuestIds ?: emptyArray()).toMutableSet()
    e.linkedQuestId?.takeIf { it.isNotBlank() }?.let { ids.add(it) }
    return ids
}

private fun uuidsOf(e: RawHexContent?): List<String> {
    if (e == null) return emptyList()
    val u = (e.linkedUuids ?: emptyArray()).toMutableList()
    e.linkedUuid?.takeIf { it.isNotBlank() && it !in u }?.let { u.add(it) }
    return u
}

private suspend fun injectHexLinksPanel(game: Game, app: AnyObject, html: HTMLElement) {
    if (html.querySelector(".km-hex-editor-links") != null) return // already injected
    val hexKey = hexKeyOf(app)
    if (hexKey == null) {
        console.warn("[km] HexEditor links: could not resolve the hex key — no Linked References panel injected")
        return
    }
    val actor = game.getKingdomActors().firstOrNull()
    if (actor == null) {
        console.warn("[km] HexEditor links: no kingdom actor found")
        return
    }
    val kingdom = actor.getKingdom() ?: return

    val entry = (kingdom.hexContents ?: emptyArray()).firstOrNull { it.hexKey == hexKey }
    val quests = kingdom.quests ?: emptyArray()
    val warThreats = kingdom.warThreats ?: emptyArray()
    val linkedQuestIds = questIdsOf(entry)

    val sb = StringBuilder()
    sb.append("<legend>${escapeHtml(t("kingdom.hexContent.linkedReferences"))}</legend>")

    if (quests.isNotEmpty()) {
        sb.append("<div class=\"form-group km-hexed-stack\"><label>${escapeHtml(t("kingdom.hexContent.linkedQuests"))}</label>")
        sb.append("<div class=\"km-hexed-quests\">")
        quests.forEach { q ->
            val checked = if (q.id in linkedQuestIds) "checked" else ""
            sb.append("<label class=\"km-hexed-quest\"><input type=\"checkbox\" data-km-quest=\"${escapeHtml(q.id)}\" $checked> <span>${escapeHtml(q.title.ifBlank { q.id })}</span></label>")
        }
        sb.append("</div></div>")
    }

    if (warThreats.isNotEmpty()) {
        sb.append("<div class=\"form-group\"><label>${escapeHtml(t("kingdom.hexContent.linkedWarThreat"))}</label>")
        sb.append("<select class=\"km-hexed-threat\" data-km-threat><option value=\"\">${escapeHtml(t("kingdom.hexContent.noWarThreat"))}</option>")
        warThreats.forEach { th ->
            val sel = if (th.id == entry?.linkedWarThreatId) "selected" else ""
            sb.append("<option value=\"${escapeHtml(th.id)}\" $sel>${escapeHtml(th.name.ifBlank { th.id })}</option>")
        }
        sb.append("</select></div>")
    }

    sb.append("<div class=\"form-group km-hexed-stack\"><label>${escapeHtml(t("kingdom.hexContent.linkedDocuments"))}</label>")
    sb.append("<div class=\"km-hexed-drop\"><i class=\"fa-solid fa-hand-pointer\"></i> ${escapeHtml(t("kingdom.hexContent.dropHint"))}</div>")
    sb.append("<div class=\"km-hexed-chips\">")
    for (uuid in uuidsOf(entry)) {
        val name = resolveName(uuid)
        val link = TextEditor.enrichHTML(buildUuid(uuid, name)).await()
        sb.append(chipHtml(uuid, link))
    }
    sb.append("</div></div>")

    val fieldset = document.createElement("fieldset")
    fieldset.className = "km-hex-editor-links"
    fieldset.innerHTML = sb.toString()

    // Insert among the native sections — before the first <fieldset>, inside .window-content.
    // (NOT before html.firstElementChild, which is the window header: that put the panel
    // outside the form content.)
    val firstFieldset = html.querySelector("fieldset")
    val fieldsetParent = firstFieldset?.parentElement
    if (fieldsetParent != null) {
        fieldsetParent.insertBefore(fieldset, firstFieldset)
    } else {
        (html.querySelector(".window-content") ?: html).appendChild(fieldset)
    }

    attachListeners(game, hexKey, fieldset)

    // Make every future open wider (≈2×) and user-resizable at a bounded height — patch the
    // class defaults once. The ApplicationV2 frame for THIS already-open instance can't gain a
    // resize handle retroactively, so its size is set directly below.
    runCatching {
        val ctor = app.asDynamic().constructor
        if (ctor.__kmHexResizable != true) {
            ctor.__kmHexResizable = true
            ctor.DEFAULT_OPTIONS.window.resizable = true
            ctor.DEFAULT_OPTIONS.position.width = 840
            ctor.DEFAULT_OPTIONS.position.height = 640
        }
    }
    // Widen + bound the current window's height so it fits the screen and the content scrolls
    // vertically (Save reachable) instead of growing past the viewport bottom.
    val targetHeight = kotlin.math.min(640, window.innerHeight - 80)
    window.requestAnimationFrame {
        val pos = js("({})")
        pos.width = 840
        pos.height = targetHeight
        app.asDynamic().setPosition(pos)
        Unit
    }
}

private suspend fun resolveName(uuid: String): String {
    val doc = fromUuid(uuid).await()
    return (doc?.asDynamic()?.name as? String) ?: uuid
}

private fun chipHtml(uuid: String, linkHtml: String): String =
    "<span class=\"km-hexed-chip\" data-uuid=\"${escapeHtml(uuid)}\">$linkHtml " +
        "<a class=\"km-hexed-remove\" data-uuid=\"${escapeHtml(uuid)}\" title=\"${escapeHtml(t("kingdom.hexContent.removeLink"))}\">×</a></span>"

private fun attachListeners(game: Game, hexKey: String, panel: Element) {
    // Quest checkboxes: on any toggle, persist all currently-checked quests.
    val boxes = panel.querySelectorAll("input[data-km-quest]")
    for (i in 0 until boxes.length) {
        (boxes[i] as? HTMLInputElement)?.asDynamic()?.addEventListener("change", {
            buildPromise { saveQuests(game, hexKey, panel) }
            Unit
        })
    }

    panel.querySelector("[data-km-threat]")?.asDynamic()?.addEventListener("change", { ev: dynamic ->
        val value = ev.target.value as? String
        buildPromise { saveThreat(game, hexKey, value) }
        Unit
    })

    val drop = panel.querySelector(".km-hexed-drop")
    drop?.asDynamic()?.addEventListener("dragover", { ev: dynamic -> ev.preventDefault(); Unit })
    drop?.asDynamic()?.addEventListener("drop", { ev: dynamic ->
        ev.preventDefault()
        val raw = ev.dataTransfer?.getData("text/plain") as? String
        val ref = raw?.let { toGenericRef(it) }
        if (ref != null) {
            buildPromise { addDocAndSave(game, hexKey, panel, ref.uuid) }
        }
        Unit
    })

    panel.querySelector(".km-hexed-chips")?.asDynamic()?.addEventListener("click", { ev: dynamic ->
        val target = ev.target
        val remove = if (target != null && target != undefined) target.closest(".km-hexed-remove") else null
        if (remove != null && remove != undefined) {
            ev.preventDefault()
            val chip = remove.closest(".km-hexed-chip")
            if (chip != null && chip != undefined) {
                chip.remove()
                buildPromise { saveUuids(game, hexKey, panel) }
            }
        }
        Unit
    })
}

private fun collectQuestIds(panel: Element): Array<String> {
    val boxes = panel.querySelectorAll("input[data-km-quest]")
    val ids = mutableListOf<String>()
    for (i in 0 until boxes.length) {
        val b = boxes[i] as? HTMLInputElement ?: continue
        if (b.checked) b.getAttribute("data-km-quest")?.let { ids.add(it) }
    }
    return ids.toTypedArray()
}

private fun collectUuids(panel: Element): Array<String> {
    val chips = panel.querySelectorAll(".km-hexed-chips .km-hexed-chip")
    val uuids = mutableListOf<String>()
    for (i in 0 until chips.length) {
        (chips[i] as? Element)?.getAttribute("data-uuid")?.let { uuids.add(it) }
    }
    return uuids.toTypedArray()
}

private suspend fun saveQuests(game: Game, hexKey: String, panel: Element) {
    val ids = collectQuestIds(panel)
    applyToEntry(game, hexKey) {
        it.linkedQuestIds = ids.takeIf { a -> a.isNotEmpty() }
        it.linkedQuestId = null
    }
}

private suspend fun saveThreat(game: Game, hexKey: String, value: String?) {
    applyToEntry(game, hexKey) {
        it.linkedWarThreatId = value?.takeIf { v -> v.isNotBlank() }
    }
}

private suspend fun addDocAndSave(game: Game, hexKey: String, panel: Element, uuid: String) {
    val chips = panel.querySelector(".km-hexed-chips") ?: return
    if (chips.querySelector("[data-uuid=\"$uuid\"]") != null) return // dedup
    val name = resolveName(uuid)
    val link = TextEditor.enrichHTML(buildUuid(uuid, name)).await()
    val span = document.createElement("span")
    span.className = "km-hexed-chip"
    span.setAttribute("data-uuid", uuid)
    span.innerHTML = "$link <a class=\"km-hexed-remove\" data-uuid=\"$uuid\" title=\"${escapeHtml(t("kingdom.hexContent.removeLink"))}\">×</a>"
    chips.appendChild(span)
    saveUuids(game, hexKey, panel)
}

private suspend fun saveUuids(game: Game, hexKey: String, panel: Element) {
    val uuids = collectUuids(panel)
    applyToEntry(game, hexKey) {
        it.linkedUuids = uuids.takeIf { a -> a.isNotEmpty() }
        it.linkedUuid = null
    }
}

/**
 * Mutate the hex's representative content entry, creating a minimal hidden entry for the hex
 * if none exists yet, then persist. Re-fetches the kingdom each call so concurrent edits win.
 */
private suspend fun applyToEntry(game: Game, hexKey: String, block: (RawHexContent) -> Unit) {
    val actor = game.getKingdomActors().firstOrNull() ?: return
    val kingdom = actor.getKingdom() ?: return
    val contents = kingdom.hexContents ?: emptyArray()
    val existing = contents.firstOrNull { it.hexKey == hexKey }
    if (existing != null) {
        block(existing)
        kingdom.hexContents = contents
    } else {
        val created = RawHexContent(
            id = "hexcontent-${js("Date.now()")}",
            hexKey = hexKey,
            type = HexContentType.LANDMARK.value,
            name = hexCoordLabel(hexKey),
            visibility = HexContentVisibility.HIDDEN.value,
            gmNotes = "",
            playerText = "",
        )
        block(created)
        kingdom.hexContents = contents + created
    }
    actor.setKingdom(kingdom)
}
