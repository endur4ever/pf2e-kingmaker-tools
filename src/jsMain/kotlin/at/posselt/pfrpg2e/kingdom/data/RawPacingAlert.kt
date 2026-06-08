package at.posselt.pfrpg2e.kingdom.data

import at.posselt.pfrpg2e.data.ValueEnum
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.localization.Translatable
import at.posselt.pfrpg2e.toCamelCase
import kotlinx.js.JsPlainObject

/**
 * An advisory balance/pacing alert (roadmap #13). Purely informational — generated
 * during the turn tick when the campaign drifts from the intended pressure curve.
 */
@JsPlainObject
external interface RawPacingAlert {
    var id: String
    var type: String
    var severity: String
    var message: String
    var turnCreated: Int
    var relatedEntityId: String?
}

enum class PacingAlertSeverity : Translatable, ValueEnum {
    WARNING,
    CRITICAL;

    companion object {
        fun fromString(value: String) = fromCamelCase<PacingAlertSeverity>(value)
    }

    override val value: String get() = toCamelCase()
    override val i18nKey: String get() = "pacingAlertSeverity.$value"
}

enum class PacingAlertType : Translatable, ValueEnum {
    LEVEL_MISMATCH,
    STAGNATION,
    TURN_GAP,
    LOOT_IMBALANCE;

    companion object {
        fun fromString(value: String) = fromCamelCase<PacingAlertType>(value)
    }

    override val value: String get() = toCamelCase()
    override val i18nKey: String get() = "pacingAlertType.$value"
}
