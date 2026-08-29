package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawFactionAgendaArchetype
import at.posselt.pfrpg2e.kingdom.data.RawFactionAgendaMove

/**
 * Static faction-agenda catalogs, one JSON file per move/archetype (plan section 12, verbatim).
 * Directories, not single files: combineJsonFiles bundles data/ SUBDIRECTORIES only.
 */
@JsModule("./faction-agenda-moves.json")
private external val factionAgendaMoves: Array<RawFactionAgendaMove>

@JsModule("./faction-agenda-archetypes.json")
private external val factionAgendaArchetypes: Array<RawFactionAgendaArchetype>

fun factionAgendaMovesById(): Map<String, RawFactionAgendaMove> =
    factionAgendaMoves.associateBy { it.id }

fun factionAgendaArchetypesById(): Map<String, RawFactionAgendaArchetype> =
    factionAgendaArchetypes.associateBy { it.id }
