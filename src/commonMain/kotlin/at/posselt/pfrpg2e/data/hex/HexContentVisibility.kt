package at.posselt.pfrpg2e.data.hex

import at.posselt.pfrpg2e.data.ValueEnum
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.localization.Translatable
import at.posselt.pfrpg2e.toCamelCase

enum class HexContentVisibility : Translatable, ValueEnum {
    HIDDEN,
    DISCOVERED,
    CLEARED;

    companion object {
        fun fromString(value: String) = fromCamelCase<HexContentVisibility>(value)
    }

    override val value: String
        get() = toCamelCase()

    override val i18nKey: String
        get() = "hexContentVisibility.$value"
}
