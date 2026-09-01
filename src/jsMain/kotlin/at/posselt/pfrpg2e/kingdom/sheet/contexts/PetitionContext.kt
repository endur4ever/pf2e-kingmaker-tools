package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import at.posselt.pfrpg2e.kingdom.RawPetitionTemplate
import at.posselt.pfrpg2e.kingdom.petitionTemplateById
import at.posselt.pfrpg2e.kingdom.petitions.Petition
import at.posselt.pfrpg2e.kingdom.petitions.PetitionStatus
import at.posselt.pfrpg2e.kingdom.petitions.unreadPetitionCount
import at.posselt.pfrpg2e.utils.t
import kotlinx.js.JsPlainObject

@Suppress("unused")
@JsPlainObject
external interface PetitionOptionContext {
    val id: String
    val label: String
    /**
     * The petition this option belongs to, denormalised onto the option.
     *
     * `petitions.hbs` is a registered partial with no parent frame, so inside `{{#each options}}`
     * the enclosing petition is unreachable — `../` is forbidden here and `@root` reaches the whole
     * context, not the current row. Carrying the id on the option is what lets each button name
     * its own petition instead of every button naming the first one.
     */
    val petitionId: String
}

@Suppress("unused")
@JsPlainObject
external interface PetitionRowContext {
    val id: String
    val roleLabel: String
    val petitionerName: String
    val premise: String
    val dueTurn: Int
    /** Turns left; negative is impossible here because expiry runs at End Turn. */
    val turnsLeft: Int
    val isUrgent: Boolean
    val options: Array<PetitionOptionContext>
    val isOpen: Boolean
    val statusLabel: String
    val chosenLabel: String?
}

@Suppress("unused")
@JsPlainObject
external interface PetitionInboxContext {
    val rows: Array<PetitionRowContext>
    val hasRows: Boolean
    val unreadCount: Int
    val hasUnread: Boolean
    /** No owned roles means no inbox at all, rather than an empty box implying nothing is pending. */
    val ownsAnyRole: Boolean
}

private fun localizeStatus(status: PetitionStatus): String = when (status) {
    PetitionStatus.OPEN -> t("kingdom.petitions.status.open")
    PetitionStatus.ANSWERED -> t("kingdom.petitions.status.answered")
    PetitionStatus.EXPIRED -> t("kingdom.petitions.status.expired")
}

/**
 * A petition's options, in catalog order.
 *
 * The label key is COMPOSED from the template and option ids, which `check_i18n_keys.py` cannot
 * see; the catalog check added for exactly this shape covers them instead, asserting every id in
 * `data/petitions/` carries a premise and per-option label in all eight locales.
 */
private fun optionsOf(template: RawPetitionTemplate, petitionId: String): Array<PetitionOptionContext> =
    (template.options ?: emptyArray()).map { option ->
        PetitionOptionContext(
            id = option.id,
            label = t("petitions.${template.id}.${option.id}.label"),
            petitionId = petitionId,
        )
    }.toTypedArray()

/**
 * The inbox for ONE user.
 *
 * Filtered by [ownedRoles] — `getOwnedLeaderRoles` already returns exactly the roles whose actor
 * the current user owns, and a GM owns all eight. A petition whose template has left the catalog
 * is dropped rather than rendered with raw keys where its prose should be: the record survives in
 * kingdom data, so restoring the template restores the row.
 *
 * "Seen" is per USER (§6), so [seenIds] comes from the User flag rather than kingdom state — two
 * GMs correctly carry independent badges.
 */
fun petitionInboxContext(
    petitions: List<Petition>,
    ownedRoles: Set<Leader>,
    seenIds: Set<String>,
    currentTurn: Int,
): PetitionInboxContext {
    val mine = petitions.filter { it.targetRole in ownedRoles }
    val rows = mine
        .sortedWith(compareBy({ it.status != PetitionStatus.OPEN }, { it.dueTurn }))
        .mapNotNull { petition ->
            val template = petitionTemplateById(petition.templateId) ?: return@mapNotNull null
            val turnsLeft = petition.dueTurn - currentTurn
            PetitionRowContext(
                id = petition.id,
                roleLabel = t(petition.targetRole.i18nKey),
                petitionerName = petition.petitionerName,
                premise = t("petitions.${petition.templateId}.premise"),
                dueTurn = petition.dueTurn,
                turnsLeft = turnsLeft,
                isUrgent = petition.status == PetitionStatus.OPEN && turnsLeft <= 1,
                options = optionsOf(template, petition.id),
                isOpen = petition.status == PetitionStatus.OPEN,
                statusLabel = localizeStatus(petition.status),
                chosenLabel = petition.chosenOptionId?.let {
                    t("petitions.${petition.templateId}.$it.label")
                },
            )
        }
        .toTypedArray()
    val unread = unreadPetitionCount(petitions, ownedRoles, seenIds)
    return PetitionInboxContext(
        rows = rows,
        hasRows = rows.isNotEmpty(),
        unreadCount = unread,
        hasUnread = unread > 0,
        ownsAnyRole = ownedRoles.isNotEmpty(),
    )
}
