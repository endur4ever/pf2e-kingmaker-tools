package at.posselt.pfrpg2e.data.armies

import at.posselt.pfrpg2e.data.ValueEnum
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.localization.Translatable
import at.posselt.pfrpg2e.toCamelCase

enum class BattleStatus : Translatable, ValueEnum {
    ACTIVE,
    VICTORY,
    DEFEAT,
    RETREAT;

    companion object {
        fun fromString(value: String) = fromCamelCase<BattleStatus>(value)
    }

    override val value: String
        get() = toCamelCase()

    override val i18nKey: String
        get() = "battleStatus.$value"
}
