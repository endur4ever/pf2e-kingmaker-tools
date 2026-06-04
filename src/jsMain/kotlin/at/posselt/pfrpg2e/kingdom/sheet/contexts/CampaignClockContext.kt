package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.campaign.CampaignClock
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface CampaignClockCardContext {
    val id: String
    val label: String
    val description: String
    val remainingTurns: Int
    val maxTurns: Int
    val progressPercent: Int
    val isExpiring: Boolean
    val isExpired: Boolean
    val paused: Boolean
    val active: Boolean
    val expiryMessage: String
}

@JsPlainObject
external interface CampaignClockContext {
    val campaignClocks: Array<CampaignClockCardContext>
    val isGM: Boolean
    val hasActiveClocks: Boolean
    val activeClockCount: Int
    val expiringClockCount: Int
}

fun Array<CampaignClock>.toCardContexts(): Array<CampaignClockCardContext> =
    map { clock ->
        val progressPercent = if (clock.maxTurns > 0) {
            (clock.turnsRemaining * 100 / clock.maxTurns)
        } else {
            0
        }
        val isExpiring = clock.turnsRemaining == 1 && clock.active && !clock.expired
        val paused = clock.pauseOnExpiry && clock.turnsRemaining == 0 && clock.expired
        CampaignClockCardContext(
            id = clock.id,
            label = clock.label,
            description = clock.description,
            remainingTurns = clock.turnsRemaining,
            maxTurns = clock.maxTurns,
            progressPercent = progressPercent,
            isExpiring = isExpiring,
            isExpired = clock.expired,
            paused = paused,
            active = clock.active,
            expiryMessage = clock.expiryMessage,
        )
    }.toTypedArray()

fun Array<CampaignClock>.toDashboardContext(isGM: Boolean): CampaignClockContext {
    val activeCount = count { it.active && !it.expired }
    val expiringCount = count { it.active && !it.expired && it.turnsRemaining <= 1 }
    return CampaignClockContext(
        campaignClocks = toCardContexts(),
        isGM = isGM,
        hasActiveClocks = activeCount > 0,
        activeClockCount = activeCount,
        expiringClockCount = expiringCount,
    )
}
