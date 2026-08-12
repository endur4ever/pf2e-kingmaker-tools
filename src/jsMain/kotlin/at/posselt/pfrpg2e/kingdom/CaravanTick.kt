package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawCaravan
import at.posselt.pfrpg2e.kingdom.data.RawCaravanShipment
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure end-of-turn processing for the caravan economy. Each kingdom turn, every in-transit caravan:
 *  1. makes a raid flat check (DC computed by the caller from partner standing / at-war state /
 *     route safety) — on a failure it loses a fraction of its cargo;
 *  2. advances one step toward its destination;
 *  3. on arrival delivers — a `sellToPartner` caravan grants bonus Resource Dice (RAW Trade
 *     Commodities: ~1 RD per Commodity, scaled by the partner's standing/alliance), while a
 *     `buyFromPartner` caravan delivers Commodities to the kingdom pool.
 *
 * (The `settlementTransfer` kind was retired — inter-settlement transfers were rejected in the
 * 2026-07-09 gap analysis because they fight the single global commodity pool; Migration44 maps any
 * stray persisted value to `sellToPartner`.)
 *
 * The raid roll, DC, and per-commodity RD rate are injected per caravan so this is fully
 * deterministic and unit-testable; the Foundry-coupled caller (performEndTurn) rolls the dice,
 * reads partner state, applies the returned deltas, and posts chat.
 */

const val CARAVAN_BASE_RAID_DC = 11
const val CARAVAN_RD_PER_COMMODITY = 1.0 // RAW Trade Commodities success: 1 bonus Resource Die / Commodity
const val CARAVAN_RAID_LOSS_FRACTION = 0.25

// RAW Purchase Commodities: 4 RP buys 2 of Food/Lumber/Ore/Stone (2 RP each); 8 RP buys 2 Luxuries (4 each).
const val CARAVAN_RP_PER_COMMODITY = 2
const val CARAVAN_RP_PER_LUXURY = 4

enum class CaravanEventKind { DELIVERED, RAIDED, LOST }

data class CaravanEvent(
    val kind: CaravanEventKind,
    val summary: String,
    val bonusResourceDice: Int = 0,
    val deliveredCommodity: String? = null,
    val deliveredAmount: Int = 0,
    val cargoLost: Int = 0,
    val turnsRemaining: Int = 0,
)

data class CaravanTickInput(
    val caravan: RawCaravan,
    val raidDc: Int,
    val raidRoll: Int,
    val rdPerCommodity: Double,
)

data class CaravanTickResult(
    val remaining: List<RawCaravan>,
    val bonusResourceDice: Int,
    val deliveredCommodities: Map<String, Int>,
    val events: List<CaravanEvent>,
    /** True when the per-turn arbitrage cap trimmed [bonusResourceDice] below what sales earned. */
    val bonusResourceDiceCapped: Boolean = false,
    /** What sales earned before the cap (== [bonusResourceDice] when the cap didn't bind). */
    val uncappedBonusResourceDice: Int = bonusResourceDice,
)

data class ShipmentTickInput(
    val shipment: RawCaravanShipment,
    val raidDc: Int,
    val raidRoll: Int,
)

data class ShipmentTickResult(
    val remaining: List<RawCaravanShipment>,
    val delivered: List<RawCaravanShipment>,
    val events: List<CaravanEvent>,
)

/** Cargo lost when a caravan is raided: a quarter of what it carries, at least 1, never more than all. */
fun caravanRaidLoss(amount: Int): Int =
    max(1, (amount * CARAVAN_RAID_LOSS_FRACTION).roundToInt()).coerceAtMost(amount)

/** What one hex on a caravan route contributes to its safety. */
data class RouteHexSafety(
    /** The kingdom holds it: claimed or cleared. */
    val safe: Boolean,
    /** A road runs through it. */
    val roaded: Boolean,
)

/** The two route-derived inputs to [caravanRaidDc], so both are computed the same way. */
data class CaravanRouteSafety(
    val claimedFraction: Double,
    val fullyRoadedThroughClaimed: Boolean,
)

/**
 * Route safety from its hexes. Shared by the End-Turn tick and the map overlay, which previously
 * each carried their own copy of the claimed-fraction loop and could drift apart.
 *
 * Both measures range over the same hexes, so they stay coherent: a route that earns
 * [fullyRoadedThroughClaimed] necessarily has a [claimedFraction] of 1.0.
 */
fun caravanRouteSafety(hexes: List<RouteHexSafety>): CaravanRouteSafety =
    if (hexes.isEmpty()) {
        CaravanRouteSafety(claimedFraction = 0.0, fullyRoadedThroughClaimed = false)
    } else {
        CaravanRouteSafety(
            claimedFraction = hexes.count { it.safe }.toDouble() / hexes.size,
            fullyRoadedThroughClaimed = hexes.all { it.safe && it.roaded },
        )
    }

/**
 * Raid DC: easier (safer) the friendlier the partner and the more of the route runs through
 * claimed hexes. A raid happens when the d20 rolls UNDER this DC, so subtracting is the safe
 * direction.
 *
 * The two route modifiers are deliberately adjacent and additive:
 *  - up to -4 scaled by [claimedFraction], the share of the route inside the kingdom's own hexes;
 *  - a further -1 when the route is [fullyRoadedThroughClaimed], i.e. every hex is BOTH held and
 *    roaded. That only ever applies on top of the full -4, since such a route is claimed
 *    throughout, so a completely built-out trade road is 5 easier than open wilderness — which is
 *    what makes paying to connect settlements by road pay off.
 */
fun caravanRaidDc(
    baseDc: Int,
    partnerStanding: Int?,
    atWar: Boolean,
    claimedFraction: Double,
    fullyRoadedThroughClaimed: Boolean = false,
): Int {
    var dc = baseDc
    if (atWar) dc += 4
    dc -= (partnerStanding ?: 0) / 2
    dc -= (claimedFraction.coerceIn(0.0, 1.0) * 4).roundToInt()
    if (fullyRoadedThroughClaimed) dc -= 1
    return dc.coerceIn(5, 40)
}

/** Bonus Resource Dice granted per Commodity sold, scaled by the partner's standing and treaty tier. */
fun caravanRdPerCommodity(
    standing: Int?,
    allianceLevel: String?,
    base: Double = CARAVAN_RD_PER_COMMODITY,
): Double {
    val allianceMultiplier = when (allianceLevel) {
        "tribute" -> 2.0
        "alliance" -> 1.5
        "non-aggression" -> 1.25
        else -> 1.0
    }
    val standingMultiplier = (1.0 + (standing ?: 0) * 0.05).coerceAtLeast(0.25)
    return base * allianceMultiplier * standingMultiplier
}

/**
 * RP-price discount/markup for buying from a partner: friendlier partners and treaties sell cheaper,
 * hostile ones charge more. Clamped to [0.5, 1.5] of the base price.
 */
fun caravanPriceMultiplier(standing: Int?, allianceLevel: String?): Double {
    val allianceDiscount = when (allianceLevel) {
        "tribute" -> 0.25
        "alliance" -> 0.15
        "non-aggression" -> 0.05
        else -> 0.0
    }
    val standingDiscount = (standing ?: 0) * 0.03
    return (1.0 - allianceDiscount - standingDiscount).coerceIn(0.5, 1.5)
}

/** RP cost to buy [amount] of [commodity] from a partner, after the standing/alliance price multiplier. */
fun caravanPurchaseCost(commodity: String, amount: Int, standing: Int?, allianceLevel: String?): Int {
    val perCommodity = if (commodity == "luxuries") CARAVAN_RP_PER_LUXURY else CARAVAN_RP_PER_COMMODITY
    return max(0, ceil(amount * perCommodity * caravanPriceMultiplier(standing, allianceLevel)).toInt())
}

private fun RawCaravan.advanced(cargoAmount: Int, turnsRemaining: Int): RawCaravan =
    RawCaravan(
        id = id,
        kind = kind,
        originHexKey = originHexKey,
        destHexKey = destHexKey,
        originLabel = originLabel,
        destLabel = destLabel,
        partnerName = partnerName,
        cargoCommodity = cargoCommodity,
        cargoAmount = cargoAmount,
        cargoRp = cargoRp,
        etaTurns = etaTurns,
        turnsRemaining = turnsRemaining,
        status = status,
    )

fun tickCaravans(inputs: List<CaravanTickInput>, bonusRdCap: Int? = null): CaravanTickResult {
    val remaining = mutableListOf<RawCaravan>()
    val events = mutableListOf<CaravanEvent>()
    val deliveredCommodities = mutableMapOf<String, Int>()
    var bonusRd = 0

    for (input in inputs) {
        val caravan = input.caravan
        if (caravan.status != "inTransit") {
            remaining.add(caravan)
            continue
        }
        val cargoLabel = caravan.cargoCommodity ?: "rp"
        val summary = "${caravan.cargoAmount} $cargoLabel → ${caravan.destLabel}"

        var amount = caravan.cargoAmount
        var lost = 0
        if (input.raidRoll < input.raidDc) {
            lost = caravanRaidLoss(amount)
            amount -= lost
        }

        if (amount <= 0) {
            events.add(CaravanEvent(CaravanEventKind.LOST, summary, cargoLost = caravan.cargoAmount))
            continue
        }

        val turnsRemaining = caravan.turnsRemaining - 1
        if (turnsRemaining > 0) {
            remaining.add(caravan.advanced(cargoAmount = amount, turnsRemaining = turnsRemaining))
            if (lost > 0) {
                events.add(CaravanEvent(CaravanEventKind.RAIDED, summary, cargoLost = lost, turnsRemaining = turnsRemaining))
            }
            continue
        }

        // Arrived this turn.
        if (caravan.kind == "sellToPartner") {
            val rd = (amount * input.rdPerCommodity).roundToInt()
            bonusRd += rd
            events.add(CaravanEvent(CaravanEventKind.DELIVERED, summary, bonusResourceDice = rd, cargoLost = lost))
        } else {
            val commodity = caravan.cargoCommodity
            if (commodity != null) {
                deliveredCommodities[commodity] = (deliveredCommodities[commodity] ?: 0) + amount
            }
            events.add(
                CaravanEvent(
                    CaravanEventKind.DELIVERED,
                    summary,
                    deliveredCommodity = commodity,
                    deliveredAmount = amount,
                    cargoLost = lost,
                )
            )
        }
    }

    // HOLE B: cap the per-turn bonus RD from sales so buy-low/sell-high can't be a self-reinforcing
    // RP loop. Applied to the turn total (not per caravan) so many small sales can't slip under it;
    // the caller surfaces the cap in the delivery chat when it binds.
    val cappedBonusRd = if (bonusRdCap != null) capCaravanBonusRd(bonusRd, bonusRdCap) else bonusRd
    return CaravanTickResult(
        remaining = remaining,
        bonusResourceDice = cappedBonusRd,
        deliveredCommodities = deliveredCommodities,
        events = events,
        bonusResourceDiceCapped = cappedBonusRd < bonusRd,
        uncappedBonusResourceDice = bonusRd,
    )
}

private fun RawCaravanShipment.advanced(itemQuantity: Int, turnsRemaining: Int, currentHexKey: String): RawCaravanShipment =
    RawCaravanShipment(
        id = id,
        itemName = itemName,
        itemQuantity = itemQuantity,
        itemLevel = itemLevel,
        itemBulk = itemBulk,
        itemPriceGp = itemPriceGp,
        originHexKey = originHexKey,
        destHexKey = destHexKey,
        originLabel = originLabel,
        destLabel = destLabel,
        caravanType = caravanType,
        goldCost = goldCost,
        etaTurns = etaTurns,
        turnsRemaining = turnsRemaining,
        path = path,
        currentHexKey = currentHexKey,
        status = status,
    )

private fun RawCaravanShipment.delivered(itemQuantity: Int): RawCaravanShipment =
    RawCaravanShipment(
        id = id,
        itemName = itemName,
        itemQuantity = itemQuantity,
        itemLevel = itemLevel,
        itemBulk = itemBulk,
        itemPriceGp = itemPriceGp,
        originHexKey = originHexKey,
        destHexKey = destHexKey,
        originLabel = originLabel,
        destLabel = destLabel,
        caravanType = caravanType,
        goldCost = goldCost,
        etaTurns = etaTurns,
        turnsRemaining = 0,
        path = path,
        currentHexKey = destHexKey,
        status = "delivered",
    )

fun tickShipments(inputs: List<ShipmentTickInput>): ShipmentTickResult {
    val remaining = mutableListOf<RawCaravanShipment>()
    val delivered = mutableListOf<RawCaravanShipment>()
    val events = mutableListOf<CaravanEvent>()

    for (input in inputs) {
        val shipment = input.shipment
        if (shipment.status != "inTransit") {
            remaining.add(shipment)
            continue
        }
        val summary = "${shipment.itemQuantity}x ${shipment.itemName} → ${shipment.destLabel}"

        var quantity = shipment.itemQuantity
        var lost = 0
        if (input.raidRoll < input.raidDc) {
            lost = max(1, (quantity * 0.25).roundToInt()).coerceAtMost(quantity)
            quantity -= lost
        }

        if (quantity <= 0) {
            events.add(CaravanEvent(CaravanEventKind.LOST, summary, cargoLost = shipment.itemQuantity))
            continue
        }

        val turnsRemaining = shipment.turnsRemaining - 1
        val path = shipment.path
        val elapsedTurns = shipment.etaTurns - turnsRemaining
        val progressIdx = if (shipment.etaTurns <= 0) path.lastIndex else ((path.size - 1) * elapsedTurns.toDouble() / shipment.etaTurns).roundToInt().coerceIn(0, path.lastIndex)
        val nextHexKey = if (path.isNotEmpty() && progressIdx >= 0 && progressIdx < path.size) path[progressIdx] else shipment.destHexKey

        if (lost > 0) {
            events.add(CaravanEvent(CaravanEventKind.RAIDED, summary, cargoLost = lost, turnsRemaining = turnsRemaining))
        }

        if (turnsRemaining > 0) {
            remaining.add(shipment.advanced(itemQuantity = quantity, turnsRemaining = turnsRemaining, currentHexKey = nextHexKey))
        } else {
            val arrivedShipment = shipment.delivered(itemQuantity = quantity)
            delivered.add(arrivedShipment)
            events.add(CaravanEvent(CaravanEventKind.DELIVERED, summary, deliveredAmount = quantity, cargoLost = lost))
        }
    }

    return ShipmentTickResult(
        remaining = remaining,
        delivered = delivered,
        events = events,
    )
}
