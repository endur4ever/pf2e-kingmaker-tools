package at.posselt.pfrpg2e.kingdom

/**
 * Pure derivation of the Vance & Kerenshara (V&K) homebrew kingdom-creation extras.
 *
 * These helpers translate the V&K creation sub-settings into the number of extra
 * initial kingdom skill-training slots and free ability boosts a brand-new kingdom
 * receives during character creation. They are deliberately UI-free so they can be
 * unit-tested directly (see `VkExtrasTest`) and reused by
 * [at.posselt.pfrpg2e.kingdom.sheet.KingdomSheet] when it builds the creation form.
 *
 * Each toggle is independent and applies regardless of the parent
 * [KingdomSettings.vanceAndKerensharaXP] XP setting; the three sub-settings default
 * to `false`, so a kingdom created without them keeps the RAW base values.
 *
 * Source: Vance & Kerenshara homebrew kingdom-creation rules.
 */

/** RAW number of trained kingdom skills granted at creation before any V&K extras. */
const val BASE_INITIAL_SKILL_SLOTS = 4

/** RAW number of free kingdom ability boosts granted at creation before any V&K extras. */
const val BASE_ABILITY_BOOSTS = 2

/**
 * Extra initial trained-skill slots granted by the V&K charter/heartland sub-rules:
 * +1 for [KingdomSettings.vkCharterExtraSkills] and +1 for
 * [KingdomSettings.vkHeartlandExtraSkills]. Result is in `0..2`.
 */
fun vkExtraSkillSlots(settings: KingdomSettings): Int =
    listOf(
        settings.vkCharterExtraSkills == true,
        settings.vkHeartlandExtraSkills == true,
    ).count { it }

/** Total initial trained kingdom skill slots: [BASE_INITIAL_SKILL_SLOTS] plus V&K extras. */
fun vkInitialSkillSlots(settings: KingdomSettings): Int =
    BASE_INITIAL_SKILL_SLOTS + vkExtraSkillSlots(settings)

/**
 * Extra free ability boosts granted by the V&K [KingdomSettings.vkExtraAbilityBoost]
 * sub-rule: `1` when enabled, otherwise `0`.
 */
fun vkExtraAbilityBoosts(settings: KingdomSettings): Int =
    if (settings.vkExtraAbilityBoost == true) 1 else 0
