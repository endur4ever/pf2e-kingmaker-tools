package at.posselt.pfrpg2e.data.armies

import at.posselt.pfrpg2e.data.armies.*

object ActorArmyMapping {
    /**
     * Pure mapping function: converts raw PF2EArmy actor data (from Foundry) into a [BattleArmyState].
     * This is the single source of truth for "what numbers enter the engine".
     *
     * The fixture shape mirrors the REAL PF2e `ArmySystemData` schema (verified against the
     * system's compiled `defineSchema()`, v8.1.2 — the original guess put AC/saves under
     * `attributes` and scanned items for strike bonuses; none of those paths exist, so every
     * custom army silently fell back to the workbook level table for everything but HP):
     * ```json
     * {
     *   "name": "1st Legion",
     *   "system": {
     *     "details": { "level": { "value": 5 } },
     *     "ac": { "value": 22, "potency": 1 },
     *     "saves": { "maneuver": 12, "morale": 8 },
     *     "weapons": { "melee": { "name": "Pikes", "potency": 1 }, "ranged": null },
     *     "attributes": { "hp": { "value": 60, "max": 60, "routThreshold": 15 } }
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

        // AC: TOP-LEVEL system.ac — sheet value plus armor potency rune
        val acValue = dyn.system?.ac?.value as Int?
        val acPotency = (dyn.system?.ac?.potency as Int?) ?: 0
        val ac = acValue?.plus(acPotency) ?: getArmyAc(level)

        // Attack bonus: level-table baseline plus the best weapon potency rune. Armies keep
        // their weapons at system.weapons.melee/.ranged (name + potency) — they are NOT items.
        val meleePotency = dyn.system?.weapons?.melee?.potency as Int? ?: 0
        val rangedPotency = dyn.system?.weapons?.ranged?.potency as Int? ?: 0
        val attackBonus = getArmyAttackBonus(level) + maxOf(meleePotency, rangedPotency, 0)

        // Saves: system.saves.maneuver (strong by default) / .morale (weak by default). An actor
        // can invert them, so the engine's high/low pair takes the actual max/min.
        val maneuver = dyn.system?.saves?.maneuver as Int?
        val morale = dyn.system?.saves?.morale as Int?
        val highSave = if (maneuver != null && morale != null) maxOf(maneuver, morale) else maneuver ?: morale ?: getArmyHighSave(level)
        val lowSave = if (maneuver != null && morale != null) minOf(maneuver, morale) else getArmyLowSave(level)

        // Rout threshold: the sheet keeps it at attributes.hp.routThreshold; derive only when absent.
        val routThreshold = (dyn.system?.attributes?.hp?.routThreshold as Int?)
            ?: ((maxHp + 3) / 4 + getArmyRoutThresholdModifier(name)).coerceAtLeast(0)

        return BattleArmyState(
            name = name,
            level = level,
            currentHp = currentHp.coerceIn(0, maxHp),
            maxHp = maxHp,
            conditions = emptySet(),
            attackBonus = attackBonus,
            ac = ac,
            routThreshold = routThreshold,
            moraleBonus = morale ?: 0,
            xp = 0,
            highSave = highSave,
            lowSave = lowSave,
        )
    }
}
