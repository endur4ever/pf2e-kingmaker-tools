package at.posselt.pfrpg2e.data.hex

import at.posselt.pfrpg2e.data.hex.HexContentType
import at.posselt.pfrpg2e.data.hex.HexContentVisibility

/**
 * Pure data class for hex content, used by commonMain pure helpers.
 * The jsMain persisted form is [at.posselt.pfrpg2e.kingdom.data.RawHexContent].
 */
data class HexContent(
    val id: String,
    val hexKey: String,
    val type: HexContentType,
    val name: String,
    val visibility: HexContentVisibility = HexContentVisibility.HIDDEN,
    val gmNotes: String = "",
    val playerText: String = "",
    val suppressesEncounters: Boolean? = null,
    val travelModifier: Int? = null,
    val linkedQuestId: String? = null,
    val linkedUuid: String? = null,
    /** War threat this content marks on the map (arrival offers can queue an encounter here). */
    val linkedWarThreatId: String? = null,
    val icon: String? = null,
)
