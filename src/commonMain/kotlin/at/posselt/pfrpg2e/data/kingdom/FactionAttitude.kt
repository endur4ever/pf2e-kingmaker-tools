package at.posselt.pfrpg2e.data.kingdom

import at.posselt.pfrpg2e.data.ValueEnum
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.localization.Translatable
import at.posselt.pfrpg2e.toCamelCase

/**
 * Diplomatic attitude band of a faction/group toward the player kingdom, derived from a
 * numeric standing via [attitudeFor]. Declared worst-to-best so the natural enum ordering
 * (by ordinal) reads as "more friendly", which [shouldOfferDiplomacyQuest] relies on.
 */
enum class FactionAttitude : Translatable, ValueEnum {
    HOSTILE,
    UNFRIENDLY,
    INDIFFERENT,
    FRIENDLY,
    HELPFUL;

    companion object {
        fun fromString(value: String) = fromCamelCase<FactionAttitude>(value)
    }

    override val value: String
        get() = toCamelCase()

    override val i18nKey: String
        get() = "factionAttitude.$value"
}
