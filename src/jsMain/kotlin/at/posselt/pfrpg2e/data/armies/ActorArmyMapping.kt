package at.posselt.pfrpg2e.data.armies

import at.posselt.pfrpg2e.data.armies.*

object ActorArmyMapping {
    /**
     * Pure mapping function: converts raw PF2EArmy actor data (from Foundry) into a [BattleArmyState].
     * This is the single source of truth for "what numbers enter the engine".
     *
     * The actor data fixture shape mirrors the PF2EArmy system data structure:
     * ```json
     * {
     *   "name": "1st Legion",
     *   "system": {
     *     "details": { "level": { "value": 5 } },
     *     "attributes": {
     *       "hp": { "value": 60, "max": 60 },
     *       "ac": { "value": 22 }
     *     },
     *     "items": [
     *       { "system": { "bonus": { "value": 15 } } }
     *     ]
     *   }
     * }
     * ```
     *
     * @param actorData  A plain JS object matching the PF2EArmy system data shape (see fixture above).
     * @return           Battle-ready state with real HP, AC, attack bonus, level, and saves.
     */
    fun toBattleArmyStateFromActorData(
        actorData: Any,
    ): BattleArmyState {
        // We use dynamic access because this runs with js("{}") fixtures in tests,
        // and with real PF2EArmy actors at runtime. The shape is validated by the JS test fixtures.
        val dyn = actorData.asDynamic()

        // Name: prefer actor name, fallback to "Unnamed Army"
        val name = (dyn.name as String?) ?: "Unnamed Army"

        // Level from system.details.level.value
        val level = (dyn.system?.details?.level?.value as Int?) ?: 1

        // HP from system.attributes.hp
        val maxHp = (dyn.system?.attributes?.hp?.max as Int?) ?: 4
        val currentHp = (dyn.system?.attributes?.hp?.value as Int?) ?: maxHp

        // AC from system.attributes.ac.value
        val ac = (dyn.system?.attributes?.ac?.value as Int?) ?: getArmyAc(level)

        // Attack bonus: find the best strike bonus from items
        val attackBonus = computeBestStrikeBonus(dyn.system?.items, level)

        // Saves: try to read from system.attributes (high/low), fall back to workbook table
        val highSave = (dyn.system?.attributes?.highSave?.value as Int?) ?: getArmyHighSave(level)
        val lowSave = (dyn.system?.attributes?.lowSave?.value as Int?) ?: getArmyLowSave(level)

        // Rout threshold: maxHp / 4 rounded up (workbook default)
        val routThreshold = ((maxHp + 3) / 4).coerceAtLeast(0)

        return BattleArmyState(
            name = name,
            level = level,
            currentHp = currentHp.coerceIn(0, maxHp),
            maxHp = maxHp,
            conditions = emptySet(),
            attackBonus = attackBonus,
            ac = ac,
            routThreshold = routThreshold,
            moraleBonus = 0,
            xp = 0,
            highSave = highSave,
            lowSave = lowSave,
        )
    }

    /**
     * Extracts the best strike attack bonus from the army's items.
     * Items with a `system.bonus.value` are treated as strikes.
     * Falls back to the workbook level-based attack bonus if none found.
     */
    private fun computeBestStrikeBonus(items: Any?, fallbackLevel: Int): Int {
        if (items == null) return getArmyAttackBonus(fallbackLevel)

        val dynItems = items.asDynamic()
        var best = Int.MIN_VALUE
        var found = false

        // Items can be an array or an EmbeddedCollection with .contents
        val contents = (dynItems.contents?.asDynamic()) ?: dynItems
        if (contents is Array<*>) {
            for (item in contents) {
                val itemDyn = item.asDynamic()
                val bonus = (itemDyn?.system?.bonus?.value as Int?) ?: continue
                if (bonus > best) {
                    best = bonus
                    found = true
                }
            }
        }

        return if (found) best else getArmyAttackBonus(fallbackLevel)
    }
}
