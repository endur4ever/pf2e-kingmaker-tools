package at.posselt.pfrpg2e.companion

import kotlin.math.abs

/**
 * Pure travel math for expedition destinations (design: distance-derived duration).
 *
 * Hex keys on the realm map encode offset coordinates as `i * 1000 + j`; the native
 * Kingmaker region model exposes each hex's cube coordinates (q + r + s = 0), which is
 * what distance is computed from. The impure hexKey -> cube resolution lives in
 * jsMain (ExpeditionDestinations.kt); everything here is deterministic and unit-tested.
 */

/** Hex distance between two cube coordinates: (|dq| + |dr| + |ds|) / 2. */
fun hexCubeDistance(q1: Int, r1: Int, s1: Int, q2: Int, r2: Int, s2: Int): Int =
    (abs(q1 - q2) + abs(r1 - r2) + abs(s1 - s2)) / 2

/**
 * Hexes a companion party covers per travel day. One Kingmaker hex is 12 miles;
 * a light party on horse/foot manages about two hexes of mixed terrain a day.
 */
const val EXPEDITION_HEXES_PER_DAY: Int = 2

/** One-way travel days for [hexDistance] hexes (ceiling division; 0 for no distance). */
fun expeditionTravelDays(hexDistance: Int, hexesPerDay: Int = EXPEDITION_HEXES_PER_DAY): Int =
    if (hexDistance <= 0 || hexesPerDay <= 0) 0
    else (hexDistance + hexesPerDay - 1) / hexesPerDay

/**
 * Total expedition duration: the tier's on-site base days plus ROUND-TRIP travel
 * (the calendar reminder promises "expected back", so the return leg counts).
 */
fun expeditionTotalDays(tierBaseDays: Int, travelDaysOneWay: Int): Int =
    tierBaseDays + 2 * travelDaysOneWay.coerceAtLeast(0)

/** Display label "i.j" for an `i*1000+j` hex key; null when the key is not numeric. */
fun formatHexKeyLabel(hexKey: String): String? =
    hexKey.toIntOrNull()?.let { "${it / 1000}.${it % 1000}" }
