package at.posselt.pfrpg2e.companion

import at.posselt.pfrpg2e.data.checks.getLevelBasedDC

/**
 * Companion level/XP subsystem.
 *
 * Standard PF2e 1000-XP/level track. Companions gain XP from expeditions
 * and personal quests, leveling silently in the background (shadow track).
 * Level is capped at 20; XP is capped at 999 (overflow only at 20).
 */

/** Maximum companion level. */
const val MAX_COMPANION_LEVEL: Int = 20

/** XP required per level (flat 1000 XP per level). */
const val XP_PER_LEVEL: Int = 1000

/** Maximum XP a companion can have (capped at level 20 with 0 overflow). */
const val MAX_COMPANION_XP: Int = MAX_COMPANION_LEVEL * XP_PER_LEVEL

/**
 * Result of applying XP to a companion.
 *
 * @property newLevel The new level after applying XP (1-20).
 * @property newXp The new XP value after level consumption (0-999 at cap).
 * @property levelsGained How many levels were gained from this application.
 */
data class LevelUpResult(
    val newLevel: Int,
    val newXp: Int,
    val levelsGained: Int,
)

/**
 * Clamp a companion level into the valid [1, MAX_COMPANION_LEVEL] range.
 */
fun clampCompanionLevel(level: Int): Int = level.coerceIn(1, MAX_COMPANION_LEVEL)

/**
 * Apply XP gain to a companion, consuming XP into levels as needed.
 *
 * Loops while xp >= 1000 and level < 20. Caps overflow to 0 at max level.
 * Negative gainedXp is guarded via coerceAtLeast(0).
 *
 * @param currentLevel The companion's current level (1-20).
 * @param currentXp The companion's current XP (0+).
 * @param gainedXp The XP gained (will be floored at 0).
 * @return A [LevelUpResult] with the new level, new XP, and levels gained.
 */
fun applyCompanionXp(currentLevel: Int, currentXp: Int, gainedXp: Int): LevelUpResult {
    var level = clampCompanionLevel(currentLevel)
    var xp = (currentXp + gainedXp.coerceAtLeast(0)).coerceAtLeast(0)
    var levelsGained = 0

    while (xp >= XP_PER_LEVEL && level < MAX_COMPANION_LEVEL) {
        xp -= XP_PER_LEVEL
        level += 1
        levelsGained += 1
    }

    // At max level, cap overflow to 0
    if (level >= MAX_COMPANION_LEVEL) {
        xp = 0
    }

    return LevelUpResult(
        newLevel = level,
        newXp = xp,
        levelsGained = levelsGained,
    )
}

/**
 * A companion's level+XP collapsed to one monotonic scalar: (level-1)*1000 + xp. Level 1 / 0 XP
 * maps to 0. Lets a level-crossing reward (or its reversal) be expressed as a single delta.
 * Inverse of [fromTotalCompanionXp].
 */
fun toTotalCompanionXp(level: Int, xp: Int): Int =
    (clampCompanionLevel(level) - 1) * XP_PER_LEVEL + xp.coerceAtLeast(0)

/**
 * Inverse of [toTotalCompanionXp]: expand a total-XP scalar back to level/xp, capping at
 * [MAX_COMPANION_LEVEL] with 0 overflow (mirrors [applyCompanionXp]'s cap). [levelsGained] is
 * unused here and always 0. Negative totals clamp to level 1 / 0 XP.
 */
fun fromTotalCompanionXp(total: Int): LevelUpResult {
    val t = total.coerceAtLeast(0)
    var level = t / XP_PER_LEVEL + 1
    var xp = t % XP_PER_LEVEL
    if (level >= MAX_COMPANION_LEVEL) {
        level = MAX_COMPANION_LEVEL
        xp = 0
    }
    return LevelUpResult(newLevel = level, newXp = xp, levelsGained = 0)
}

/**
 * Build the reward-delta snapshot a personal-quest completion records so a later reopen can
 * reverse it. [influenceDelta] is the post-clamp influence change; [xpDelta] is measured in
 * total-XP space so a level-up reverses cleanly. Shared by the manual complete-quest button and
 * the expedition reward path so the two can never drift.
 */
fun personalQuestCompletionSnapshot(
    priorStatus: String,
    beforeInfluence: Int,
    afterInfluence: Int,
    beforeLevel: Int,
    beforeXp: Int,
    afterLevel: Int,
    afterXp: Int,
): PersonalQuestCompletionSnapshot = PersonalQuestCompletionSnapshot(
    priorStatus = priorStatus,
    influenceDelta = afterInfluence - beforeInfluence,
    xpDelta = toTotalCompanionXp(afterLevel, afterXp) - toTotalCompanionXp(beforeLevel, beforeXp),
)

/** Companion state after reversing a personal-quest completion. */
data class PersonalQuestReopenOutcome(
    val newStatus: String,
    val newInfluence: Int,
    val newLevel: Int,
    val newXp: Int,
)

/**
 * Reverse a personal-quest completion from its [snapshot], restoring the companion's influence and
 * level/XP by subtracting the applied deltas (total-XP space handles level-downs) and returning the
 * quest to its prior status. Pure + unit-tested. Exact when nothing else moved the companion since
 * completion; otherwise preserves unrelated changes by working in deltas rather than absolutes.
 */
fun reversePersonalQuestReward(
    snapshot: PersonalQuestCompletionSnapshot,
    currentInfluence: Int,
    currentLevel: Int,
    currentXp: Int,
): PersonalQuestReopenOutcome {
    val restored = fromTotalCompanionXp(toTotalCompanionXp(currentLevel, currentXp) - snapshot.xpDelta)
    return PersonalQuestReopenOutcome(
        newStatus = snapshot.priorStatus,
        newInfluence = clampInfluence(currentInfluence - snapshot.influenceDelta),
        newLevel = restored.newLevel,
        newXp = restored.newXp,
    )
}

/**
 * XP actually accrued from an expedition, honoring the companion-leveling setting.
 * When leveling is disabled the expedition still resolves for flavor but awards no XP.
 */
fun accruedExpeditionXp(xpAwarded: Int, levelingEnabled: Boolean): Int =
    if (levelingEnabled) xpAwarded else 0

/**
 * Whether a completed expedition should surface a companion level-up offer.
 *
 * Only when leveling is enabled, the companion is not an NPC (NPCs accrue shadow XP
 * but never get the level offer), the projected XP crosses a level threshold, and the
 * companion is below the level cap.
 */
fun shouldOfferLevelUp(
    levelingEnabled: Boolean,
    isNpc: Boolean,
    currentLevel: Int,
    currentXp: Int,
    xpAwarded: Int,
): Boolean =
    levelingEnabled &&
        !isNpc &&
        (currentXp + xpAwarded) >= XP_PER_LEVEL &&
        currentLevel < MAX_COMPANION_LEVEL

/**
 * Result of applying a personal-quest completion reward to one quest/companion.
 *
 * @property applied False when the quest was not "active" (idempotent no-op).
 * @property newStatus The quest's status after applying ("completed" on apply).
 * @property newInfluence The companion's clamped influence after the reward.
 * @property levelResult The companion's level/xp after the quest XP (honoring the setting).
 */
data class QuestRewardOutcome(
    val applied: Boolean,
    val newStatus: String,
    val newInfluence: Int,
    val levelResult: LevelUpResult,
)

/**
 * Apply a personal-quest completion reward (influence + XP) to a single quest, idempotently.
 *
 * A quest that is not "active" returns `applied = false` with unchanged values, so re-running
 * the resolution (e.g. a re-clicked reward button) never double-applies. Quest XP honors the
 * companion-leveling setting via [accruedExpeditionXp].
 */
fun applyPersonalQuestReward(
    status: String,
    currentInfluence: Int,
    influenceReward: Int,
    currentLevel: Int,
    currentXp: Int,
    questXp: Int,
    levelingEnabled: Boolean,
): QuestRewardOutcome {
    if (status != "active") {
        return QuestRewardOutcome(
            applied = false,
            newStatus = status,
            newInfluence = currentInfluence,
            levelResult = LevelUpResult(newLevel = currentLevel, newXp = currentXp, levelsGained = 0),
        )
    }
    return QuestRewardOutcome(
        applied = true,
        newStatus = "completed",
        newInfluence = clampInfluence(currentInfluence + influenceReward),
        levelResult = applyCompanionXp(currentLevel, currentXp, accruedExpeditionXp(questXp, levelingEnabled)),
    )
}

/**
 * Whether an expedition's completion reward can still be applied.
 *
 * Guards double-apply: a reward already applied, or an expedition already resolved,
 * must not award XP/influence/loot a second time (e.g. a re-clicked offer button).
 */
fun canApplyExpeditionReward(rewardApplied: Boolean, status: String): Boolean =
    !rewardApplied && status != "resolved"

/**
 * The `expeditionStatus` a participant should hold after their expedition's reward is applied.
 *
 * Applying the reward un-strands participants (otherwise a member stays "onExpedition" forever and
 * is locked out of future launches) — but it must NOT cancel injury downtime. Injury and reward are
 * independent GM offers on the same result card and can be clicked in either order; an injured
 * companion stays "unavailable" until the daily tick counts their recovery days down.
 */
fun participantStatusAfterReward(injuryDaysRemaining: Int?): String =
    if ((injuryDaysRemaining ?: 0) > 0) "unavailable" else "available"

/**
 * Resource points a completed expedition's loot tier yields to the kingdom treasury.
 *
 * Deliberately small so expeditions supplement rather than replace kingdom income
 * (they are GM-confirmed and capped at 3 concurrent). Tune the amounts here.
 */
fun lootTierToResourcePoints(lootTier: String?): Int = when (lootTier) {
    "minor" -> 1
    "moderate" -> 2
    "major" -> 3
    else -> 0 // "none" / null
}

/**
 * Resource-point cost to provision an expedition, scaled by difficulty tier.
 *
 * A modest sink so launching is a real (if small) economic decision alongside the
 * concurrency cap. Tune here. Deducted (coerced to >= 0) when the expedition is created.
 */
fun expeditionLaunchCost(tier: String): Int = when (tier) {
    "routine" -> 1
    "perilous" -> 3
    else -> 2 // standard
}

/** Outcome of applying an expedition's accrued reward to ONE participant. */
data class ExpeditionParticipantOutcome(
    val levelResult: LevelUpResult,
    val newInfluence: Int,
)

/**
 * Apply an expedition's accrued XP + influence to a single participant.
 *
 * The expedition rolls ONCE as a party (lead companion's check), so every participant
 * receives the SAME accrued reward — the apply site loops this helper over all of
 * `expedition.companionIds`. XP honors the leveling setting via [accruedExpeditionXp];
 * influence is clamped. Pure and unit-tested.
 */
fun applyExpeditionParticipantReward(
    currentLevel: Int,
    currentXp: Int,
    currentInfluence: Int,
    accruedXp: Int,
    accruedInfluenceDelta: Int,
    levelingEnabled: Boolean,
): ExpeditionParticipantOutcome = ExpeditionParticipantOutcome(
    levelResult = applyCompanionXp(
        currentLevel = currentLevel,
        currentXp = currentXp,
        gainedXp = accruedExpeditionXp(accruedXp, levelingEnabled),
    ),
    newInfluence = if (accruedInfluenceDelta != 0) {
        clampInfluence(currentInfluence + accruedInfluenceDelta)
    } else {
        currentInfluence
    },
)

/** Tier modifier on the level-based expedition DC (design doc 3.5: routine −2 / standard 0 / perilous +4). */
fun expeditionTierDcModifier(tier: String): Int = when (tier) {
    "routine" -> -2
    "perilous" -> 4
    else -> 0 // standard
}

/**
 * Expedition check DC (design doc 3.5): the level-based DC of the STRONGEST participant
 * (14 + L + L/3, the standard PF2e level-DC curve) plus the tier modifier. Tracking the
 * companion's own level keeps success rates stable across a career — a hardcoded DC lets
 * a high-level companion auto-crit routine work forever, hollowing out the leveling loop.
 */
fun expeditionDc(maxParticipantLevel: Int, tier: String): Int =
    getLevelBasedDC(clampCompanionLevel(maxParticipantLevel)) + expeditionTierDcModifier(tier)
