package at.posselt.pfrpg2e.kingdom.petitions

import at.posselt.pfrpg2e.data.kingdom.leaders.Leader

/**
 * Pure core of the Petition Inbox (`docs/plans/2026-07-09-plan-petition-inbox.md`).
 *
 * The unit is the **kingdom turn number**: petitions are generated and expire at End Turn (§3, §5),
 * so nothing here touches a clock. The per-role chance roll and the weighted template pick stay
 * with the caller — this core decides only who *may* receive a petition and what happens to the
 * ones that exist, which keeps every function deterministic and testable without a Foundry world.
 */

enum class PetitionStatus(val value: String) {
    OPEN("open"), ANSWERED("answered"), EXPIRED("expired");

    companion object {
        fun fromValue(value: String?): PetitionStatus? = entries.find { it.value == value }
    }
}

/** Answered-or-expired petitions kept before the oldest are trimmed; open ones never count (§2). */
const val PETITION_HISTORY_CAP = 100

/** §3: without this cap eight filled roles generate eight petitions a turn and the inbox
 *  becomes the chore it was meant to replace. */
const val MAX_NEW_PETITIONS_PER_TURN = 2

/** §3: a role sits on at most one undecided audience at a time. */
const val MAX_OPEN_PETITIONS_PER_ROLE = 1

/** §3: `dueTurn = createdTurn + 3` — three End Turns to answer before the petition lapses. */
const val PETITION_DUE_AFTER_TURNS = 3

data class Petition(
    val id: String,
    /** Roster id plus the name captured at creation, so a deleted roster entry still renders a
     *  readable petition (§2). */
    val petitionerId: String,
    val petitionerName: String,
    val settlementId: String? = null,
    val targetRole: Leader,
    /** Catalog id; options and text live in the template, so a wording or balance fix reaches
     *  petitions already sitting in inboxes and the persisted record stays small (§2). */
    val templateId: String,
    val createdTurn: Int,
    val dueTurn: Int,
    val status: PetitionStatus = PetitionStatus.OPEN,
    val chosenOptionId: String? = null,
    val resolvedTurn: Int? = null,
)

/** [newlyExpired] holds only petitions expired by this call — the caller posts one overdue offer
 *  per entry, so an already-expired petition reappearing here would re-post that card every turn. */
data class ExpiryOutcome(
    val petitions: List<Petition>,
    val newlyExpired: List<Petition>,
)

/**
 * Expire every OPEN petition whose deadline has passed at End Turn.
 *
 * `dueTurn <= currentTurn`, not `==`: a table can skip the exact turn a petition lapses, and the
 * lapse must still be seen — once. §5 makes expiry a GM **offer**, never an automatic penalty, so
 * only petitions expired by this very call appear in [ExpiryOutcome.newlyExpired]; already-EXPIRED
 * ones keep their original [Petition.resolvedTurn] and ANSWERED ones are decided history, untouched.
 */
fun expirePetitions(petitions: List<Petition>, currentTurn: Int): ExpiryOutcome {
    val next = mutableListOf<Petition>()
    val newlyExpired = mutableListOf<Petition>()
    for (p in petitions) {
        if (p.status == PetitionStatus.OPEN && p.dueTurn <= currentTurn) {
            val expired = p.copy(status = PetitionStatus.EXPIRED, resolvedTurn = currentTurn)
            next.add(expired)
            newlyExpired.add(expired)
        } else {
            next.add(p)
        }
    }
    return ExpiryOutcome(next, newlyExpired)
}

/**
 * Which filled leadership roles may receive a new petition this End Turn.
 *
 * Both caps come from §3: without them eight filled roles generate eight petitions a turn and the
 * inbox becomes the chore it was meant to replace. Only OPEN petitions hold a role's slot —
 * ANSWERED and EXPIRED are history. Candidates come back in [Leader] declaration order, truncated
 * to [maxPerTurn], so the result never depends on a Set's iteration order; the per-role chance
 * roll stays with the caller to keep this core free of randomness.
 */
fun rolesEligibleForNewPetitions(
    petitions: List<Petition>,
    filledRoles: Set<Leader>,
    maxPerTurn: Int = MAX_NEW_PETITIONS_PER_TURN,
    maxOpenPerRole: Int = MAX_OPEN_PETITIONS_PER_ROLE,
): List<Leader> {
    val openByRole = petitions
        .filter { it.status == PetitionStatus.OPEN }
        .groupingBy { it.targetRole }
        .eachCount()
    return Leader.entries
        .filter { it in filledRoles && (openByRole[it] ?: 0) < maxOpenPerRole }
        .take(maxPerTurn)
}

/** One entry of the settlement's population roster, as far as casting needs it. */
data class PetitionerCandidate(
    val id: String,
    val name: String,
    val occupation: String,
)

/**
 * Cast the petitioner from the settlement's population roster.
 *
 * Null on an empty roster: §3 forbids inventing a name, so the petition is not generated rather
 * than signed by nobody. An occupation the template names is preferred — matched ignoring case
 * and padding, because occupations are typed by a GM — otherwise the first roster entry, which
 * keeps the choice deterministic for the same roster.
 */
fun castPetitioner(
    roster: List<PetitionerCandidate>,
    preferredOccupation: String?,
): PetitionerCandidate? {
    if (roster.isEmpty()) return null
    val wanted = preferredOccupation?.trim()
    if (!wanted.isNullOrEmpty()) {
        roster.find { it.occupation.trim().equals(wanted, ignoreCase = true) }?.let { return it }
    }
    return roster.first()
}

/**
 * Append a petition, trimming the oldest **answered-or-expired** entries once they exceed [cap].
 *
 * OPEN petitions are never pruned, even beyond the cap: trimming an unanswered petition silently
 * robs a player of a decision (§2). The cap counts closed petitions only, so however long answers
 * take, history can never crowd out a decision still waiting to be made.
 */
fun appendPetition(
    existing: List<Petition>,
    petition: Petition,
    cap: Int = PETITION_HISTORY_CAP,
): List<Petition> {
    val all = existing + petition
    val closed = all.filter { it.status != PetitionStatus.OPEN }
    if (closed.size <= cap) return all
    val drop = closed.take(closed.size - cap).toSet()
    return all.filterNot { it in drop }
}

/**
 * The badge count for one user's inbox.
 *
 * §6: "seen" is per *user*, not per world — it lives on the User document — so this takes the seen
 * ids as input instead of reading any kingdom state, and two GMs correctly carry independent
 * badges. Only OPEN petitions addressed to a role the user owns count: answered and expired ones
 * demand nothing, and a user owning no roles gets no badge however full the inbox is.
 */
fun unreadPetitionCount(
    petitions: List<Petition>,
    ownedRoles: Set<Leader>,
    seenIds: Set<String>,
): Int = petitions.count {
    it.status == PetitionStatus.OPEN && it.targetRole in ownedRoles && it.id !in seenIds
}
