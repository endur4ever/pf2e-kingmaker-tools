package at.posselt.pfrpg2e.kingdom.data

import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import at.posselt.pfrpg2e.kingdom.petitions.Petition
import at.posselt.pfrpg2e.kingdom.petitions.PetitionStatus
import kotlinx.js.JsPlainObject

/**
 * One NPC audience awaiting a leader's answer
 * (`docs/plans/2026-07-09-plan-petition-inbox.md` section 2).
 *
 * OPTIONS ARE NOT STORED HERE. They live in the template catalog keyed by [templateId], so a
 * wording or balance fix reaches petitions already sitting in inboxes; the petition keeps only
 * which option was chosen. [petitionerName] is captured at creation so a petition still reads
 * after the GM deletes that roster entry.
 */
@JsPlainObject
external interface RawPetition {
    var id: String
    var petitionerId: String
    var petitionerName: String
    var settlementId: String?
    /** Leader.value; an unrecognised role DROPS this petition rather than emptying every inbox. */
    var targetRole: String
    /** Catalog id; its text is an i18n key, never stored prose. */
    var templateId: String
    var createdTurn: Int
    var dueTurn: Int
    /** PetitionStatus.value: open | answered | expired. */
    var status: String
    /** Null while open, and for a petition that expired unanswered. */
    var chosenOptionId: String?
    var resolvedTurn: Int?
}

/** Null for a role or status this build does not know: one bad row is skipped, never thrown on. */
fun RawPetition.toModel(): Petition? {
    val role = Leader.fromString(targetRole) ?: return null
    val state = PetitionStatus.fromValue(status) ?: return null
    return Petition(
        id = id,
        petitionerId = petitionerId,
        petitionerName = petitionerName,
        settlementId = settlementId,
        targetRole = role,
        templateId = templateId,
        createdTurn = createdTurn,
        dueTurn = dueTurn,
        status = state,
        chosenOptionId = chosenOptionId,
        resolvedTurn = resolvedTurn,
    )
}

fun Petition.toRaw(): RawPetition = RawPetition(
    id = id,
    petitionerId = petitionerId,
    petitionerName = petitionerName,
    settlementId = settlementId,
    targetRole = targetRole.value,
    templateId = templateId,
    createdTurn = createdTurn,
    dueTurn = dueTurn,
    status = status.value,
    chosenOptionId = chosenOptionId,
    resolvedTurn = resolvedTurn,
)
