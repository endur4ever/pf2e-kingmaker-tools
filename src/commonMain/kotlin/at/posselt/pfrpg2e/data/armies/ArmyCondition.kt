package at.posselt.pfrpg2e.data.armies

import at.posselt.pfrpg2e.data.ValueEnum
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.localization.Translatable
import at.posselt.pfrpg2e.toCamelCase

enum class ArmyCondition : Translatable, ValueEnum {
    MIRED,
    PINNED,
    WEARY,
    DAMAGED,
    ROUTED,
    DESTROYED;

    companion object {
        fun fromString(value: String) = fromCamelCase<ArmyCondition>(value)
    }

    override val value: String
        get() = toCamelCase()

    override val i18nKey: String
        get() = "armyCondition.$value"
}
