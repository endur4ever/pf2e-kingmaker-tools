package at.posselt.pfrpg2e.kingdom.sheet

import at.posselt.pfrpg2e.data.ValueEnum
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.localization.Translatable
import at.posselt.pfrpg2e.toCamelCase

enum class ResourceMode : Translatable, ValueEnum {
    GAIN,
    LOSE;

    companion object {
        fun fromString(value: String) = fromCamelCase<ResourceMode>(value)
    }

    override val i18nKey = "resourceButton.mode.$value"

    override val value: String
        get() = toCamelCase()
}

enum class Resource : Translatable, ValueEnum {
    RESOURCE_DICE,
    CRIME,
    EVENT,
    DECAY,
    CORRUPTION,
    CONSUMPTION,
    STRIFE,
    RESOURCE_POINTS,
    FOOD,
    LUXURIES,
    UNREST,
    ORE,
    LUMBER,
    FAME,
    STONE,
    XP,
    SUPERNATURAL_SOLUTION,
    CREATIVE_SOLUTION,
    ROLLED_RESOURCE_DICE;

    companion object {
        fun fromString(value: String) = entries.find { it.value == value }
    }

    override val value: String
        get() = toCamelCase()

    override val i18nKey = "resourceButton.resource.$value"

    val i18nKeyExpression = "resourceButton.resourceExpression.$value"
}