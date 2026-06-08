package at.posselt.pfrpg2e.kingdom.data

import at.posselt.pfrpg2e.data.ValueEnum
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.localization.Translatable
import at.posselt.pfrpg2e.toCamelCase

/** Lifecycle status of a [RawArmyDeployment] (roadmap #12). */
enum class ArmyDeploymentStatus : Translatable, ValueEnum {
    DEPLOYED,
    BATTLE,
    RETREATED,
    DESTROYED;

    companion object {
        fun fromString(value: String) = fromCamelCase<ArmyDeploymentStatus>(value)
    }

    override val value: String
        get() = toCamelCase()

    override val i18nKey: String
        get() = "armyDeploymentStatus.$value"
}
