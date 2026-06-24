package at.posselt.pfrpg2e.companion

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
