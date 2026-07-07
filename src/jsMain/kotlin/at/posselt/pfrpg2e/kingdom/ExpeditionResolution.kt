package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.actor.hasAttribute
import at.posselt.pfrpg2e.actor.rollCheck
import at.posselt.pfrpg2e.camping.ExpeditionResolverEngine
import at.posselt.pfrpg2e.expedition.getExpeditionActivities
import at.posselt.pfrpg2e.expedition.getOutcome
import at.posselt.pfrpg2e.companion.companionDiscoveryThresholds
import at.posselt.pfrpg2e.data.actor.Skill
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.data.checks.determineDegreeOfSuccess
import at.posselt.pfrpg2e.data.checks.RollMode
import at.posselt.pfrpg2e.data.kingdom.applyStandingDelta
import at.posselt.pfrpg2e.data.kingdom.shouldOfferDiplomacyQuest
import at.posselt.pfrpg2e.data.kingdom.shouldOfferWarThreat
import at.posselt.pfrpg2e.fromOrdinal
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import at.posselt.pfrpg2e.kingdom.data.RawExpeditionChronicleEntry
import at.posselt.pfrpg2e.kingdom.data.createRawExpeditionChronicleEntry
import at.posselt.pfrpg2e.kingdom.data.RawFactionStandingEntry
import at.posselt.pfrpg2e.companion.accruedExpeditionXp
import at.posselt.pfrpg2e.companion.applyExpeditionParticipantReward
import at.posselt.pfrpg2e.companion.applyPersonalQuestReward
import at.posselt.pfrpg2e.companion.canApplyExpeditionReward
import at.posselt.pfrpg2e.companion.expeditionActivityReward
import at.posselt.pfrpg2e.companion.expeditionActivitySkills
import at.posselt.pfrpg2e.companion.lootTierToResourcePoints
import at.posselt.pfrpg2e.companion.personalQuestCompletionSnapshot
import at.posselt.pfrpg2e.companion.selectRewardQuest
import at.posselt.pfrpg2e.companion.shouldOfferLevelUp
import at.posselt.pfrpg2e.kingdom.sheet.contexts.pruneResolvedExpeditions
import at.posselt.pfrpg2e.kingdom.sheet.contexts.pruneExpeditionChronicle
import at.posselt.pfrpg2e.settings.Pfrpg2eKingdomCampingWeatherSettings
import at.posselt.pfrpg2e.utils.fromUuidOfTypes
import at.posselt.pfrpg2e.utils.escapeHtml
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
import kotlin.math.roundToInt

/**
 * Impure wrapper that rolls the resolution check at the edge of an expedition
 * completion, accrues the result onto the expedition record (XP, influence,
 * loot, injuries, degree), sets status to `awaitingResolution`,
 * and persists via [setKingdom].
 *
 * Multi-companion expeditions: the LEAD companion (first of `companionIds`) makes the
 * check on the party's behalf; every participant then shares the accrued reward when
 * the GM applies it (see [applyExpeditionRewardToKingdom]'s participant loop).
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
 * 4. Hand (baseInfluence, tier, degree) to [ExpeditionResolverEngine.resolve].
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

    // Roll the activity's OWN declared skills (diplomacy -> Diplomacy, hunt -> Survival, ...) in
    // catalog order, falling back to the generic exploration trio when the activity declares no
    // usable skill or the actor lacks them all.
    val activity = getExpeditionActivities().find { it.id == expedition.activityId }
    val activitySkills = activity?.skills?.map { it.name }?.let { expeditionActivitySkills(it) } ?: emptyList()
    val skillCandidates = (activitySkills + listOf(Skill.NATURE, Skill.SURVIVAL, Skill.ATHLETICS)).distinct()

    val degree: DegreeOfSuccess = if (linkedActor != null) {
        // Linked: roll the participant's best matching skill check; if none resolve, fall back
        // to a flat d20 + level modifier.
        val skill = skillCandidates.firstOrNull { linkedActor.hasAttribute(it) }
        // bandBonus is a circumstance BONUS on the roll, modelled as a lower effective DC
        // (consistent with the unlinked d20Resolve(modifier = bandBonus) path).
        val promise = skill?.let { linkedActor.rollCheck(it, dc - bandBonus, rollMode = RollMode.GMROLL) }
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

    val baseInfluence = 1  // engine gates influence gain on degree

    val result = ExpeditionResolverEngine.resolve(
        baseInfluence = baseInfluence,
        tier = expedition.tier,
        degree = degree,
    )

    // Activity-specific reward layer (hunt->food, craft->lumber/ore, train->xp mult, rest->heal,
    // scout->intel, treasure-hunt->loot swing). Pure; accrued here, spent at reward apply.
    val activityReward = expeditionActivityReward(expedition.activityId, expedition.tier, degree)

    // When companion leveling is disabled, zero out XP so the reward offer shows 0
    // and clicking "Apply Reward" gives no XP. Expeditions still resolve for flavor.
    val levelingEnabled = Pfrpg2eKingdomCampingWeatherSettings.getEnableCompanionLeveling()
    val multipliedXp = (result.xpAwarded * activityReward.xpMultiplier).roundToInt()
    expedition.accruedXp = accruedExpeditionXp(multipliedXp, levelingEnabled)
    expedition.accruedInfluenceDelta = result.influenceDelta
    expedition.accruedInjuries = result.injuryConditions
    expedition.lootTier = activityReward.lootTierOverride ?: result.lootTier
    expedition.accruedFood = activityReward.commodities["food"]
    expedition.accruedLumber = activityReward.commodities["lumber"]
    expedition.accruedOre = activityReward.commodities["ore"]
    expedition.accruedInjuryDaysReduction = activityReward.injuryDaysReduction.takeIf { it > 0 }
    expedition.accruedIntelGmNote = activityReward.intelNoteKey
    expedition.accruedBonusLootRp = activityReward.bonusLootRp.takeIf { it > 0 }
    expedition.outcomeDegree = degree.value
    // Diplomacy expeditions move their target faction's standing on resolution; every other
    // activity leaves it at 0. Application happens when the GM applies the reward.
    val isDiplomacy = expedition.activityId == "diplomacy" && expedition.targetFactionName != null
    expedition.factionStandingDelta = if (isDiplomacy) {
        ExpeditionResolverEngine.diplomacyStandingDelta(expedition.tier, degree)
    } else {
        0
    }
    // Prefer the activity catalog's per-degree outcome text (already localized) over the engine
    // placeholder; fall back to the engine note when the activity has none.
    var gmNotes = activity?.getOutcome(degree)?.message?.takeIf { it.isNotBlank() } ?: result.gmNotes
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

    // Get GM user IDs for whispering
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()

    // Homecoming chat beat
    val companions = kingdom.companions ?: emptyArray()
    val nameByKey = companions.associateBy({ it.actorUuid ?: it.name }, { it.name })
    val names = expedition.companionIds.mapNotNull { nameByKey[it] }.joinToString(", ").ifBlank { companion.name }

    // Raw values: postChatMessage escapes the final message exactly once (no pre-escaping,
    // which double-escaped '&' in companion names).
    val homecomingMessage = t(
        "kingdom.expeditions.homecoming",
        recordOf("names" to names, "title" to expedition.title)
    )
    if (expedition.visibleToPlayers) {
        postChatMessage(homecomingMessage)
    } else if (gmUserIds.isNotEmpty()) {
        postChatMessage(homecomingMessage, whisper = gmUserIds)
    }

    // Post the full expedition-result offer card WHISPERED to GMs only.
    // Renders degree styling, accrued results, GM notes, and GM-only offer buttons.
    val companionName = (
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
    // The war-threat / diplomacy-quest narrative follow-ups fire only when the pending standing
    // change actually crosses an attitude band, previewed against the target faction's current
    // standing (the delta is applied later, when the GM applies the reward).
    val targetGroup = expedition.targetFactionName?.let { fn -> kingdom.groups.find { it.name == fn } }
    val hasFactionStanding = expedition.factionStandingDelta != 0 && targetGroup != null
    val offerWarThreat: Boolean
    val offerDiplomacyQuest: Boolean
    if (hasFactionStanding && targetGroup != null) {
        val before = targetGroup.standing
        val after = applyStandingDelta(before, expedition.factionStandingDelta)
        offerWarThreat = shouldOfferWarThreat(before, after)
        offerDiplomacyQuest = shouldOfferDiplomacyQuest(before, after)
    } else {
        offerWarThreat = false
        offerDiplomacyQuest = false
    }

    val offerReward = !expedition.rewardApplied && expedition.status == "awaitingResolution"
    // Level-up offer: only when setting enabled AND companion has enough XP to level
    val enableLeveling = Pfrpg2eKingdomCampingWeatherSettings.getEnableCompanionLeveling()
    val offerLevelUp = shouldOfferLevelUp(
        levelingEnabled = enableLeveling,
        isNpc = companion.role == "npc",
        currentLevel = companion.level,
        currentXp = companion.xp,
        xpAwarded = result.xpAwarded,
    )
    val targetLevel = if (offerLevelUp) (companion.level + 1).coerceAtMost(20) else companion.level
    val offerInjury = hasInjuries
    val offerFactionStanding = hasFactionStanding

    // Build the GM-only offer card context (no isGM flag needed since it's whispered)
    val offerContext = recordOf(
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
        "expeditionId" to expedition.id,
        "actorUuid" to actor.uuid,
        "companionId" to companion.actorUuid,
        "companionActorUuid" to companion.actorUuid,
        "targetLevel" to targetLevel,
        "offerInjury" to offerInjury,
        "offerFactionStanding" to offerFactionStanding,
        "offerWarThreat" to offerWarThreat,
        "offerDiplomacyQuest" to offerDiplomacyQuest,
        "factionName" to (expedition.targetFactionName ?: ""),
        "offerReward" to offerReward,
        "offerLevelUp" to offerLevelUp,
    )

    // Post GM-whispered full offer card
    if (gmUserIds.isNotEmpty()) {
        postChatTemplate(
            templatePath = "chatmessages/expedition-result.hbs",
            templateContext = offerContext,
            whisper = gmUserIds,
        )
    }

    // Post public player-safe recap ONLY when expedition.visibleToPlayers is true
    if (expedition.visibleToPlayers) {
        val flavor = when {
            isCriticalSuccess -> t("chatMessages.expeditionRecap.flavor.criticalSuccess")
            isSuccess -> t("chatMessages.expeditionRecap.flavor.success")
            isCriticalFailure -> t("chatMessages.expeditionRecap.flavor.criticalFailure")
            else -> t("chatMessages.expeditionRecap.flavor.failure")
        }
        val recapContext = recordOf(
            "title" to expedition.title,
            "companionName" to companionName,
            "isCriticalSuccess" to isCriticalSuccess,
            // Exclusive flags: the recap prints each degree as its own line, so a crit must
            // not also light the plain success/failure line (offer card keeps inclusive flags).
            "isSuccess" to (degree == DegreeOfSuccess.SUCCESS),
            "isFailure" to (degree == DegreeOfSuccess.FAILURE),
            "isCriticalFailure" to isCriticalFailure,
            "flavor" to flavor,
        )
        postChatTemplate(
            templatePath = "chatmessages/expedition-recap.hbs",
            templateContext = recapContext,
        )
    }
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

/**
 * Apply a completed expedition's accrued reward (XP, influence, personal-quest reward, loot) to the
 * kingdom and mark it resolved. Mutates [kingdom] in place; the CALLER persists via setKingdom, so a
 * batch can apply many then persist once. Returns true if the reward was applied (false if already
 * applied / resolved — idempotent via canApplyExpeditionReward). Does NOT apply injuries or the
 * real-actor level bump — those remain separate GM-per-card offers.
 */
suspend fun applyExpeditionRewardToKingdom(
    kingdom: KingdomData,
    expedition: RawCompanionExpedition,
): Boolean {
    if (!canApplyExpeditionReward(expedition.rewardApplied, expedition.status)) return false

    // Lead companion (first id): rolls the check and owns any personal-quest reward below.
    val companionId = expedition.companionIds.firstOrNull()
    val levelingEnabled = Pfrpg2eKingdomCampingWeatherSettings.getEnableCompanionLeveling()

    // Reward + status restore for EVERY participant, not just the lead — the expedition
    // rolled once as a party, so all members share the accrued XP/influence. Restoring
    // expeditionStatus here is also what un-strands companions: a missed participant would
    // stay "onExpedition" forever and be locked out of all future launches.
    for (participantId in expedition.companionIds) {
        val companion = kingdom.companions?.find { (it.actorUuid ?: it.name) == participantId } ?: continue
        val outcome = applyExpeditionParticipantReward(
            currentLevel = companion.level,
            currentXp = companion.xp,
            currentInfluence = companion.influence,
            accruedXp = expedition.accruedXp,
            accruedInfluenceDelta = expedition.accruedInfluenceDelta,
            levelingEnabled = levelingEnabled,
        )
        if (levelingEnabled) {
            companion.level = outcome.levelResult.newLevel
            companion.xp = outcome.levelResult.newXp
        }
        companion.influence = outcome.newInfluence
        companion.expeditionStatus = "available"
    }

    // Personal-quest completion + reward.
    val isSuccess = expedition.outcomeDegree == "success" || expedition.outcomeDegree == "criticalSuccess"
    if (expedition.activityId == "personal-quest" && isSuccess && companionId != null) {
        val companion = kingdom.companions?.find { (it.actorUuid ?: it.name) == companionId }
        val quests = kingdom.companionPersonalQuests ?: emptyArray()
        val activeQuest = selectRewardQuest(quests.toList(), companionId, expedition.targetQuestId)
        if (activeQuest != null) {
            if (companion != null) {
                val priorStatus = activeQuest.status
                val beforeInfluence = companion.influence
                val beforeLevel = companion.level
                val beforeXp = companion.xp
                val outcome = applyPersonalQuestReward(
                    status = activeQuest.status,
                    currentInfluence = companion.influence,
                    influenceReward = activeQuest.influenceReward,
                    currentLevel = companion.level,
                    currentXp = companion.xp,
                    questXp = activeQuest.rewards?.xp ?: 0,
                    levelingEnabled = levelingEnabled,
                )
                activeQuest.status = outcome.newStatus
                companion.influence = outcome.newInfluence
                companion.level = outcome.levelResult.newLevel
                companion.xp = outcome.levelResult.newXp
                // Record the applied deltas so a GM "reopen" can reverse them — same snapshot the
                // manual complete-quest button writes, so an expedition-completed quest is reopenable too.
                if (outcome.applied) {
                    activeQuest.completionSnapshot = personalQuestCompletionSnapshot(
                        priorStatus = priorStatus,
                        beforeInfluence = beforeInfluence,
                        afterInfluence = companion.influence,
                        beforeLevel = beforeLevel,
                        beforeXp = beforeXp,
                        afterLevel = companion.level,
                        afterXp = companion.xp,
                    )
                }
                if (outcome.levelResult.levelsGained > 0) {
                    postChatMessage(t("kingdom.companionLeveledUp", recordOf("name" to companion.name, "level" to outcome.levelResult.newLevel)))
                }
            } else {
                // No companion object to reward; only the quest status changes, so a reopen just
                // flips it back (zero-delta snapshot keeps the reopen path consistent).
                activeQuest.completionSnapshot = personalQuestCompletionSnapshot(
                    priorStatus = activeQuest.status,
                    beforeInfluence = 0,
                    afterInfluence = 0,
                    beforeLevel = 1,
                    beforeXp = 0,
                    afterLevel = 1,
                    afterXp = 0,
                )
                activeQuest.status = "completed"
            }
            kingdom.companionPersonalQuests = quests
        }
    }

    // Loot tier -> kingdom resource points (+ treasure-hunt jackpot bonus).
    val lootRp = lootTierToResourcePoints(expedition.lootTier) + (expedition.accruedBonusLootRp ?: 0)
    if (lootRp > 0) {
        kingdom.resourcePoints.now = (kingdom.resourcePoints.now + lootRp).coerceAtLeast(0)
        postChatMessage(t("kingdom.expeditionLootApplied", recordOf("rp" to lootRp)))
    }

    // Activity commodity grants -> kingdom stores (hunt: food; craft: lumber/ore). Small.
    val food = expedition.accruedFood ?: 0
    val lumber = expedition.accruedLumber ?: 0
    val ore = expedition.accruedOre ?: 0
    if (food > 0 || lumber > 0 || ore > 0) {
        val now = kingdom.commodities.now
        now.food = (now.food + food).coerceAtLeast(0)
        now.lumber = (now.lumber + lumber).coerceAtLeast(0)
        now.ore = (now.ore + ore).coerceAtLeast(0)
        postChatMessage(t("kingdom.expeditionCommoditiesApplied", recordOf("food" to food, "lumber" to lumber, "ore" to ore)))
    }

    // Rest: shave recovery days off every injured companion kingdom-wide (a recuperation mission).
    val healDays = expedition.accruedInjuryDaysReduction ?: 0
    if (healDays > 0) {
        kingdom.companions?.forEach { c ->
            val remaining = c.injuryDaysRemaining
            if (remaining != null) {
                val reduced = remaining - healDays
                c.injuryDaysRemaining = if (reduced <= 0) null else reduced
                if (reduced <= 0) {
                    c.expeditionStatus = "available"
                    c.campAvailable = true
                }
            }
        }
        postChatMessage(t("kingdom.expeditionRestHealed", recordOf("days" to healDays)))
    }

    // Scout intel: a GM-facing narrative note (i18n key resolved here).
    expedition.accruedIntelGmNote?.let { key ->
        postChatMessage(t(key))
    }

    // Diplomacy standing -> target faction (mirrors the manual faction-adjust apply path:
    // clamp via applyStandingDelta + append a standingLog entry). No-op unless a diplomacy
    // expedition named a faction and earned a non-zero delta.
    val factionDelta = expedition.factionStandingDelta
    val targetFactionName = expedition.targetFactionName
    if (factionDelta != 0 && targetFactionName != null) {
        val group = kingdom.groups.find { it.name == targetFactionName }
        if (group != null) {
            group.standing = applyStandingDelta(group.standing, factionDelta)
            group.standingLog = (group.standingLog ?: emptyArray()) + RawFactionStandingEntry(
                // +1 for the same reason as the chronicle stamp: applied during turn N,
                // reported by the record built with the incremented turn number.
                turn = (kingdom.currentTurn ?: 0) + 1,
                delta = factionDelta,
                reason = "kingdom.factionStanding.expedition",
            )
            postChatMessage(t("kingdom.expeditionFactionStandingApplied", recordOf("name" to targetFactionName, "delta" to factionDelta)))
        } else {
            // Faction was renamed or removed between launch and apply. Don't silently drop the
            // earned standing — warn the GM to adjust it manually. The rest of the reward (already
            // applied above) still resolves; we don't bail here or the XP/loot could double-apply.
            postChatMessage(t("kingdom.expeditionFactionStandingMissing", recordOf("name" to targetFactionName, "delta" to factionDelta)))
        }
    }

    expedition.rewardApplied = true
    expedition.status = "resolved"
    kingdom.companionExpeditions = kingdom.companionExpeditions?.map {
        if (it.id == expedition.id) expedition else it
    }?.toTypedArray()?.let { pruneResolvedExpeditions(it) }

    // ---- DURABLE HISTORY: expedition chronicle + companion career ledgers ----
    recordExpeditionInHistory(kingdom, expedition, lootRp)

    return true
}

/**
 * Record an expedition into the durable history: bump every participant's career ledger and
 * append a chronicle entry (capped). Shared by BOTH terminal paths — reward apply and the
 * injury offer (which consumes the reward path) — so no resolved expedition vanishes from
 * history. careerScars is NOT counted here: a scar is recorded only when an injury is
 * actually APPLIED (km-offer-injury), not merely offered.
 *
 * The chronicle turn is stamped currentTurn + 1: entries are written DURING turn N but the
 * End Turn record that reports them is built with the incremented turn number, so +1 makes
 * the gazette filter actually match.
 */
fun recordExpeditionInHistory(
    kingdom: KingdomData,
    expedition: RawCompanionExpedition,
    lootRp: Int,
) {
    // Fall back to the stored participant id when the roster lookup misses, so the durable
    // record always names every participant even after a companion is deleted.
    val companionNames = expedition.companionIds
        .map { pid ->
            kingdom.companions?.find { (it.actorUuid ?: it.name) == pid }?.name ?: pid
        }
        .joinToString(", ")

    val isCriticalSuccess = expedition.outcomeDegree == "criticalSuccess"
    for (participantId in expedition.companionIds) {
        val companion = kingdom.companions?.find { (it.actorUuid ?: it.name) == participantId } ?: continue
        companion.careerExpeditions = (companion.careerExpeditions ?: 0) + 1
        if (isCriticalSuccess) companion.careerTriumphs = (companion.careerTriumphs ?: 0) + 1
    }

    val chronicleEntry = createRawExpeditionChronicleEntry(
        title = expedition.title,
        companionNames = companionNames,
        activityId = expedition.activityId,
        outcomeDegree = expedition.outcomeDegree ?: "failure",
        lootRp = lootRp,
        factionStandingDelta = expedition.factionStandingDelta,
        targetFactionName = expedition.targetFactionName,
        turn = (kingdom.currentTurn ?: 0) + 1,
        appliedAt = kotlin.js.Date().toISOString(),
    )
    kingdom.expeditionChronicle = pruneExpeditionChronicle(
        (kingdom.expeditionChronicle ?: emptyArray()) + chronicleEntry,
    )
}
