package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

/**
 * Global war-pressure track (roadmap #12): the cumulative 0-100 effect of active
 * threats (minus deployed armies) on the kingdom. Auto-calculated each tick;
 * thresholds add unrest/ruin modifiers.
 */
@JsPlainObject
external interface RawWarPressure {
    var currentPressure: Int
    var pressurePerTurn: Int

    var unrestModifier: Int
    var consumptionModifier: Int

    var unrestThreshold: Int
    var ruinThreshold: Int

    var lastChange: Int?
}
