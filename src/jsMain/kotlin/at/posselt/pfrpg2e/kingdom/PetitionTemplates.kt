package at.posselt.pfrpg2e.kingdom

import kotlinx.js.JsPlainObject

/**
 * The petition template catalog (`docs/plans/2026-07-09-plan-petition-inbox.md` section 3).
 *
 * Data files carry MECHANICS AND KEYS ONLY -- never prose. A template's premise and its option
 * labels are i18n values, which is what lets a wording fix reach petitions already sitting in
 * inboxes without touching stored data.
 *
 * The catalog ships EMPTY: the plan's forty starter templates are fully specified for
 * consequences but not for their option ids and labels, which are Gregory's to write (plan
 * section 6, Tone). Everything around them is here, so the templates drop in as pure data.
 */
@JsPlainObject
external interface RawPetitionConsequence {
    /** unrest | rp | standing | quest | rumor -- the closed set of section 4. */
    var kind: String
    var amount: Int?
    /** Names the faction for a standing consequence. */
    var ref: String?
}

@JsPlainObject
external interface RawPetitionOption {
    var id: String
    var consequences: Array<RawPetitionConsequence>?
}

@JsPlainObject
external interface RawPetitionTemplate {
    var id: String
    /** Leader.value. */
    var targetRole: String
    var weight: Int
    var requiresStructure: String?
    var requiresEvent: String?
    var options: Array<RawPetitionOption>?
}

@JsModule("./petitions.json")
private external val rawPetitionTemplates: Array<RawPetitionTemplate>

fun petitionTemplates(): Array<RawPetitionTemplate> = rawPetitionTemplates

/** Templates addressed to one role, in catalog order. */
fun petitionTemplatesForRole(role: String): List<RawPetitionTemplate> =
    rawPetitionTemplates.filter { it.targetRole == role }

/** The template a petition names, or null when the catalog no longer carries it. */
fun petitionTemplateById(id: String): RawPetitionTemplate? =
    rawPetitionTemplates.find { it.id == id }
