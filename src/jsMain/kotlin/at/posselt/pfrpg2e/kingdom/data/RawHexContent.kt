package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

@JsPlainObject
external interface RawHexContent {
    var id: String
    var hexKey: String
    var type: String          // HexContentType.value
    var name: String
    var visibility: String    // HexContentVisibility.value
    var gmNotes: String
    var playerText: String
    var suppressesEncounters: Boolean?
    /** GM queued an encounter here (war-threat arrival offer); cleared manually once run. */
    var pendingEncounter: Boolean?
    /**
     * Creatures curated for this queued encounter (encounter-stager 2.3). Null means "not
     * curated yet" -- the Stage button opens the dialog empty rather than being hidden, because
     * curating is exactly what the GM came to do.
     */
    var encounterManifest: at.posselt.pfrpg2e.camping.RawEncounterManifest?
    var travelModifier: Int?
    var linkedQuestId: String?       // legacy single-quest link (kept for back-compat)
    var linkedUuid: String?          // legacy single-document link (kept for back-compat)
    var linkedQuestIds: Array<String>?   // referenced kingdom quests
    var linkedUuids: Array<String>?      // referenced Foundry documents (journals/actors/scenes/items)
    var linkedWarThreatId: String?       // referenced war threat
    var icon: String?

    /** Treasure prepped for this hex (loot-manifests SS2.2). Null = no treasure. */
    var lootManifest: Array<RawLootManifestEntry>?
    /** True once awarded -- the double-grant idempotency guard (offerConsumed pattern). */
    var manifestAwarded: Boolean?
    /** Kingdom turn the award fired, for audit. */
    var manifestAwardedTurn: Int?
}

/** Guarded read: a hex from before the stager, or one never curated, reads as an empty manifest. */
fun RawHexContent.encounterManifestOrNull(): at.posselt.pfrpg2e.camping.RawEncounterManifest? =
    encounterManifest?.takeIf { it.creatures?.isNotEmpty() == true }

/**
 * Copy every field the hex-content FORM does not render from [previous] onto [rebuilt].
 *
 * The manager rebuilds a hex from form data on edit, which drops anything the form has no input
 * for. That already cost the loot-award history once (patched inline) and silently cleared
 * `pendingEncounter` -- editing a hex, or merely cycling its visibility on the map, un-queued the
 * encounter and its map marker. One helper instead of a per-field patch, so the NEXT engine-owned
 * field is carried by construction rather than by whoever remembers.
 */
fun carryHexEngineState(rebuilt: RawHexContent, previous: RawHexContent): RawHexContent {
    rebuilt.pendingEncounter = previous.pendingEncounter
    rebuilt.encounterManifest = previous.encounterManifest
    rebuilt.lootManifest = previous.lootManifest
    rebuilt.manifestAwarded = previous.manifestAwarded
    rebuilt.manifestAwardedTurn = previous.manifestAwardedTurn
    return rebuilt
}
