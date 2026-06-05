package at.posselt.pfrpg2e.data.hex

import at.posselt.pfrpg2e.data.ValueEnum
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.localization.Translatable
import at.posselt.pfrpg2e.toCamelCase

enum class HexContentType : Translatable, ValueEnum {
    LANDMARK,
    REFUGE,
    WORKSITE,
    RESOURCE,
    RUIN,
    MERCHANT,
    TRAINER,
    ENEMY_ARMY,
    CUSTOM;

    companion object {
        fun fromString(value: String) = fromCamelCase<HexContentType>(value)
    }

    override val value: String
        get() = toCamelCase()

    override val i18nKey: String
        get() = "hexContentType.$value"
}
