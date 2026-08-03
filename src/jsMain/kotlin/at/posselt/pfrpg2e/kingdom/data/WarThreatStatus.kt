package at.posselt.pfrpg2e.kingdom.data

import at.posselt.pfrpg2e.data.ValueEnum
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.localization.Translatable
import at.posselt.pfrpg2e.toCamelCase

/** Lifecycle status of a [RawWarThreat] (roadmap #12). */
enum class WarThreatStatus : Translatable, ValueEnum {
    ACTIVE,
    DEFEATED,
    EVADED,
    EXPIRED;

    companion object {
        fun fromString(value: String) = fromCamelCase<WarThreatStatus>(value)
    }

    override val value: String
        get() = toCamelCase()

    override val i18nKey: String
        get() = "warThreatStatus.$value"
}
