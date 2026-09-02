package com.foundryvtt.kingmaker

import com.foundryvtt.core.grid.GridHex
import com.foundryvtt.core.grid.GridOffset2D
import com.foundryvtt.core.grid.HexagonalGrid
import com.foundryvtt.core.grid.HexagonalGridCube2D
import com.foundryvtt.core.utils.Color
import com.pixijs.Point
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface HexOffsetCoordinate {
    val i: Int
    val j: Int
}

@JsPlainObject
external interface Terrain {
    val id: String
    val img: String
    val label: String
}

/** One entry of kingmaker.CONST.TRAVEL (verified against pf2e-km-compiled.mjs, 2026-09-02). */
@JsPlainObject
external interface TravelKind {
    val id: String
    val label: String
    val multiplier: Double
}

/** One entry of kingmaker.CONST.DISCOVERY_TRAITS. */
@JsPlainObject
external interface DiscoveryTrait {
    val id: String
    val label: String
}

/** One entry of kingmaker.CONST.EXPLORATION_STATES: value 0 NONE, 1 RECON, 2 MAP. */
@JsPlainObject
external interface ExplorationState {
    val value: Int
    val label: String
}

@JsPlainObject
external interface Zone {
    val id: String
    val color: String
    val label: String
    val level: Int
    val terrain: String
    val travel: String
    val polygon: Array<Int>
}


external class KingmakerHex(
    coordinates: Point,
    grid: HexagonalGrid,
): GridHex {
    constructor(
        coordinates: HexagonalGridCube2D,
        grid: HexagonalGrid,
    )

    constructor(
        coordinates: GridOffset2D,
        grid: HexagonalGrid,
    )

    val key: Int
    val name: String
    val zone: Zone
    val terrain: Terrain
    /** kingmaker.CONST.TRAVEL[data.travel]: open | difficult | greater-difficult. */
    val travel: TravelKind
    /** Also a TRAVEL entry: the module reuses the travel table for difficulty. */
    val difficulty: TravelKind
    /** kingmaker.CONST.DISCOVERY_TRAITS[data.discoveryTrait]: landmark | standard | secret. */
    val discoveryTrait: DiscoveryTrait
    /** The EXPLORATION_STATES entry whose value matches data.exploration (0 none, 1 recon, 2 map). */
    val explorationState: ExplorationState?
    val color: Color

    companion object {
        @JsStatic
        fun getKey(offset: HexOffsetCoordinate): Int
    }
}