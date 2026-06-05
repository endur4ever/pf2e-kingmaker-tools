package at.posselt.pfrpg2e.campaign

/**
 * Pure engine — no Foundry/Game dependencies, fully unit-testable.
 */
object CampaignClockManager {

    /**
     * Tick all active, non-expired clocks by 1 turn.
     * Paused-at-expiry clocks (pauseOnExpiry && turnsRemaining == 0 && !expired)
     * are skipped and produce a PAUSED event.
     */
    fun tickAll(clocks: Array<CampaignClock>): ClockTickResult {
        val events = mutableListOf<ClockTickEvent>()
        var totalUnrest = 0

        val updated = clocks.map { clock ->
            if (!clock.active || (clock.expired && !clock.pauseOnExpiry)) return@map clock

            if (clock.turnsRemaining == 0 && clock.pauseOnExpiry) {
                // Decision 3: soft-pause — do not tick
                events += createEvent(clock.id, clock.label, "PAUSED", 0, 0, null, 0)
                return@map clock
            }

            if (clock.turnsRemaining <= 1) {
                // Reaches 0 this tick → expire
                val unrest = if (!clock.pauseOnExpiry) clock.expiryConsequenceUnrest else 0
                totalUnrest += unrest

                val expiredClock = jsObject<CampaignClock> {
                    id = clock.id
                    label = clock.label
                    maxTurns = clock.maxTurns
                    turnsRemaining = 0
                    description = clock.description
                    pauseOnExpiry = clock.pauseOnExpiry
                    expired = true
                    active = clock.active
                    expiryConsequenceUnrest = clock.expiryConsequenceUnrest
                    expiryMessage = clock.expiryMessage
                }

                events += createEvent(clock.id, clock.label, "EXPIRED", 1, 0, clock.expiryMessage, unrest)

                if (!clock.pauseOnExpiry && unrest > 0) {
                    events += createEvent(clock.id, clock.label, "TRIGGERED", 0, 0, "Unrest +${clock.expiryConsequenceUnrest}", 0)
                }
                return@map expiredClock
            }

            // Normal advance
            val oldTurns = clock.turnsRemaining
            val newTurns = oldTurns - 1
            val advancedClock = jsObject<CampaignClock> {
                id = clock.id
                label = clock.label
                maxTurns = clock.maxTurns
                turnsRemaining = newTurns
                description = clock.description
                pauseOnExpiry = clock.pauseOnExpiry
                expired = false
                active = clock.active
                expiryConsequenceUnrest = clock.expiryConsequenceUnrest
                expiryMessage = clock.expiryMessage
            }

            events += createEvent(clock.id, clock.label, "ADVANCED", oldTurns, newTurns, null, 0)
            return@map advancedClock
        }.toTypedArray()

        return ClockTickResult(
            events = events.toTypedArray(),
            updatedClocks = updated,
            totalUnrestChange = totalUnrest
        )
    }

    private fun createEvent(
        clockId: String,
        label: String,
        type: String,
        oldTurns: Int,
        newTurns: Int,
        message: String?,
        unrestChange: Int
    ): ClockTickEvent {
        return jsObject {
            this.clockId = clockId
            this.type = type
            this.label = label
            this.oldTurns = oldTurns
            this.newTurns = newTurns
            this.message = message
            this.unrestChange = unrestChange
        }
    }
}

/**
 * Helper to create a JS object with the given properties.
 * Usage: jsObject { prop1 = value1; prop2 = value2 }
 */
inline fun <T> jsObject(init: dynamic.() -> Unit): T {
    val obj = js("{}")
    obj.init()
    return obj.unsafeCast<T>()
}
