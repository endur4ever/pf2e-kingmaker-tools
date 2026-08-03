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

    /**
     * Turn whose ruin-threshold offer card has been answered (applied or dismissed) — idempotency
     * guard so the km-offer-war-ruin buttons can only fire once per crossing. Nullable for saves
     * predating the field; no migration needed.
     */
    var ruinOfferTurn: Int?
}
