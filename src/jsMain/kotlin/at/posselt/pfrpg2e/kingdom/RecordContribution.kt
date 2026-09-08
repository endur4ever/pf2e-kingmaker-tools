package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.Contribution
import at.posselt.pfrpg2e.data.kingdom.ContributionKind
import at.posselt.pfrpg2e.data.kingdom.DeedCategory
import at.posselt.pfrpg2e.data.kingdom.PcRenown
import at.posselt.pfrpg2e.data.kingdom.TurnTally
import at.posselt.pfrpg2e.data.kingdom.accrueRenown
import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import at.posselt.pfrpg2e.data.kingdom.revertRenown
import at.posselt.pfrpg2e.kingdom.data.RawPcRenown
import at.posselt.pfrpg2e.kingdom.data.RawRenownDeed
import at.posselt.pfrpg2e.kingdom.data.RawTurnContribution
import at.posselt.pfrpg2e.kingdom.data.appliedFaction
import at.posselt.pfrpg2e.kingdom.data.appliedPopulace
import at.posselt.pfrpg2e.kingdom.data.toModel
import at.posselt.pfrpg2e.kingdom.data.toRaw

/**
 * THE attribution seam: credits one deed to one PC, in the kingdom's in-progress tally and in
 * their cumulative renown.
 *
 * Idempotent PER DEED, not per call. A second call carrying a [deedId] already in
 * `kingdom.currentTurnDeeds` REPLACES that deed: the prior credit is reverted with the deltas
 * that ACTUALLY landed, its tally counter is decremented, and the new result is applied. That is
 * the re-roll path -- a re-rolled check must end up credited once, at its final degree, not twice
 * and not at the degree it first rolled.
 *
 * Never emits offers. Epithet and perk offers are batched at End Turn, so a PC crossing a
 * threshold mid-turn does not interrupt the table with a chat card.
 *
 * The caller owns persistence: this mutates [kingdom] in place and the caller batches its own
 * `setKingdom` with whatever else that flow is writing.
 */
fun recordContribution(
    kingdom: KingdomData,
    deedId: String,
    actorUuid: String,
    actorName: String?,
    kind: ContributionKind,
    leader: Leader,
    category: DeedCategory = DeedCategory.OTHER,
    factionName: String? = null,
) {
    if (actorUuid.isBlank() || deedId.isBlank()) return

    val priorDeed = (kingdom.currentTurnDeeds ?: emptyArray()).firstOrNull { it.deedId == deedId }
    // the prior row's OWN actor, not the incoming one: a re-roll cannot move credit between PCs,
    // and reverting against the wrong ledger would mint renown out of nothing
    val revertUuid = priorDeed?.actorUuid?.takeIf { it.isNotBlank() }

    var renownRows = kingdom.renown ?: emptyArray()
    var tallyRows = kingdom.currentTurnContributions ?: emptyArray()

    if (priorDeed != null && revertUuid != null) {
        val priorContribution = priorDeed.toModel()
        val priorLedger = renownRows.firstOrNull { it.actorUuid == revertUuid }
        if (priorContribution != null && priorLedger != null) {
            priorLedger.toModel()?.let { model ->
                val reverted = revertRenown(
                    current = model,
                    deed = priorContribution,
                    populaceApplied = priorDeed.appliedPopulace,
                    factionApplied = priorDeed.appliedFaction,
                )
                renownRows = renownRows.replacingActor(
                    revertUuid,
                    reverted.toRaw(
                        actorName = priorLedger.actorName,
                        lastOfferedTurn = priorLedger.lastOfferedTurn,
                        dismissedEpithets = priorLedger.dismissedEpithets,
                    ),
                )
            }
            tallyRows = tallyRows.adjustCounter(revertUuid, priorContribution.kind, delta = -1, actorName = null)
        }
        kingdom.currentTurnDeeds = (kingdom.currentTurnDeeds ?: emptyArray())
            .filter { it.deedId != deedId }
            .toTypedArray()
    }

    val existing = renownRows.firstOrNull { it.actorUuid == actorUuid }
    val currentModel = existing?.toModel() ?: PcRenown(actorUuid = actorUuid)
    val result = accrueRenown(
        current = currentModel,
        deed = Contribution(kind = kind, leader = leader, category = category, factionName = factionName),
    )
    kingdom.renown = renownRows.replacingActor(
        actorUuid,
        result.renown.toRaw(
            // an incoming name refreshes a renamed PC, but never erases a stored one with null
            actorName = actorName ?: existing?.actorName,
            lastOfferedTurn = existing?.lastOfferedTurn,
            dismissedEpithets = existing?.dismissedEpithets,
        ),
    )
    kingdom.currentTurnContributions = tallyRows.adjustCounter(actorUuid, kind, delta = 1, actorName = actorName)
    kingdom.currentTurnDeeds = (kingdom.currentTurnDeeds ?: emptyArray()) +
            Contribution(kind = kind, leader = leader, category = category, factionName = factionName)
                .toRaw(
                    deedId = deedId,
                    actorUuid = actorUuid,
                    populaceApplied = result.populaceApplied,
                    factionApplied = result.factionApplied,
                )
}

/** Replaces one actor's ledger row, appending when absent. */
private fun Array<RawPcRenown>.replacingActor(actorUuid: String, row: RawPcRenown): Array<RawPcRenown> =
    if (any { it.actorUuid == actorUuid }) {
        map { if (it.actorUuid == actorUuid) row else it }.toTypedArray()
    } else {
        this + row
    }

/**
 * Moves the one tally counter [kind] owns by [delta], flooring at zero.
 *
 * The buckets are DISJOINT -- exactly one counter moves per deed -- because that is the contract
 * the pure core is written against: `spotlightScore` weights crits and checks SEPARATELY and
 * `spotlightActionCount` sums all five, so a crit that also bumped `checks` would score four
 * points where the weights say three and count as two actions instead of one. TurnTally's own
 * KDoc states this ("Reading them as nested would make a crit worth four points").
 *
 * Floored because a revert against a tally that was never written -- a deed row that outlived its
 * tally across an interrupted End Turn reset -- must not produce a negative count.
 */
private fun Array<RawTurnContribution>.adjustCounter(
    actorUuid: String,
    kind: ContributionKind,
    delta: Int,
    actorName: String?,
): Array<RawTurnContribution> {
    val existing = firstOrNull { it.actorUuid == actorUuid }
    val tally = existing?.toModel() ?: TurnTally(actorUuid = actorUuid, actorName = actorName)
    fun floor(value: Int) = maxOf(value, 0)
    val updated = when (kind) {
        ContributionKind.CHECK_CRIT -> tally.copy(crits = floor(tally.crits + delta))
        ContributionKind.CHECK_SUCCESS, ContributionKind.CHECK_FAILURE ->
            tally.copy(checks = floor(tally.checks + delta))
        ContributionKind.CHECK_CRIT_FAIL -> tally.copy(critFails = floor(tally.critFails + delta))
        ContributionKind.ACTIVITY -> tally.copy(activities = floor(tally.activities + delta))
        ContributionKind.EVENT_RESOLVED, ContributionKind.PETITION_ANSWERED ->
            tally.copy(events = floor(tally.events + delta))
    }
    val row = updated.copy(actorName = actorName ?: tally.actorName).toRaw()
    return if (existing != null) {
        map { if (it.actorUuid == actorUuid) row else it }.toTypedArray()
    } else {
        this + row
    }
}

/** Maps a rolled degree onto the kind the ledger credits. */
fun contributionKindFor(degree: at.posselt.pfrpg2e.data.checks.DegreeOfSuccess): ContributionKind =
    when (degree) {
        at.posselt.pfrpg2e.data.checks.DegreeOfSuccess.CRITICAL_SUCCESS -> ContributionKind.CHECK_CRIT
        at.posselt.pfrpg2e.data.checks.DegreeOfSuccess.SUCCESS -> ContributionKind.CHECK_SUCCESS
        at.posselt.pfrpg2e.data.checks.DegreeOfSuccess.FAILURE -> ContributionKind.CHECK_FAILURE
        at.posselt.pfrpg2e.data.checks.DegreeOfSuccess.CRITICAL_FAILURE -> ContributionKind.CHECK_CRIT_FAIL
    }
