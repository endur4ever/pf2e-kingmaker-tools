package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.actor.hasAttribute
import at.posselt.pfrpg2e.actor.rollCheck
import at.posselt.pfrpg2e.camping.ExpeditionResolverEngine
import at.posselt.pfrpg2e.companion.companionDiscoveryThresholds
import at.posselt.pfrpg2e.data.actor.Skill
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.data.checks.determineDegreeOfSuccess
import at.posselt.pfrpg2e.data.checks.RollMode
import at.posselt.pfrpg2e.fromOrdinal
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import at.posselt.pfrpg2e.companion.applyCompanionXp
import at.posselt.pfrpg2e.companion.clampInfluence
import at.posselt.pfrpg2e.settings.Pfrpg2eKingdomCampingWeatherSettings
import at.posselt.pfrpg2e.utils.escapeHtml
import at.posselt.pfrpg2e.utils.fromUuidOfTypes
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.dice.Roll
import com.foundryvtt.pf2e.actor.PF2ECharacter
import com.foundryvtt.pf2e.actor.PF2ENpc
import com.foundryvtt.pf2e.actor.PF2EParty
import js.objects.recordOf
import kotlinx.coroutines.await

/**
 * Impure wrapper that rolls the resolution check at the edge of an expedition
 * completion, accrues the result onto the expedition record (XP, influence,
 * loot, injuries, degree), sets status to `awaitingResolution`,
 * and persists via [setKingdom].
 *
 * This function does NOT mutate the companion's level / XP / conditions —
 * that happens only when the GM clicks the offer button (next task).
 *
 * Resolution flow:
 * 1. Resolve the participant's statistic (linked actor → [rollCheck] on a
 *    relevant skill; unlinked → flat d20 + level as proficiency proxy).
 * 2. Apply a +0..+2 influence circumstance bonus by discovery band:
 *    unknown → +0, introduced/established → +1, trusted/bonded → +2.
 * 3. Convert the roll result to [DegreeOfSuccess] via [fromOrdinal].
 * 4. Hand (baseXp, baseInfluence, tier, degree) to [ExpeditionResolverEngine.resolve].
 * 5. Write accrued* + outcomeDegree onto the record.
 * 6. Set status, persist, and post a minimal degree-of-success chat line.
 */
suspend fun offerExpeditionResolution(
    game: Game,
    actor: PF2EParty,
    companion: RawCharacter,
    expedition: RawCompanionExpedition,
) {
    val dc = expedition.dc
    val bandBonus = influenceBandBonus(companion.discoveryStatus)
    val linkedActor = companion.actorUuid?.let {
        fromUuidOfTypes<PF2ECharacter>(it)
    }

    val degree: DegreeOfSuccess = if (linkedActor != null) {
        // Linked: roll the participant's best exploration skill check.
        // Try Nature → Survival → Athletics; if none resolve, fall back
        // to a flat d20 + level modifier.
        val skill = listOf(Skill.NATURE, Skill.SURVIVAL, Skill.ATHLETICS)
            .firstOrNull { linkedActor.hasAttribute(it) }
        val promise = skill?.let { linkedActor.rollCheck(it, dc + bandBonus, rollMode = RollMode.GMROLL) }
        if (promise != null) {
            val result = promise.await()
            result?.degreeOfSuccess ?: DegreeOfSuccess.FAILURE
        } else {
            // No matching skill resolved — flat d20 + level.
            d20Resolve(
                level = linkedActor.system.details.level.value,
                dc = dc,
                modifier = bandBonus,
            )
        }
    } else {
        // Unlinked: flat d20 + level as proficiency proxy.
        d20Resolve(
            level = companion.level,
            dc = dc,
            modifier = bandBonus,
        )
    }

    val baseXp = 80  // mid-point base; tier multiplier handled in engine
    val baseInfluence = 1  // engine gates influence gain on degree

    val result = ExpeditionResolverEngine.resolve(
        baseXp = baseXp,
        baseInfluence = baseInfluence,
        tier = expedition.tier,
        degree = degree,
    )

    // When companion leveling is disabled, zero out XP so the reward offer shows 0
    // and clicking "Apply Reward" gives no XP. Expeditions still resolve for flavor.
    val levelingEnabled = Pfrpg2eKingdomCampingWeatherSettings.getEnableCompanionLeveling()
    expedition.accruedXp = if (levelingEnabled) result.xpAwarded else 0
    expedition.accruedInfluenceDelta = result.influenceDelta
    expedition.accruedInjuries = result.injuryConditions
    expedition.lootTier = result.lootTier
    expedition.outcomeDegree = degree.value
    var gmNotes = result.gmNotes
    if (expedition.activityId == "personal-quest" && degree == DegreeOfSuccess.CRITICAL_FAILURE) {
        val complicationText = t("kingdom.companion.quest.complication")
        gmNotes = if (gmNotes.isNotBlank()) {
            "$gmNotes\n\n$complicationText"
        } else {
            complicationText
        }
    }
    expedition.gmNotes = gmNotes
    expedition.status = "awaitingResolution"

    // Persist the accrued result.
    val kingdom = actor.getKingdom() ?: return
    actor.setKingdom(kingdom)

    // Post the full expedition-result offer card via postChatTemplate.
    // Renders degree styling, accrued results, and GM-only offer buttons.
    val companionName = escapeHtml(
        companion.actorUuid?.let { uuid ->
            fromUuidOfTypes<PF2ECharacter>(uuid)?.name
                ?: fromUuidOfTypes<PF2ENpc>(uuid)?.name
        } ?: companion.name
    )

    val isCriticalSuccess = degree == DegreeOfSuccess.CRITICAL_SUCCESS
    val isSuccess = degree == DegreeOfSuccess.SUCCESS || isCriticalSuccess
    val isCriticalFailure = degree == DegreeOfSuccess.CRITICAL_FAILURE
    val isFailure = degree == DegreeOfSuccess.FAILURE || isCriticalFailure

    // Determine offer flags based on accrued results
    val hasInjuries = result.injuryConditions.isNotEmpty()
    val hasFactionStanding = expedition.factionStandingDelta != 0

    val offerReward = !expedition.rewardApplied && expedition.status == "awaitingResolution"
    // Level-up offer: only when setting enabled AND companion has enough XP to level
    val enableLeveling = Pfrpg2eKingdomCampingWeatherSettings.getEnableCompanionLeveling()
    val projectedXp = companion.xp + result.xpAwarded
    val offerLevelUp = enableLeveling && companion.role != "npc" && projectedXp >= 1000 && companion.level < 20
    val targetLevel = if (offerLevelUp) (companion.level + 1).coerceAtMost(20) else companion.level
    val offerInjury = hasInjuries
    val offerFactionStanding = hasFactionStanding
    val offerWarThreat = hasFactionStanding && expedition.factionStandingDelta < 0
    val offerDiplomacyQuest = hasFactionStanding && expedition.factionStandingDelta > 0

    postChatTemplate(
        templatePath = "chatmessages/expedition-result.hbs",
        templateContext = recordOf(
            "title" to expedition.title,
            "companionName" to companionName,
            "isCriticalSuccess" to isCriticalSuccess,
            "isSuccess" to isSuccess,
            "isFailure" to isFailure,
            "isCriticalFailure" to isCriticalFailure,
            "accruedXp" to expedition.accruedXp,
            "accruedInfluenceDelta" to result.influenceDelta,
            "lootTier" to result.lootTier,
            "gmNotes" to result.gmNotes,
            "rewardApplied" to expedition.rewardApplied,
            "isGM" to game.user.isGM,
            "expeditionId" to expedition.id,
            "actorUuid" to actor.uuid,
            "companionId" to companion.actorUuid,
            "companionActorUuid" to companion.actorUuid,
            "targetLevel" to targetLevel,
            "offerInjury" to offerInjury,
            "offerFactionStanding" to offerFactionStanding,
            "offerWarThreat" to offerWarThreat,
            "offerDiplomacyQuest" to offerDiplomacyQuest,
            "factionName" to "",
            "offerReward" to offerReward,
            "offerLevelUp" to offerLevelUp,
        ),
    )
}

/**
 * Roll a d20 + level + modifier against [dc] and return the [DegreeOfSuccess].
 * Uses the shared [d20Check] utility for the roll, then recomputes degree
 * with the raw die value for correctness.
 */
private suspend fun d20Resolve(
    level: Int,
    dc: Int,
    modifier: Int,
): DegreeOfSuccess {
    val total = Roll("1d20").evaluate().await().total + level + modifier
    val dieValue = total - level - modifier
    val degree = determineDegreeOfSuccess(dc, total, dieValue)
    return fromOrdinal<DegreeOfSuccess>(degree.ordinal)!!
}

/**
 * Map a companion's discovery status to a +0..+2 circumstance bonus:
 *   unknown         → +0
   introduced      → +1
 *   established     → +1
 *   trusted         → +2
 *   bonded          → +2
 */
internal fun influenceBandBonus(discoveryStatus: String): Int {
    val ip = companionDiscoveryThresholds[discoveryStatus] ?: 0
    return when {
        ip >= 6 -> 2   // trusted / bonded
        ip >= 2 -> 1   // introduced / established
        else -> 0      // unknown
    }
}
