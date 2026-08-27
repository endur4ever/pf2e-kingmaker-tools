package at.posselt.pfrpg2e.camping

import kotlinx.js.JsPlainObject

@JsPlainObject
external interface RawRumorMutationTable {
    var category: String
    var beats: Array<String>
}

@JsModule("./rumor-mutations.json")
private external val rumorMutationTables: Array<RawRumorMutationTable>

/**
 * Expiry-beat i18n keys by encounter category. ONLY `monster` ships filled -- the beat prose is
 * campaign voice and Gregory's to author (plan section 5.3, explicitly gated); a category with no
 * table degrades to a quiet expiry rather than a wrong-flavoured beat.
 */
fun rumorMutationTablesByCategory(): Map<String, List<String>> =
    rumorMutationTables.associateBy({ it.category }, { it.beats.toList() })
