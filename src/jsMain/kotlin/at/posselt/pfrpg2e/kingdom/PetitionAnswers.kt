package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.camping.Rumor
import at.posselt.pfrpg2e.camping.currentWorldDay
import at.posselt.pfrpg2e.camping.getCampingActors
import at.posselt.pfrpg2e.camping.updateRumors
import at.posselt.pfrpg2e.kingdom.data.toModel
import at.posselt.pfrpg2e.kingdom.data.toRaw
import at.posselt.pfrpg2e.kingdom.dialogs.AddQuest
import at.posselt.pfrpg2e.kingdom.petitions.Petition
import at.posselt.pfrpg2e.kingdom.petitions.PetitionStatus
import at.posselt.pfrpg2e.utils.launch
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.ui
import js.objects.recordOf
import kotlin.random.Random

/**
 * Answering a petition (`docs/plans/2026-07-09-plan-petition-inbox.md` §4/§5).
 *
 * The split the plan insists on: the ROLE'S PLAYER picks, the GM confirms. A player's click posts
 * an offer and writes nothing; only [applyPetitionAnswer] — GM-gated — touches the kingdom. That
 * is why the sheet's `answer-petition` handler needs no ownership check: there is nothing there
 * to protect.
 */

/** The GM-facing consequence summary. Deliberately not shown on the player's buttons: §6 forbids
 *  telegraphing a price the office would not know. */
private fun describeConsequence(consequence: RawPetitionConsequence): String {
    val amount = consequence.amount ?: 0
    return when (consequence.kind) {
        "unrest" -> t("kingdom.petitions.effect.unrest", recordOf("amount" to amount.toString()))
        "rp" -> t("kingdom.petitions.effect.rp", recordOf("amount" to amount.toString()))
        "standing" -> t("kingdom.petitions.effect.standing", recordOf("amount" to amount.toString()))
        "quest" -> t("kingdom.petitions.effect.quest")
        "rumor" -> t("kingdom.petitions.effect.rumor")
        else -> ""
    }
}

private fun findPetition(kingdom: KingdomData, petitionId: String): Petition? =
    kingdom.petitions?.mapNotNull { it.toModel() }?.find { it.id == petitionId }

/**
 * Whisper the GM the choice a role's player made.
 *
 * Clicking twice DOES post two cards -- the click writes nothing, so there is no state to debounce
 * against -- and the GM may see an answer the player has since changed their mind about. What
 * cannot happen is paying twice: [applyPetitionAnswer] acts only on an OPEN petition, so whichever
 * card the GM confirms first closes the audience and every other card for it becomes inert.
 */
suspend fun postPetitionAnswerOffer(
    game: Game,
    actorUuid: String,
    kingdom: KingdomData,
    petitionId: String,
    optionId: String,
) {
    val petition = findPetition(kingdom, petitionId) ?: return
    if (petition.status != PetitionStatus.OPEN) {
        ui.notifications.info(t("kingdom.petitions.alreadyAnswered"))
        return
    }
    val template = petitionTemplateById(petition.templateId) ?: return
    val option = template.options?.find { it.id == optionId } ?: return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) {
        ui.notifications.warn(t("kingdom.petitions.noGm"))
        return
    }
    val consequences = option.consequences ?: emptyArray()
    // a standing consequence carries no faction: the catalog cannot know which realm a table's
    // campaign has, so the GM names it on the card rather than the data guessing wrong
    val needsFaction = consequences.any { it.kind == "standing" && it.ref == null }
    postChatTemplate(
        templatePath = "chatmessages/petition-answer.hbs",
        templateContext = recordOf<String, Any?>(
            "actorUuid" to actorUuid,
            "petitionId" to petition.id,
            "optionId" to optionId,
            "roleLabel" to t(petition.targetRole.i18nKey),
            "petitionerName" to petition.petitionerName,
            "premise" to t("petitions.${petition.templateId}.premise"),
            "optionLabel" to t("petitions.${petition.templateId}.$optionId.label"),
            "effects" to consequences.map { describeConsequence(it) }.filter { it.isNotEmpty() }
                .toTypedArray(),
            "needsFaction" to needsFaction,
            "factions" to (kingdom.groups.map { it.name }.toTypedArray()),
        ),
        whisper = gmUserIds,
    )
    ui.notifications.info(t("kingdom.petitions.sentToGm"))
}

/**
 * Apply one consequence. Returns false when it could not be applied, so the caller can report a
 * partial answer rather than claim a price the kingdom never paid.
 */
private suspend fun applyConsequence(
    game: Game,
    actor: KingdomActor,
    kingdom: KingdomData,
    petition: Petition,
    consequence: RawPetitionConsequence,
    factionName: String?,
): Boolean {
    val amount = consequence.amount ?: 0
    return when (consequence.kind) {
        // both clamp at 0: §4. A petition must never drive a resource negative, which would make
        // the next turn's arithmetic wrong rather than merely harsh
        "unrest" -> {
            kingdom.unrest = (kingdom.unrest + amount).coerceAtLeast(0)
            true
        }

        "rp" -> {
            kingdom.resourcePoints.now = (kingdom.resourcePoints.now + amount).coerceAtLeast(0)
            true
        }

        "standing" -> {
            val name = consequence.ref ?: factionName
            val group = kingdom.groups.find { it.name == name }
            if (group == null) {
                // §4: a missing group TELLS the GM rather than silently doing nothing
                ui.notifications.warn(
                    t("kingdom.petitions.missingFaction", recordOf("name" to (name ?: "")))
                )
                false
            } else {
                group.standing = (group.standing ?: 0) + amount
                true
            }
        }

        "quest" -> {
            // the existing generator, prefilled from the petition -- the GM writes the quest, the
            // petition only supplies who asked and what about.
            //
            // The callback RE-READS the kingdom and persists itself. It fires whenever the GM
            // finishes the dialog, which is long after applyPetitionAnswer's own setKingdom has
            // run: appending to the captured object instead would add the quest to a kingdom
            // nobody saves, and it would vanish at the next read.
            AddQuest(
                prefillTitle = t("petitions.${petition.templateId}.premise"),
                prefillGiver = petition.petitionerName,
                settlements = kingdom.getAllSettlements(game).allSettlements.map { it.id to it.name },
            ) { quest ->
                actor.getKingdom()?.let { fresh ->
                    fresh.quests = (fresh.quests ?: emptyArray()) + quest
                    actor.setKingdom(fresh)
                }
            }.launch()
            true
        }

        "rumor" -> {
            val campingActor = game.getCampingActors().firstOrNull()
            if (campingActor == null) {
                ui.notifications.warn(t("kingdom.petitions.noCampingActor"))
                false
            } else {
                campingActor.updateRumors { existing ->
                    existing + Rumor(
                        text = t("petitions.${petition.templateId}.premise"),
                        id = "rumor-petition-${petition.id}-${Random.nextInt(100000)}",
                        bornDay = currentWorldDay(game),
                        sourceRegion = petition.petitionerName,
                    )
                }
                true
            }
        }

        else -> false
    }
}

/**
 * The GM's confirm: apply §4's consequences and close the petition.
 *
 * Consequences are applied BEFORE the status flips, and the flip happens even on a partial
 * application — a petition whose standing consequence found no faction is still answered, because
 * re-offering it would ask the table to decide the same audience twice. What failed is reported.
 *
 * Only an OPEN petition is answerable, so a stale card left in scrollback applies nothing.
 */
suspend fun applyPetitionAnswer(
    game: Game,
    actor: KingdomActor,
    petitionId: String,
    optionId: String,
    factionName: String?,
): Boolean {
    if (!game.user.isGM) return false
    val kingdom = actor.getKingdom() ?: return false
    val petition = findPetition(kingdom, petitionId) ?: return false
    if (petition.status != PetitionStatus.OPEN) {
        ui.notifications.info(t("kingdom.petitions.alreadyAnswered"))
        return false
    }
    val template = petitionTemplateById(petition.templateId) ?: return false
    val option = template.options?.find { it.id == optionId } ?: return false
    var allApplied = true
    for (consequence in option.consequences ?: emptyArray()) {
        if (!applyConsequence(game, actor, kingdom, petition, consequence, factionName)) allApplied = false
    }
    val currentTurn = kingdom.currentTurn ?: 0
    kingdom.petitions = (kingdom.petitions ?: emptyArray()).map { raw ->
        if (raw.id == petitionId) {
            petition.copy(
                status = PetitionStatus.ANSWERED,
                chosenOptionId = optionId,
                resolvedTurn = currentTurn,
            ).toRaw()
        } else raw
    }.toTypedArray()
    actor.setKingdom(kingdom)
    if (!allApplied) ui.notifications.warn(t("kingdom.petitions.partiallyApplied"))
    return true
}

/**
 * The overdue consequence for a lapsed petition: unrest +1, and only when the GM says so (§5).
 *
 * Guards on EXPIRED, so declining and then clicking an older copy of the card cannot fine the
 * kingdom twice for the same lapse.
 */
suspend fun applyPetitionOverdue(
    game: Game,
    actor: KingdomActor,
    petitionId: String,
): Boolean {
    if (!game.user.isGM) return false
    val kingdom = actor.getKingdom() ?: return false
    val petition = findPetition(kingdom, petitionId) ?: return false
    if (petition.status != PetitionStatus.EXPIRED) return false
    if (petition.chosenOptionId == OVERDUE_APPLIED) return false
    kingdom.unrest = (kingdom.unrest + PETITION_OVERDUE_UNREST).coerceAtLeast(0)
    kingdom.petitions = (kingdom.petitions ?: emptyArray()).map { raw ->
        if (raw.id == petitionId) petition.copy(chosenOptionId = OVERDUE_APPLIED).toRaw() else raw
    }.toTypedArray()
    actor.setKingdom(kingdom)
    return true
}

/** §5's default overdue consequence. */
const val PETITION_OVERDUE_UNREST = 1

/**
 * Marks an expired petition's overdue consequence as spent.
 *
 * It rides in `chosenOptionId`, which is otherwise null for an expired petition, rather than in a
 * new field: adding one to [at.posselt.pfrpg2e.kingdom.data.RawPetition] would need a migration
 * and every rebuild path audited for the wipe that costs.
 */
const val OVERDUE_APPLIED = "__overdue-applied"
