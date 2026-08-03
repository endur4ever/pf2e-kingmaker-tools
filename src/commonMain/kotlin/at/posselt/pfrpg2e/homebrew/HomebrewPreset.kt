package at.posselt.pfrpg2e.homebrew

import at.posselt.pfrpg2e.data.ValueEnum
import at.posselt.pfrpg2e.localization.Translatable
import at.posselt.pfrpg2e.fromCamelCase

/**
 * Enum representing built‑in homebrew presets.
 * Implements both `ValueEnum` (for serialization) and `Translatable` (for i18n).
 */
enum class HomebrewPreset(
    override val value: String,
    override val i18nKey: String,
) : ValueEnum, Translatable {
    NONE("none", "pf2e-kingmaker-tools.enums.homebrewPreset.none"),
    GREGORY("gregory", "pf2e-kingmaker-tools.enums.homebrewPreset.gregory");

    companion object {
        /** Convert a raw string to the enum, ignoring case and handling camel‑case conversion. */
        fun fromString(value: String) = fromCamelCase<HomebrewPreset>(value)
    }
}
