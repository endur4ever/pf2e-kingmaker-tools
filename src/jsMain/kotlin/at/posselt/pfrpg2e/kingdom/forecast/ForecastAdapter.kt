package at.posselt.pfrpg2e.kingdom.forecast

import at.posselt.pfrpg2e.campaign.ClockEventType
import at.posselt.pfrpg2e.kingdom.CARAVAN_BASE_RAID_DC
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.TickResult
import at.posselt.pfrpg2e.kingdom.caravanRaidDc
import at.posselt.pfrpg2e.kingdom.caravanRouteSafety
import at.posselt.pfrpg2e.kingdom.computeCaravanRoute
import at.posselt.pfrpg2e.kingdom.currentSeasonalModifiers
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import at.posselt.pfrpg2e.kingdom.dialogs.runKingdomTurnTick
import at.posselt.pfrpg2e.kingdom.getAllSettlements
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.map.KingmakerHexGridProvider
import at.posselt.pfrpg2e.kingdom.map.routeHexSafety
import at.posselt.pfrpg2e.kingdom.resources.calculateStorage
import at.posselt.pfrpg2e.kingdom.shipmentRaidDc
import at.posselt.pfrpg2e.kingdom.getRealmData
import com.foundryvtt.core.Game

/**
 * jsMain adapter for the forecast engine (`docs/plans/2026-07-09-plan-session-forecast.md`, phase 2).
 *
 * Every label key is a compile-time constant listed here — the engine never assembles keys, and a
 * runtime-assembled key is invisible to the i18n guard and ships as a raw key on screen.
 */
const val FORECAST_KEY_COMPANION_ARRIVES = "kingdom.forecast.companionArrives"
const val FORECAST_KEY_EXPEDITION_RESOLVES = "kingdom.forecast.expeditionResolves"
const val FORECAST_KEY_CARAVAN_RISK = "kingdom.forecast.caravanRisk"
const val FORECAST_KEY_SHIPMENT_RISK = "kingdom.forecast.shipmentRisk"
const val FORECAST_KEY_CLOCK_EVENT = "kingdom.forecast.clockEvent"
const val FORECAST_KEY_THREAT_ARRIVES = "kingdom.forecast.threatArrives"
const val FORECAST_KEY_QUEST_DEADLINE = "kingdom.forecast.questDeadline"

/**
 * Daily countdowns from the two feeds that genuinely tick per world day. A companion with no eta is
 * not travelling; an expedition only counts down while inProgress — awaitingResolution is the GM's
 * queue, not a schedule.
 */
fun forecastCountdowns(
    companions: Array<RawCharacter>?,
    expeditions: Array<RawCompanionExpedition>?,
): List<DailyCountdown> {
    val travel = companions.orEmpty().mapNotNull { companion ->
        val eta = companion.eta ?: return@mapNotNull null
        DailyCountdown(
            labelKey = FORECAST_KEY_COMPANION_ARRIVES,
            labelArgs = mapOf("name" to companion.name),
            remainingDays = eta,
            kind = ForecastKind.ARRIVAL,
            target = "roster",
        )
    }
    val resolving = expeditions.orEmpty()
        .filter { it.status == "inProgress" }
        .map {
            DailyCountdown(
                labelKey = FORECAST_KEY_EXPEDITION_RESOLVES,
                labelArgs = mapOf("title" to it.title),
                remainingDays = it.daysRemaining,
                kind = ForecastKind.COMPLETION,
                target = "expeditions",
            )
        }
    return travel + resolving
}

/**
 * The End Turn block from one DISCARDED tick result.
 *
 * Clock beats keep only EXPIRED and TRIGGERED — ADVANCED fires for every ticking clock every turn
 * and would drown the panel in noise. [EndTurnBeats.resourceAlerts] is deliberately empty in this
 * phase: TickResult.changes is a full before/after diff, but choosing WHICH rows read as an alert
 * is a presentation decision that belongs with the panel, made against real data.
 */
fun endTurnBeats(tick: TickResult): EndTurnBeats = EndTurnBeats(
    clockExpirations = tick.clockEvents
        .filter { it.type == ClockEventType.EXPIRED.name || it.type == ClockEventType.TRIGGERED.name }
        .map { LabeledBeat(FORECAST_KEY_CLOCK_EVENT, mapOf("label" to it.label), target = "campaign") },
    newThreats = tick.newlyTriggeredThreats
        .map { LabeledBeat(FORECAST_KEY_THREAT_ARRIVES, mapOf("name" to it.name), target = "armyPressure") },
    questDeadlines = tick.questDeadlineReached
        .map { LabeledBeat(FORECAST_KEY_QUEST_DEADLINE, mapOf("name" to it), target = "quests") },
    resourceAlerts = emptyList(),
    ruinThresholdCrossed = tick.ruinThresholdCrossed,
)

/**
 * Assembles the full forecast, or null for a non-GM.
 *
 * The GM gate is the FIRST statement: forecasts leak hidden state by construction (unarrived
 * threats, secret clock progress), so nothing may be computed before the gate — a template
 * conditional is layout, not authorization. The tick runs on the deepClone `getKingdom()` already
 * returns and its result is read and thrown away, so previewing the future never writes it.
 */
suspend fun buildForecast(game: Game, actor: KingdomActor, horizonDays: Int): ForecastResult? {
    if (!game.user.isGM) return null
    val kingdom = actor.getKingdom() ?: return null
    val seasonalDelta = game.currentSeasonalModifiers().caravanRaidDcDelta

    val risks = runCatching {
        val caravanRisks = kingdom.caravans.orEmpty()
            .filter { it.status == "inTransit" }
            .mapNotNull { caravan ->
                val path = computeCaravanRoute(
                    KingmakerHexGridProvider(), caravan.originHexKey, caravan.destHexKey,
                )?.path.orEmpty()
                if (path.isEmpty()) return@mapNotNull null
                val safety = caravanRouteSafety(path.map(::routeHexSafety))
                val partner = kingdom.groups.find { it.name == caravan.partnerName }
                RiskForecast(
                    labelKey = FORECAST_KEY_CARAVAN_RISK,
                    labelArgs = mapOf("partner" to (caravan.partnerName ?: caravan.destLabel)),
                    dc = caravanRaidDc(
                        baseDc = CARAVAN_BASE_RAID_DC,
                        partnerStanding = partner?.standing,
                        atWar = partner?.atWar == true,
                        claimedFraction = safety.claimedFraction,
                        fullyRoadedThroughClaimed = safety.fullyRoadedThroughClaimed,
                        seasonalDcDelta = seasonalDelta,
                    ),
                    target = "turn",
                )
            }
        val shipmentRisks = kingdom.shipments.orEmpty()
            .filter { it.status == "inTransit" }
            .mapNotNull { shipment ->
                val path = shipment.path.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                RiskForecast(
                    labelKey = FORECAST_KEY_SHIPMENT_RISK,
                    labelArgs = mapOf("name" to shipment.itemName),
                    dc = shipmentRaidDc(
                        caravanRouteSafety(path.map(::routeHexSafety)),
                        seasonalDcDelta = seasonalDelta,
                    ),
                    target = "turn",
                )
            }
        caravanRisks + shipmentRisks
    }.getOrDefault(emptyList())

    val endTurn = runCatching {
        val realm = game.getRealmData(actor, kingdom)
        val settlements = kingdom.getAllSettlements(game)
        val storage = calculateStorage(realm, settlements.allSettlements)
        endTurnBeats(runKingdomTurnTick(kingdom, storage, (kingdom.currentTurn ?: 0) + 1))
    }.getOrDefault(EndTurnBeats())

    return forecast(
        ForecastInputs(
            horizonDays = horizonDays,
            countdowns = forecastCountdowns(kingdom.companions, kingdom.companionExpeditions),
            risks = risks,
            endTurn = endTurn,
        ),
    )
}
