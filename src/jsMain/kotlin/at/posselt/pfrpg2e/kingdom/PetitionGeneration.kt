package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import at.posselt.pfrpg2e.kingdom.data.RawPetition
import at.posselt.pfrpg2e.kingdom.data.toModel
import at.posselt.pfrpg2e.kingdom.data.toRaw
import at.posselt.pfrpg2e.kingdom.petitions.MAX_NEW_PETITIONS_PER_TURN
import at.posselt.pfrpg2e.kingdom.petitions.PETITION_DUE_AFTER_TURNS
import at.posselt.pfrpg2e.kingdom.petitions.PETITION_ROLE_CHANCE_PERCENT
import at.posselt.pfrpg2e.kingdom.petitions.Petition
import at.posselt.pfrpg2e.kingdom.petitions.PetitionTemplateChoice
import at.posselt.pfrpg2e.kingdom.petitions.PetitionerCandidate
import at.posselt.pfrpg2e.kingdom.petitions.appendPetition
import at.posselt.pfrpg2e.kingdom.petitions.castPetitioner
import at.posselt.pfrpg2e.kingdom.petitions.expirePetitions
import at.posselt.pfrpg2e.kingdom.petitions.rolesEligibleForNewPetitions
import at.posselt.pfrpg2e.kingdom.petitions.rotatePetitionCandidates
import at.posselt.pfrpg2e.kingdom.petitions.weightedPickPetition
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import js.objects.recordOf
import kotlin.random.Random

/**
 * End Turn generation and expiry for the Petition Inbox
 * (`docs/plans/2026-07-09-plan-petition-inbox.md` phase 3).
 *
 * The pure core decides who MAY receive a petition and what happens to the ones that exist; this
 * adapter supplies the two draws it deliberately does not own — the per-role chance and the
 * weighted template pick — and the world state the gates read.
 */

/** One settlement's residents, as casting sees them, paired with the settlement they live in. */
private data class RosterSource(
    val settlementId: String,
    val candidates: List<PetitionerCandidate>,
)

private fun rosterSources(kingdom: KingdomData): List<RosterSource> =
    kingdom.settlements.mapNotNull { settlement ->
        val npcs = settlement.populationRoster?.npcs?.map {
            PetitionerCandidate(id = it.id, name = it.name, occupation = it.occupation)
        }.orEmpty()
        if (npcs.isEmpty()) null else RosterSource(settlement.sceneId, npcs)
    }

/** Roles with somebody actually appointed; a vacant office receives no audiences. */
private fun filledLeaderRoles(kingdom: KingdomData): Set<Leader> {
    val leaders = kingdom.leaders
    return buildSet {
        if (leaders.ruler.uuid != null) add(Leader.RULER)
        if (leaders.counselor.uuid != null) add(Leader.COUNSELOR)
        if (leaders.emissary.uuid != null) add(Leader.EMISSARY)
        if (leaders.general.uuid != null) add(Leader.GENERAL)
        if (leaders.magister.uuid != null) add(Leader.MAGISTER)
        if (leaders.treasurer.uuid != null) add(Leader.TREASURER)
        if (leaders.viceroy.uuid != null) add(Leader.VICEROY)
        if (leaders.warden.uuid != null) add(Leader.WARDEN)
    }
}

/**
 * Templates this kingdom currently qualifies for.
 *
 * A gate naming something the kingdom does not have removes that TEMPLATE, never the role's whole
 * draw — the role simply picks from what remains, so one gated template cannot silence an office.
 */
private fun eligibleTemplatesFor(
    role: Leader,
    structureNames: Set<String>,
    ongoingEventNames: Set<String>,
): List<RawPetitionTemplate> =
    petitionTemplatesForRole(role.value).filter { template ->
        val structureOk = template.requiresStructure?.let { it in structureNames } ?: true
        val eventOk = template.requiresEvent?.let { it in ongoingEventNames } ?: true
        structureOk && eventOk
    }

/**
 * Generate this turn's petitions, mutating [KingdomData.petitions] in place.
 *
 * Returns only the petitions created by this call, so the caller can report them without
 * re-deriving which rows are new. The rolls are parameters rather than direct [Random] calls so
 * the cadence, the caps and the fairness rotation are all testable without a Foundry world.
 *
 * Order matters: candidates come back from the core in [Leader] declaration order and are ROTATED
 * by turn before the cap applies. Truncating the raw order instead would hand every petition to
 * RULER and COUNSELOR and leave WARDEN, last in the enum, permanently empty.
 */
fun generatePetitionsForTurn(
    kingdom: KingdomData,
    currentTurn: Int,
    structureNames: Set<String>,
    ongoingEventNames: Set<String> = emptySet(),
    chanceRoll: () -> Int = { Random.nextInt(1, 101) },
    pickRoll: (Int) -> Int = { bound -> Random.nextInt(0, bound) },
): List<Petition> {
    val rosters = rosterSources(kingdom)
    // §3 forbids inventing a name: with nobody on any roster there is no one to sign a petition
    if (rosters.isEmpty()) return emptyList()

    var petitions = kingdom.petitions?.mapNotNull { it.toModel() } ?: emptyList()
    val eligible = rolesEligibleForNewPetitions(
        petitions = petitions,
        filledRoles = filledLeaderRoles(kingdom),
        maxPerTurn = Int.MAX_VALUE,
    )
    val created = mutableListOf<Petition>()
    for (role in rotatePetitionCandidates(eligible, currentTurn)) {
        if (created.size >= MAX_NEW_PETITIONS_PER_TURN) break
        if (chanceRoll() > PETITION_ROLE_CHANCE_PERCENT) continue
        val templates = eligibleTemplatesFor(role, structureNames, ongoingEventNames)
        if (templates.isEmpty()) continue
        val choices = templates.map { PetitionTemplateChoice(it.id, it.weight) }
        val total = choices.sumOf { maxOf(it.weight, 0) }
        if (total <= 0) continue
        val picked = weightedPickPetition(choices, pickRoll(total)) ?: continue
        val source = rosters.first()
        val petitioner = castPetitioner(source.candidates, preferredOccupation = null) ?: continue
        val petition = Petition(
            id = "petition-$currentTurn-${role.value}-${picked.id}",
            petitionerId = petitioner.id,
            petitionerName = petitioner.name,
            settlementId = source.settlementId,
            targetRole = role,
            templateId = picked.id,
            createdTurn = currentTurn,
            dueTurn = currentTurn + PETITION_DUE_AFTER_TURNS,
        )
        petitions = appendPetition(petitions, petition)
        created.add(petition)
    }
    if (created.isNotEmpty()) {
        kingdom.petitions = petitions.map { it.toRaw() }.toTypedArray()
    }
    return created
}

/**
 * Lapse overdue petitions, mutating [KingdomData.petitions], and return the ones lapsed HERE.
 *
 * Separate from generation and run first, so a role whose petition lapses this turn is free to
 * receive the next one immediately rather than sitting a turn out behind a dead audience.
 */
fun expirePetitionsForTurn(kingdom: KingdomData, currentTurn: Int): List<Petition> {
    val petitions = kingdom.petitions?.mapNotNull { it.toModel() } ?: return emptyList()
    val outcome = expirePetitions(petitions, currentTurn)
    if (outcome.newlyExpired.isEmpty()) return emptyList()
    kingdom.petitions = outcome.petitions.map { it.toRaw() }.toTypedArray()
    return outcome.newlyExpired
}

/**
 * One whispered card per petition that lapsed this turn.
 *
 * §5 makes the overdue consequence an OFFER, never an automatic penalty: a table that spent the
 * session elsewhere should not be fined without the GM saying so. Only petitions expired by this
 * turn's call are here, so declining one cannot make it re-offer next turn.
 */
suspend fun postPetitionExpiryOffers(
    game: Game,
    actorUuid: String,
    expired: List<Petition>,
) {
    if (expired.isEmpty()) return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    // an EMPTY whisper array posts PUBLICLY rather than to nobody
    if (gmUserIds.isEmpty()) return
    for (petition in expired) {
        postChatTemplate(
            templatePath = "chatmessages/petition-expired.hbs",
            templateContext = recordOf<String, Any?>(
                "actorUuid" to actorUuid,
                "petitionId" to petition.id,
                "petitionerName" to petition.petitionerName,
                "roleLabel" to t(petition.targetRole.i18nKey),
                "premise" to t("petitions.${petition.templateId}.premise"),
            ),
            whisper = gmUserIds,
        )
    }
}

/**
 * One whispered card telling the GM which offices received an audience this turn.
 *
 * The players' signal is the inbox badge on their own sheet (§6), so this is deliberately a GM
 * summary rather than a card per petition: it answers "what landed this turn" without becoming
 * the chat spam the caps exist to prevent. It applies nothing and carries no buttons.
 */
suspend fun postNewPetitionNotice(
    game: Game,
    actorUuid: String,
    created: List<Petition>,
) {
    if (created.isEmpty()) return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return
    postChatTemplate(
        templatePath = "chatmessages/petition-new.hbs",
        templateContext = recordOf<String, Any?>(
            "actorUuid" to actorUuid,
            "petitions" to created.map { petition ->
                recordOf<String, Any?>(
                    "roleLabel" to t(petition.targetRole.i18nKey),
                    "petitionerName" to petition.petitionerName,
                    "premise" to t("petitions.${petition.templateId}.premise"),
                    "dueTurn" to petition.dueTurn,
                )
            }.toTypedArray(),
        ),
        whisper = gmUserIds,
    )
}
